package nl.robin.rolbeheer.spelers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * De spelerscommands: homes, spawn, warps, kits, /back, /tpa en /msg.
 * Alle permissies beginnen met rolbeheer., dus je regelt ze in het rollenpaneel.
 */
public final class PlayerCommands implements TabExecutor {

    /** Namen van de commands die deze klasse afhandelt. */
    public static final List<String> COMMANDS = List.of(
            "home", "sethome", "delhome", "homes", "spawn", "setspawn", "back",
            "warp", "warps", "setwarp", "delwarp", "kit", "kits", "setkit", "delkit",
            "tpa", "tpahere", "tpaccept", "tpdeny", "msg", "r");

    private record Request(UUID from, boolean here, long expires) {}

    private final RolBeheer plugin;
    private final Store store;
    private final Map<UUID, Location> backLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastMessage = new ConcurrentHashMap<>();

    public PlayerCommands(RolBeheer plugin, Store store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Zorgt dat warps en kits als permissie in het paneel verschijnen. */
    public void registerPermissions() {
        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        for (String warp : store.warpNames()) {
            addPermission(pm, "rolbeheer.warp." + warp, "Toegang tot warp " + warp);
        }
        for (String kit : store.kitNames()) {
            addPermission(pm, "rolbeheer.kit." + kit, "Toegang tot kit " + kit);
        }
    }

    private static void addPermission(org.bukkit.plugin.PluginManager pm, String node, String description) {
        if (pm.getPermission(node) == null) {
            pm.addPermission(new org.bukkit.permissions.Permission(node, description,
                    org.bukkit.permissions.PermissionDefault.OP));
        }
    }

    public void rememberBack(Player player, Location location) {
        backLocations.put(player.getUniqueId(), location);
    }

    /** Onthoudt met wie iemand als laatst praatte, ook bij het vanilla /msg. */
    public void rememberConversation(UUID a, UUID b) {
        lastMessage.put(a, b);
        lastMessage.put(b, a);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            Text.send(sender, "<red>Dit command werkt alleen in de game.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "home" -> home(player, args);
            case "sethome" -> setHome(player, args);
            case "delhome" -> delHome(player, args);
            case "homes" -> listHomes(player);
            case "spawn" -> spawn(player);
            case "setspawn" -> setSpawn(player);
            case "back" -> back(player);
            case "warp" -> warp(player, args);
            case "warps" -> listWarps(player);
            case "setwarp" -> setWarp(player, args);
            case "delwarp" -> delWarp(player, args);
            case "kit" -> kit(player, args);
            case "kits" -> listKits(player);
            case "setkit" -> setKit(player, args);
            case "delkit" -> delKit(player, args);
            case "tpa", "tpahere" -> requestTeleport(player, args, command.getName().equalsIgnoreCase("tpahere"));
            case "tpaccept" -> acceptTeleport(player);
            case "tpdeny" -> denyTeleport(player);
            case "msg" -> message(player, args);
            case "r" -> reply(player, args);
            default -> { }
        }
        return true;
    }

    // ---------------------------------------------------------------- homes

    /** Hoeveel homes deze speler mag hebben: hoogste rolbeheer.homes.<getal> uit zijn rollen. */
    public int homeLimit(Player player) {
        int limit = plugin.getConfig().getInt("commands.standaard-homes", 1);
        for (Role role : plugin.roles().getEffectiveRoles(player.getUniqueId())) {
            for (String perm : role.getPermissions()) {
                if (perm.equals("*")) limit = Math.max(limit, 100);
                if (!perm.startsWith("rolbeheer.homes.")) continue;
                try {
                    limit = Math.max(limit, Integer.parseInt(perm.substring("rolbeheer.homes.".length())));
                } catch (NumberFormatException ignored) {
                    // geen getal achter de permissie
                }
            }
        }
        return limit;
    }

    private void home(Player player, String[] args) {
        Map<String, Location> homes = store.homes(player.getUniqueId());
        if (homes.isEmpty()) {
            Text.send(player, "Je hebt nog geen home. Zet er een met <white>/sethome");
            return;
        }
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : homes.keySet().iterator().next();
        Location location = homes.get(name);
        if (location == null) {
            Text.send(player, "<red>Je hebt geen home die <white><n></white> heet.", Placeholder.unparsed("n", name));
            listHomes(player);
            return;
        }
        teleport(player, location, "Welkom thuis.");
    }

    private void setHome(Player player, String[] args) {
        Map<String, Location> homes = store.homes(player.getUniqueId());
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        if (!Store.isValidName(name)) {
            Text.send(player, "<red>Gebruik alleen letters, cijfers, _ en - in de naam.");
            return;
        }
        int limit = homeLimit(player);
        if (!homes.containsKey(name) && homes.size() >= limit) {
            Text.send(player, "<red>Je mag maximaal <white><n></white> homes hebben.",
                    Placeholder.unparsed("n", String.valueOf(limit)));
            return;
        }
        store.setHome(player.getUniqueId(), name, player.getLocation());
        Text.send(player, "Home <white><n></white> opgeslagen.", Placeholder.unparsed("n", name));
    }

    private void delHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        if (store.deleteHome(player.getUniqueId(), name)) {
            Text.send(player, "Home <white><n></white> verwijderd.", Placeholder.unparsed("n", name));
        } else {
            Text.send(player, "<red>Je hebt geen home die <white><n></white> heet.", Placeholder.unparsed("n", name));
        }
    }

