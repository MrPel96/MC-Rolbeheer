package nl.robin.rolbeheer;

import nl.robin.rolbeheer.command.RolCommand;
import nl.robin.rolbeheer.gui.ChatInput;
import nl.robin.rolbeheer.gui.MenuListener;
import nl.robin.rolbeheer.listener.PlayerListener;
import nl.robin.rolbeheer.listener.ServerListener;
import nl.robin.rolbeheer.role.RoleManager;
import nl.robin.rolbeheer.scan.PluginScanner;
import nl.robin.rolbeheer.sync.PlayerSync;
import nl.robin.rolbeheer.spelers.EssentialsImport;
import nl.robin.rolbeheer.spelers.ForwardExecutor;
import nl.robin.rolbeheer.spelers.PlayerCommands;
import nl.robin.rolbeheer.spelers.Boards;
import nl.robin.rolbeheer.spelers.SpawnProtection;
import nl.robin.rolbeheer.spelers.Stats;
import nl.robin.rolbeheer.spelers.StatsCommands;
import nl.robin.rolbeheer.spelers.Store;
import nl.robin.rolbeheer.web.Updater;
import nl.robin.rolbeheer.web.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RolBeheer extends JavaPlugin {

    private static final String DEFAULT_CHAT_FORMAT =
            "<prefix><naam><suffix><dark_gray> » </dark_gray><white><bericht>";

    private RoleManager roles;
    private PluginScanner scanner;
    private PlayerSync sync;
    private ChatInput chatInput;
    private WebServer web;
    private Updater updater;
    private Store store;
    private PlayerCommands playerCommands;
    private EssentialsImport essentialsImport;
    private SpawnProtection spawnProtection;
    private Stats stats;
    private Boards boards;

    // Gecachte config-waarden (worden ook vanaf de async chat-thread gelezen)
    private volatile String chatFormat = DEFAULT_CHAT_FORMAT;
    private volatile boolean chatEnabled = true;
    private volatile String noAccessMessage = "<red>Geen toegang.";

    private final long startedAt = System.currentTimeMillis();

    @Override
    public void onEnable() {
        migrateOldFolder();
        saveDefaultConfig();
        cacheConfig();

        roles = new RoleManager(this);
        roles.load();
        scanner = new PluginScanner(this);
        sync = new PlayerSync(this);
        chatInput = new ChatInput(this);
        updater = new Updater(this);
        web = new WebServer(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(chatInput, this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ServerListener(this), this);

        // De spelerscommands zijn een extraatje: gaat hier iets mis, dan blijft de rest van
        // de plugin gewoon draaien in plaats van dat alles uitvalt.
        try {
            if (getConfig().getBoolean("commands.ingeschakeld", true)) {
            store = new Store(this);
            playerCommands = new PlayerCommands(this, store);
            playerCommands.registerPermissions();
            essentialsImport = new EssentialsImport(this, store);
            spawnProtection = new SpawnProtection(this);
            pm.registerEvents(spawnProtection, this);
            java.util.List<String> off = getConfig().getStringList("commands.uitgeschakeld").stream()
                    .map(n -> n.toLowerCase(java.util.Locale.ROOT).replace("/", "")).toList();
            for (String name : PlayerCommands.COMMANDS) {
                PluginCommand pc = getCommand(name);
                if (pc == null) continue;
                if (off.contains(name)) {
                    // Niet zelf afhandelen: doorgeven aan de plugin die dit command ook levert.
                    pc.setPermission(null);
                    ForwardExecutor forward = new ForwardExecutor(this, name);
                    pc.setExecutor(forward);
                    pc.setTabCompleter(forward);
                    continue;
                }
                pc.setExecutor(playerCommands);
                pc.setTabCompleter(playerCommands);
            }
            if (!off.isEmpty()) getLogger().info("Uitgezette commands: " + String.join(", ", off));
        }
        } catch (RuntimeException | LinkageError e) {
            getLogger().warning("De spelerscommands konden niet starten: " + e
                    + ". De rollen en het webpaneel werken gewoon.");
        }

        stats = new Stats(this);
        boards = new Boards(this, stats);
        StatsCommands statsCommands = new StatsCommands(this, stats);
        for (String name : new String[]{"stats", "top"}) {
            PluginCommand sc = getCommand(name);
            if (sc != null) {
                sc.setExecutor(statsCommands);
                sc.setTabCompleter(statsCommands);
            }
        }
        boards.start();

        RolCommand command = new RolCommand(this);
        PluginCommand pc = getCommand("rol");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        // Eerste scan meteen, tweede scan zodra de server volledig is opgestart
        // (dan zijn alle andere plugins en hun commands geladen).
        scanner.rescan();
        sync.applyAll();
        Bukkit.getScheduler().runTask(this, () -> {
            scanner.rescan();
            sync.applyAll();
            getLogger().info(scanner.groups().size() + " plugin-groepen gevonden, "
                    + roles.getRoles().size() + " rollen geladen.");
        });
        web.start();
    }

    @Override
    public void onDisable() {
        if (boards != null) boards.stop();
        if (stats != null) stats.saveIfNeeded();
        if (web != null) web.stop();
        if (updater != null) updater.applyStagedOnShutdown();
        if (sync != null) sync.shutdown();
        if (roles != null) roles.save();
    }

    /** Alles opnieuw inladen: config, rollen, spelers en plugin-scan. */
    public void reloadAll() {
        reloadConfig();
        cacheConfig();
        roles.load();
        scanner.rescan();
        sync.applyAll();
        if (spawnProtection != null) spawnProtection.refresh();
        if (boards != null) boards.updateAll();
        web.stop();
        web.start();
    }

    /** Wijzigingen opslaan en direct op alle online spelers toepassen. */
    public void commit() {
        roles.save();
        sync.applyAll();
    }

    /** Neemt de instellingen over uit de oude map plugins/RolBeheer. */
    private void migrateOldFolder() {
        java.io.File newFolder = getDataFolder();
        java.io.File oldFolder = new java.io.File(newFolder.getParentFile(), "RolBeheer");
        if (!oldFolder.isDirectory() || new java.io.File(newFolder, "config.yml").isFile()) return;
        newFolder.mkdirs();
        java.io.File[] files = oldFolder.listFiles();
        if (files == null) return;
        int copied = 0;
        for (java.io.File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".yml")) continue;
            try {
                java.nio.file.Files.copy(file.toPath(), new java.io.File(newFolder, file.getName()).toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (java.io.IOException e) {
                getLogger().warning("Kon " + file.getName() + " niet overnemen: " + e.getMessage());
            }
        }
        if (copied > 0) {
            getLogger().info(copied + " bestanden overgenomen uit plugins/RolBeheer. "
                    + "Die map mag je verwijderen zodra alles goed werkt.");
        }
    }

    public long startedAt() { return startedAt; }

    public String displayName() {
        return getConfig().getString("weergavenaam", "The Blueprint");
    }

    private void cacheConfig() {
        nl.robin.rolbeheer.util.Text.setPrefix(getConfig().getString("berichten.prefix",
                nl.robin.rolbeheer.util.Text.DEFAULT_PREFIX));
        chatFormat = getConfig().getString("chat.formaat", DEFAULT_CHAT_FORMAT);
        chatEnabled = getConfig().getBoolean("chat.ingeschakeld", true);
        noAccessMessage = getConfig().getString("berichten.geen-toegang-command",
                "<red>Je hebt geen toestemming om dit command te gebruiken.");
    }

    public RoleManager roles() { return roles; }
    public PluginScanner scanner() { return scanner; }
    public PlayerSync sync() { return sync; }
    public ChatInput chatInput() { return chatInput; }
    public WebServer web() { return web; }
    public Updater updater() { return updater; }
    public Store store() { return store; }
    public PlayerCommands playerCommands() { return playerCommands; }
    public EssentialsImport essentialsImport() { return essentialsImport; }
    public SpawnProtection spawnProtection() { return spawnProtection; }
    public Stats stats() { return stats; }
    public Boards boards() { return boards; }

    /** Na het wijzigen van instellingen via het paneel. */
    public void refreshSettings() {
        cacheConfig();
        if (spawnProtection != null) spawnProtection.refresh();
        if (boards != null) boards.updateAll();
    }

    /** Het jar-bestand van deze plugin; nodig om een update te kunnen installeren. */
    public java.io.File jarFile() { return getFile(); }
    public String chatFormat() { return chatFormat; }
    public boolean chatEnabled() { return chatEnabled; }
    public String noAccessMessage() { return noAccessMessage; }
}
