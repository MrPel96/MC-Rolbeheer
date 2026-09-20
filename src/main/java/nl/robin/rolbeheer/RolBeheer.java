package nl.robin.rolbeheer;

import nl.robin.rolbeheer.command.RolCommand;
import nl.robin.rolbeheer.gui.ChatInput;
import nl.robin.rolbeheer.gui.MenuListener;
import nl.robin.rolbeheer.listener.PlayerListener;
import nl.robin.rolbeheer.listener.ServerListener;
import nl.robin.rolbeheer.role.RoleManager;
import nl.robin.rolbeheer.scan.PluginScanner;
import nl.robin.rolbeheer.sync.PlayerSync;
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

    // Gecachte config-waarden (worden ook vanaf de async chat-thread gelezen)
    private volatile String chatFormat = DEFAULT_CHAT_FORMAT;
    private volatile boolean chatEnabled = true;
    private volatile String noAccessMessage = "<red>Geen toegang.";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cacheConfig();

        roles = new RoleManager(this);
        roles.load();
        scanner = new PluginScanner(this);
        sync = new PlayerSync(this);
        chatInput = new ChatInput(this);
        web = new WebServer(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(chatInput, this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ServerListener(this), this);

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
        if (web != null) web.stop();
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
        web.stop();
        web.start();
    }

    /** Wijzigingen opslaan en direct op alle online spelers toepassen. */
    public void commit() {
        roles.save();
        sync.applyAll();
    }

    private void cacheConfig() {
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
    public String chatFormat() { return chatFormat; }
    public boolean chatEnabled() { return chatEnabled; }
    public String noAccessMessage() { return noAccessMessage; }
}