    private void listHomes(Player player) {
        Map<String, Location> homes = store.homes(player.getUniqueId());
        if (homes.isEmpty()) {
            Text.send(player, "Je hebt nog geen home. Zet er een met <white>/sethome");
            return;
        }
        Text.send(player, "Jouw homes (<white><n></white> van <white><max></white>): <white><lijst>",
                Placeholder.unparsed("n", String.valueOf(homes.size())),
                Placeholder.unparsed("max", String.valueOf(homeLimit(player))),
                Placeholder.unparsed("lijst", String.join(", ", homes.keySet())));
    }

    // ---------------------------------------------------------------- spawn en back

    private void spawn(Player player) {
        Location spawn = store.spawn();
        if (spawn == null) spawn = player.getWorld().getSpawnLocation();
        teleport(player, spawn, "Je bent bij de spawn.");
    }

    private void setSpawn(Player player) {
        store.setSpawn(player.getLocation());
        Text.send(player, "Het spawnpunt staat nu hier.");
    }

    private void back(Player player) {
        Location location = backLocations.get(player.getUniqueId());
        if (location == null) {
            Text.send(player, "<red>Er is geen plek om naar terug te gaan.");
            return;
        }
        teleport(player, location, "Terug naar je vorige plek.");
    }

    // ---------------------------------------------------------------- warps

    private void warp(Player player, String[] args) {
        if (args.length == 0) {
            listWarps(player);
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        Location location = store.warp(name);
        if (location == null) {
            Text.send(player, "<red>Er is geen warp die <white><n></white> heet.", Placeholder.unparsed("n", name));
            return;
        }
        if (!player.hasPermission("rolbeheer.warp." + name) && !player.hasPermission("rolbeheer.warp.*")) {
            Text.send(player, "<red>Je mag deze warp niet gebruiken.");
            return;
        }
        teleport(player, location, "Je bent bij warp " + name + ".");
    }

    private void listWarps(Player player) {
        List<String> names = new ArrayList<>();
        for (String name : store.warpNames()) {
            if (player.hasPermission("rolbeheer.warp." + name) || player.hasPermission("rolbeheer.warp.*")) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            Text.send(player, "Er zijn nog geen warps die jij mag gebruiken.");
            return;
        }
        Text.send(player, "Warps: <white><lijst>", Placeholder.unparsed("lijst", String.join(", ", names)));
    }

    private void setWarp(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "<red>Gebruik: <white>/setwarp <naam>");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!Store.isValidName(name)) {
            Text.send(player, "<red>Gebruik alleen letters, cijfers, _ en - in de naam.");
            return;
        }
        store.setWarp(name, player.getLocation());
        registerPermissions();
        plugin.scanner().rescan();
        Text.send(player, "Warp <white><n></white> opgeslagen.", Placeholder.unparsed("n", name));
    }

    private void delWarp(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "<red>Gebruik: <white>/delwarp <naam>");
            return;
        }
        if (store.deleteWarp(args[0])) {
            Text.send(player, "Warp verwijderd.");
        } else {
            Text.send(player, "<red>Die warp bestaat niet.");
        }
    }

    // ---------------------------------------------------------------- kits

    private void kit(Player player, String[] args) {
        if (args.length == 0) {
            listKits(player);
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!store.hasKit(name)) {
            Text.send(player, "<red>Er is geen kit die <white><n></white> heet.", Placeholder.unparsed("n", name));
            return;
        }
        if (!player.hasPermission("rolbeheer.kit." + name) && !player.hasPermission("rolbeheer.kit.*")) {
            Text.send(player, "<red>Je mag deze kit niet pakken.");
            return;
        }
        int cooldown = store.kitCooldown(name);
        long last = store.lastKitUse(player.getUniqueId(), name);
        long waitSeconds = cooldown - (System.currentTimeMillis() - last) / 1000;
        if (last > 0 && cooldown > 0 && waitSeconds > 0) {
            Text.send(player, "<red>Deze kit kun je pas over <white><tijd></white> weer pakken.",
                    Placeholder.unparsed("tijd", duration(waitSeconds)));
            return;
        }
        List<ItemStack> items = store.kitItems(name);
        if (items.isEmpty()) {
            Text.send(player, "<red>Deze kit is leeg.");
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        store.setLastKitUse(player.getUniqueId(), name);
        Text.send(player, "Je hebt kit <white><n></white> gekregen.", Placeholder.unparsed("n", name));
        if (!leftover.isEmpty()) Text.send(player, "<gray>Je inventaris zat vol; de rest ligt op de grond.");
    }

    private void listKits(Player player) {
        List<String> names = new ArrayList<>();
        for (String name : store.kitNames()) {
            if (player.hasPermission("rolbeheer.kit." + name) || player.hasPermission("rolbeheer.kit.*")) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            Text.send(player, "Er zijn nog geen kits die jij mag pakken.");
            return;
        }
        Text.send(player, "Kits: <white><lijst>", Placeholder.unparsed("lijst", String.join(", ", names)));
    }

    private void setKit(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "<red>Gebruik: <white>/setkit <naam> [wachttijd in seconden]");
            Text.send(player, "<gray>De kit wordt gevuld met alles wat je nu in je inventaris hebt.");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!Store.isValidName(name)) {
            Text.send(player, "<red>Gebruik alleen letters, cijfers, _ en - in de naam.");
            return;
        }
        int cooldown = 0;
        if (args.length > 1) {
            try {
                cooldown = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Text.send(player, "<red>De wachttijd moet een getal in seconden zijn.");
                return;
            }
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        if (items.isEmpty()) {
            Text.send(player, "<red>Je inventaris is leeg.");
            return;
        }
        store.setKit(name, items, cooldown);
        registerPermissions();
        plugin.scanner().rescan();
        Text.send(player, "Kit <white><n></white> opgeslagen met <white><i></white> items.",
                Placeholder.unparsed("n", name), Placeholder.unparsed("i", String.valueOf(items.size())));
        Text.send(player, "<gray>Geef een rol de permissie <white>rolbeheer.kit." + name + "</white> om hem te mogen pakken.");
    }

    private void delKit(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "<red>Gebruik: <white>/delkit <naam>");
            return;
        }
        if (store.deleteKit(args[0])) {
            Text.send(player, "Kit verwijderd.");
        } else {
            Text.send(player, "<red>Die kit bestaat niet.");
        }
    }

    // ---------------------------------------------------------------- teleportverzoeken

    private void requestTeleport(Player player, String[] args, boolean here) {
        if (args.length == 0) {
            Text.send(player, "<red>Gebruik: <white>/" + (here ? "tpahere" : "tpa") + " <speler>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            Text.send(player, "<red>Die speler is niet online.");
            return;
        }
        long timeout = plugin.getConfig().getLong("commands.tpa-seconden", 60);
        requests.put(target.getUniqueId(), new Request(player.getUniqueId(), here,
                System.currentTimeMillis() + timeout * 1000));

        Text.send(player, "Verzoek verstuurd naar <white><n></white>.", Placeholder.unparsed("n", target.getName()));
        Text.send(target, here
                        ? "<white><n></white> wil dat jij naar hem toe komt."
                        : "<white><n></white> wil naar jou toe teleporteren.",
                Placeholder.unparsed("n", player.getName()));
        Text.send(target, "<white>/tpaccept</white> om te accepteren, <white>/tpdeny</white> om te weigeren.");
    }

    private void acceptTeleport(Player player) {
        Request request = requests.remove(player.getUniqueId());
        if (request == null || request.expires() < System.currentTimeMillis()) {
            Text.send(player, "<red>Je hebt geen openstaand verzoek.");
            return;
        }
        Player other = Bukkit.getPlayer(request.from());
        if (other == null) {
            Text.send(player, "<red>Die speler is niet meer online.");
            return;
        }
        if (request.here()) {
            teleport(player, other.getLocation(), "Je bent naar " + other.getName() + " gebracht.");
            Text.send(other, "<white><n></white> accepteerde je verzoek.", Placeholder.unparsed("n", player.getName()));
        } else {
            teleport(other, player.getLocation(), "Je bent naar " + player.getName() + " gebracht.");
            Text.send(player, "Verzoek geaccepteerd.");
        }
    }

    private void denyTeleport(Player player) {
        Request request = requests.remove(player.getUniqueId());
        if (request == null) {
            Text.send(player, "<red>Je hebt geen openstaand verzoek.");
            return;
        }
        Text.send(player, "Verzoek geweigerd.");
        Player other = Bukkit.getPlayer(request.from());
        if (other != null) {
            Text.send(other, "<white><n></white> weigerde je verzoek.", Placeholder.unparsed("n", player.getName()));
        }
    }

    // ---------------------------------------------------------------- privéberichten

    private void message(Player player, String[] args) {
        if (args.length < 2) {
            Text.send(player, "<red>Gebruik: <white>/msg <speler> <bericht>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(player, "<red>Die speler is niet online.");
            return;
        }
        send(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    private void reply(Player player, String[] args) {
        if (args.length == 0) {
            Text.send(player, "<red>Gebruik: <white>/r <bericht>");
            return;
        }
        UUID last = lastMessage.get(player.getUniqueId());
        Player target = last == null ? null : Bukkit.getPlayer(last);
        if (target == null) {
            Text.send(player, "<red>Er is niemand om op te antwoorden.");
            return;
        }
        send(player, target, String.join(" ", args));
    }

    private void send(Player from, Player to, String message) {
        Component text = Component.text(message, NamedTextColor.WHITE);
        from.sendMessage(Component.text("jij → " + to.getName() + ": ", NamedTextColor.GRAY).append(text));
        to.sendMessage(Component.text(from.getName() + " → jou: ", NamedTextColor.GRAY).append(text));
        rememberConversation(from.getUniqueId(), to.getUniqueId());
    }

    // ---------------------------------------------------------------- hulpjes

    private void teleport(Player player, Location destination, String message) {
        rememberBack(player, player.getLocation());
        player.teleportAsync(destination).thenAccept(done -> {
            if (Boolean.TRUE.equals(done)) Text.send(player, message);
        });
    }

    private static String duration(long seconds) {
        if (seconds < 60) return seconds + " seconden";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " minuten";
        return (minutes / 60) + " uur en " + (minutes % 60) + " minuten";
    }

    // ---------------------------------------------------------------- tab-aanvulling

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        List<String> options = new ArrayList<>();
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "home", "delhome" -> options.addAll(store.homes(player.getUniqueId()).keySet());
            case "warp", "delwarp" -> options.addAll(store.warpNames());
            case "kit", "delkit", "setkit" -> options.addAll(store.kitNames());
            case "tpa", "tpahere", "msg" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            default -> { }
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }

    /** Voor het webpaneel: alle openstaande gegevens zijn nodig bij het opruimen van spelers. */
    public Map<UUID, Location> backLocations() {
        return new HashMap<>(backLocations);
    }
}
