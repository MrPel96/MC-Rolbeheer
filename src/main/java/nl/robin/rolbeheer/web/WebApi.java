package nl.robin.rolbeheer.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.PermState;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.role.RoleManager;
import nl.robin.rolbeheer.scan.PluginScanner.PermEntry;
import nl.robin.rolbeheer.server.PlayerAdmin;
import nl.robin.rolbeheer.server.ServerSettings;
import nl.robin.rolbeheer.scan.PluginScanner.PluginGroup;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Alle acties van het webpaneel. Wordt altijd op de hoofdthread van de server uitgevoerd. */
public final class WebApi {

    private static final Pattern VALID_NODE = Pattern.compile("-?[a-z0-9_.*-]+");

    private final RolBeheer plugin;
    private final ServerSettings settings;
    private final PlayerAdmin players;

    public WebApi(RolBeheer plugin) {
        this.plugin = plugin;
        this.settings = new ServerSettings(plugin);
        this.players = new PlayerAdmin(plugin);
    }

    public JsonElement handle(String method, String route, JsonObject req) {
        if (method.equals("GET")) {
            return switch (route) {
                case "state" -> state();
                case "plugins" -> plugins();
                case "server" -> server(null);
                case "spelers" -> spelers(null);
                case "berichten" -> berichten(null);
                case "overzicht" -> overzicht();
                default -> throw new ApiException("Onbekende actie.");
            };
        }
        if (!method.equals("POST")) throw new ApiException("Methode niet toegestaan.");

        switch (route) {
            case "preview" -> {
                JsonObject o = new JsonObject();
                o.addProperty("html", ComponentHtml.toHtml(Text.parse(str(req, "text"))));
                return o;
            }
            case "rescan" -> {
                plugin.scanner().rescan();
                plugin.sync().applyAll();
                return state();
            }
            case "role/create" -> createRole(req);
            case "role/delete" -> deleteRole(req);
            case "role/update" -> updateRole(req);
            case "role/perm" -> setPerm(req);
            case "role/perms" -> setPerms(req);
            case "player/role" -> playerRole(req);
            case "server/property" -> {
                String key = str(req, "key");
                ServerSettings.Setting setting = ServerSettings.setting(key);
                String value = ServerSettings.check(setting, str(req, "value"));
                settings.writeProperty(key, value);
                settings.applyLive(key, value);
                log("serverinstelling " + key + " = " + value);
                return server(null);
            }
            case "spelers/instelling" -> {
                String key = str(req, "key");
                switch (key) {
                    case "standaard-homes" -> plugin.getConfig().set("commands.standaard-homes",
                            Math.max(1, Math.min(100, number(req))));
                    case "tpa-seconden" -> plugin.getConfig().set("commands.tpa-seconden",
                            Math.max(5, Math.min(600, number(req))));
                    case "ingeschakeld" -> plugin.getConfig().set("commands.ingeschakeld",
                            req.has("value") && req.get("value").getAsBoolean());
                    case "spawn-eerste-join" -> plugin.getConfig().set("commands.spawn.bij-eerste-join",
                            req.has("value") && req.get("value").getAsBoolean());
                    case "spawn-bij-dood" -> plugin.getConfig().set("commands.spawn.bij-dood",
                            req.has("value") && req.get("value").getAsBoolean());
                    case "bescherming", "bescherming-pvp", "bescherming-mobs", "bescherming-interactie" -> {
                        String path = switch (key) {
                            case "bescherming" -> "ingeschakeld";
                            case "bescherming-pvp" -> "geen-pvp";
                            case "bescherming-mobs" -> "geen-mobschade";
                            default -> "geen-interactie";
                        };
                        plugin.getConfig().set("commands.spawn.bescherming." + path,
                                req.has("value") && req.get("value").getAsBoolean());
                    }
                    case "chat-prefix" -> {
                        String value = str(req, "value").trim();
                        if (value.length() > 100) throw new ApiException("Deze tekst is te lang.");
                        plugin.getConfig().set("berichten.prefix", value);
                    }
                    case "bescherming-straal" -> plugin.getConfig().set("commands.spawn.bescherming.straal",
                            Math.max(1, Math.min(500, number(req))));
                    default -> throw new ApiException("Onbekende instelling.");
                }
                plugin.saveConfig();
                plugin.refreshSettings();
                log("spelerscommands: " + key + " gewijzigd");
                return spelers(key.equals("ingeschakeld") ? "Opgeslagen. Herstart de server om dit toe te passen." : null);
            }
            case "berichten/opslaan" -> {
                String key = str(req, "key");
                String path = switch (key) {
                    case "join" -> "berichten.join";
                    case "vertrek" -> "berichten.vertrek";
                    case "eerste-join" -> "berichten.eerste-join";
                    case "welkom" -> "berichten.welkom";
                    case "koptekst" -> "tablijst.koptekst";
                    case "voettekst" -> "tablijst.voettekst";
                    case "scorebord-titel" -> "scorebord.titel";
                    case "scorebord-soort" -> "scorebord.soort";
                    case "scorebord-aantal" -> "scorebord.aantal";
                    case "scorebord-aan" -> "scorebord.ingeschakeld";
                    case "tablijst-aan" -> "tablijst.ingeschakeld";
                    default -> throw new ApiException("Onbekende instelling.");
                };
                switch (key) {
                    case "welkom", "koptekst", "voettekst" -> plugin.getConfig().set(path, lines(req));
                    case "scorebord-aantal" -> plugin.getConfig().set(path, Math.max(1, Math.min(10, number(req))));
                    case "scorebord-aan", "tablijst-aan" ->
                            plugin.getConfig().set(path, req.has("value") && req.get("value").getAsBoolean());
                    case "scorebord-soort" -> plugin.getConfig().set(path,
                            str(req, "value").equalsIgnoreCase("kills") ? "kills" : "doden");
                    default -> {
                        String value = str(req, "value");
                        if (value.length() > 300) throw new ApiException("Deze tekst is te lang.");
                        plugin.getConfig().set(path, value);
                    }
                }
                plugin.saveConfig();
                plugin.refreshSettings();
                if (plugin.boards() != null) plugin.boards().updateAll();
                log("bericht/tablijst: " + key + " gewijzigd");
                return berichten("Opgeslagen.");
            }
            case "stats/reset" -> {
                plugin.stats().resetAll();
                if (plugin.boards() != null) plugin.boards().updateSidebar();
                log("statistieken gewist");
                return berichten("De tellers staan weer op nul.");
            }
            case "spelers/import" -> {
                if (plugin.essentialsImport() == null) throw new ApiException("De spelerscommands staan uit.");
                var result = plugin.essentialsImport().run(req.has("overwrite") && req.get("overwrite").getAsBoolean());
                if (result.problem() != null) throw new ApiException(result.problem());
                log("Essentials-import: " + result.homes() + " homes van " + result.players() + " spelers");
                String msg = result.homes() + " homes van " + result.players() + " spelers overgenomen"
                        + (result.skipped() > 0 ? ", " + result.skipped() + " overgeslagen" : "")
                        + (result.spawn() ? ", spawnpunt overgenomen" : "") + ".";
                return spelers(msg);
            }
            case "server/motd" -> {
                String value = str(req, "value").replace("\r", "");
                if (value.length() > 400) throw new ApiException("Deze tekst is te lang.");
                if (value.split("\n", -1).length > 2) throw new ApiException("De MOTD mag maximaal twee regels zijn.");
                plugin.getConfig().set("motd", value.replace("\n", "\\n"));
                plugin.saveConfig();
                plugin.applyMotd();
                log("MOTD gewijzigd");
                return server(null);
            }
            case "spelers/module" -> {
                String module = str(req, "name");
                java.util.List<String> commands = MODULES.get(module);
                if (commands == null) throw new ApiException("Onbekende module.");
                boolean enable = req.has("enabled") && req.get("enabled").getAsBoolean();
                java.util.List<String> off = new java.util.ArrayList<>(
                        plugin.getConfig().getStringList("commands.uitgeschakeld"));
                for (String command : commands) {
                    off.remove(command);
                    if (!enable) off.add(command);
                }
                plugin.getConfig().set("commands.uitgeschakeld", off);
                plugin.saveConfig();
                log("module " + module + (enable ? " aangezet" : " uitgezet"));
                return spelers("Opgeslagen. Herstart de server om dit toe te passen.");
            }
            case "spelers/commando" -> {
                String name = str(req, "name").toLowerCase(Locale.ROOT);
                if (!nl.robin.rolbeheer.spelers.PlayerCommands.COMMANDS.contains(name)) {
                    throw new ApiException("Onbekend command.");
                }
                java.util.List<String> off = new java.util.ArrayList<>(
                        plugin.getConfig().getStringList("commands.uitgeschakeld"));
                boolean enable = req.has("enabled") && req.get("enabled").getAsBoolean();
                if (enable) off.remove(name);
                else if (!off.contains(name)) off.add(name);
                plugin.getConfig().set("commands.uitgeschakeld", off);
                plugin.saveConfig();
                log("command /" + name + (enable ? " aangezet" : " uitgezet"));
                return spelers("Opgeslagen. Herstart de server om dit toe te passen.");
            }
            case "spelers/warp" -> {
                if (plugin.store() == null) throw new ApiException("De spelerscommands staan uit.");
                if (!plugin.store().deleteWarp(str(req, "name"))) throw new ApiException("Die warp bestaat niet.");
                log("warp " + str(req, "name") + " verwijderd");
                return spelers("Warp verwijderd.");
            }
            case "spelers/kit" -> {
                if (plugin.store() == null) throw new ApiException("De spelerscommands staan uit.");
                String name = str(req, "name");
                if (req.has("delete") && req.get("delete").getAsBoolean()) {
                    if (!plugin.store().deleteKit(name)) throw new ApiException("Die kit bestaat niet.");
                    log("kit " + name + " verwijderd");
                    return spelers("Kit verwijderd.");
                }
                plugin.store().setKitCooldown(name, Math.max(0, Math.min(2_592_000, number(req))));
                log("kit " + name + ": wachttijd gewijzigd");
                return spelers("Wachttijd opgeslagen.");
            }
            case "server/world" -> {
                return server(settings.world(str(req, "world")).getName());
            }
            case "server/gamerule" -> {
                World world = settings.world(str(req, "world"));
                settings.setRule(world, str(req, "key"), str(req, "value"));
                log("gameregel " + str(req, "key") + " = " + str(req, "value") + " in " + world.getName());
                return server(world.getName());
            }
            case "server/whitelist" -> {
                String msg = players.setWhitelist(req.has("on") && req.get("on").getAsBoolean());
                log(msg);
                JsonObject o = server(null);
                o.addProperty("message", msg);
                return o;
            }
            case "server/player" -> {
                String msg = players.apply(str(req, "action"), str(req, "player"), str(req, "reason"));
                log(msg);
                JsonObject o = server(null);
                o.addProperty("message", msg);
                return o;
            }
            default -> throw new ApiException("Onbekende actie.");
        }
        plugin.commit();
        return state();
    }

    // ---------------------------------------------------------------- lezen

    private JsonObject state() {
        RoleManager rm = plugin.roles();
        String def = rm.defaultRoleName();
        JsonObject o = new JsonObject();
        o.addProperty("defaultRole", def);

        JsonArray roles = new JsonArray();
        for (Role r : rm.getRoles()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", r.getName());
            j.addProperty("prefix", r.getPrefix());
            j.addProperty("suffix", r.getSuffix());
            j.addProperty("prefixHtml", ComponentHtml.toHtml(Text.parse(r.getPrefix())));
            j.addProperty("suffixHtml", ComponentHtml.toHtml(Text.parse(r.getSuffix())));
            j.addProperty("priority", r.getPriority());
            j.addProperty("nameColor", r.getNameColorName());
            j.addProperty("isDefault", r.getName().equals(def));
            JsonArray perms = new JsonArray();
            r.getPermissions().forEach(perms::add);
            j.add("permissions", perms);
            JsonArray members = new JsonArray();
            for (UUID uuid : rm.getMembers(r.getName())) {
                JsonObject m = new JsonObject();
                m.addProperty("uuid", uuid.toString());
                m.addProperty("name", rm.getPlayerName(uuid));
                m.addProperty("online", Bukkit.getPlayer(uuid) != null);
                members.add(m);
            }
            j.add("members", members);
            roles.add(j);
        }
        o.add("roles", roles);

        JsonArray online = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getName());
        o.add("online", online);
        return o;
    }

    private JsonObject plugins() {
        JsonArray groups = new JsonArray();
        for (PluginGroup g : plugin.scanner().groups()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", g.name());
            j.addProperty("server", g.server());
            j.addProperty("enabled", g.enabled());
            JsonArray entries = new JsonArray();
            for (PermEntry pe : g.entries()) {
                JsonObject e = new JsonObject();
                e.addProperty("node", pe.node());
                e.addProperty("description", pe.description());
                e.addProperty("virtual", pe.virtual());
                JsonArray cmds = new JsonArray();
                pe.commands().forEach(cmds::add);
                e.add("commands", cmds);
                entries.add(e);
            }
            j.add("entries", entries);
            groups.add(j);
        }
        JsonObject o = new JsonObject();
        o.add("groups", groups);
        return o;
    }

    private JsonObject server(String worldName) {
        JsonObject o = new JsonObject();

        Map<String, String> current = settings.readProperties();
        JsonArray props = new JsonArray();
        for (ServerSettings.Setting s : ServerSettings.settings()) {
            JsonObject j = new JsonObject();
            j.addProperty("key", s.key());
            j.addProperty("label", s.label());
            j.addProperty("description", s.description());
            j.addProperty("type", s.type().name());
            j.addProperty("value", current.getOrDefault(s.key(), ""));
            j.addProperty("restart", s.restart());
            j.addProperty("min", s.min());
            j.addProperty("max", s.max());
            JsonArray options = new JsonArray();
            s.options().forEach(options::add);
            j.add("options", options);
            props.add(j);
        }
        o.add("properties", props);

        String motd = plugin.getConfig().getString("motd", "");
        // Nog niets ingesteld? Begin met wat er nu in server.properties staat.
        if (motd == null || motd.isBlank()) {
            motd = current.getOrDefault("motd", "").replace("\\n", "\n").replace('§', '&');
        }
        o.addProperty("motd", motd.replace("\\n", "\n"));
        o.addProperty("motdHtml", ComponentHtml.toHtml(Text.parse(motd.replace("\\n", "\n"))));

        World world = settings.world(worldName);
        o.addProperty("world", world.getName());
        JsonArray worlds = new JsonArray();
        Bukkit.getWorlds().forEach(w -> worlds.add(w.getName()));
        o.add("worlds", worlds);

        JsonArray rules = new JsonArray();
        for (ServerSettings.RuleInfo r : settings.rules(world)) {
            JsonObject j = new JsonObject();
            j.addProperty("key", r.key());
            j.addProperty("label", r.label());
            j.addProperty("description", r.description());
            j.addProperty("bool", r.bool());
            j.addProperty("value", r.value());
            rules.add(j);
        }
        o.add("gamerules", rules);

        JsonObject access = new JsonObject();
        access.addProperty("whitelistEnabled", players.whitelistEnabled());
        access.add("whitelist", entries(players.whitelist()));
        access.add("ops", entries(players.ops()));
        access.add("bans", entries(players.bans()));
        JsonArray online = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getName());
        access.add("online", online);
        o.add("access", access);
        return o;
    }

    private static JsonArray entries(java.util.List<PlayerAdmin.Entry> list) {
        JsonArray array = new JsonArray();
        for (PlayerAdmin.Entry e : list) {
            JsonObject j = new JsonObject();
            j.addProperty("name", e.name());
            j.addProperty("detail", e.detail());
            array.add(j);
        }
        return array;
    }

    /** Welke commands bij welke module horen. */
    static final java.util.Map<String, java.util.List<String>> MODULES = java.util.Map.of(
            "homes", java.util.List.of("home", "sethome", "delhome", "homes"),
            "spawn", java.util.List.of("spawn", "setspawn"),
            "back", java.util.List.of("back"),
            "warps", java.util.List.of("warp", "warps", "setwarp", "delwarp"),
            "kits", java.util.List.of("kit", "kits", "setkit", "delkit"),
            "teleport", java.util.List.of("tpa", "tpahere", "tpaccept", "tpdeny"),
            "berichten", java.util.List.of("msg", "r"),
            "stats", java.util.List.of("stats", "top"));

    private JsonObject spelers(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", plugin.getConfig().getBoolean("commands.ingeschakeld", true));
        o.addProperty("defaultHomes", plugin.getConfig().getInt("commands.standaard-homes", 1));
        o.addProperty("tpaSeconds", plugin.getConfig().getInt("commands.tpa-seconden", 60));
        o.addProperty("canImport", plugin.essentialsImport() != null && plugin.essentialsImport().available());
        o.addProperty("spawnFirstJoin", plugin.getConfig().getBoolean("commands.spawn.bij-eerste-join", true));
        o.addProperty("spawnOnDeath", plugin.getConfig().getBoolean("commands.spawn.bij-dood", true));
        o.addProperty("protection", plugin.getConfig().getBoolean("commands.spawn.bescherming.ingeschakeld", false));
        o.addProperty("protectionRadius", plugin.getConfig().getInt("commands.spawn.bescherming.straal", 32));
        o.addProperty("protectionPvp", plugin.getConfig().getBoolean("commands.spawn.bescherming.geen-pvp", true));
        o.addProperty("protectionMobs", plugin.getConfig().getBoolean("commands.spawn.bescherming.geen-mobschade", true));
        o.addProperty("protectionInteract", plugin.getConfig().getBoolean("commands.spawn.bescherming.geen-interactie", false));
        String chatPrefix = plugin.getConfig().getString("berichten.prefix", Text.DEFAULT_PREFIX);
        o.addProperty("chatPrefix", chatPrefix);
        o.addProperty("chatPrefixHtml", ComponentHtml.toHtml(Text.parse(chatPrefix)));

        java.util.List<String> off = plugin.getConfig().getStringList("commands.uitgeschakeld");

        JsonArray modules = new JsonArray();
        String[][] info = {
                {"homes", "Homes", "/home, /sethome, /delhome, /homes"},
                {"spawn", "Spawn", "/spawn, /setspawn"},
                {"back", "Terug", "/back na teleport of dood"},
                {"warps", "Warps", "/warp, /warps, /setwarp, /delwarp"},
                {"kits", "Kits", "/kit, /kits, /setkit, /delkit"},
                {"teleport", "Teleportverzoeken", "/tpa, /tpahere, /tpaccept, /tpdeny"},
                {"berichten", "Prive-berichten", "/msg, /r"},
                {"stats", "Statistieken", "/stats, /top"}};
        for (String[] row : info) {
            JsonObject j = new JsonObject();
            j.addProperty("name", row[0]);
            j.addProperty("label", row[1]);
            j.addProperty("commands", row[2]);
            boolean on = MODULES.get(row[0]).stream().noneMatch(off::contains);
            j.addProperty("enabled", on);
            modules.add(j);
        }
        o.add("modules", modules);

        JsonArray commands = new JsonArray();
        for (String name : nl.robin.rolbeheer.spelers.PlayerCommands.COMMANDS) {
            JsonObject j = new JsonObject();
            j.addProperty("name", name);
            j.addProperty("enabled", !off.contains(name));
            commands.add(j);
        }
        o.add("commands", commands);
        if (message != null) o.addProperty("message", message);

        JsonArray warps = new JsonArray();
        JsonArray kits = new JsonArray();
        JsonObject spawn = null;
        if (plugin.store() != null) {
            for (String name : plugin.store().warpNames()) {
                org.bukkit.Location location = plugin.store().warp(name);
                JsonObject j = new JsonObject();
                j.addProperty("name", name);
                j.addProperty("where", location == null ? "?" : location.getWorld().getName()
                        + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
                j.addProperty("permission", "rolbeheer.warp." + name);
                warps.add(j);
            }
            for (String name : plugin.store().kitNames()) {
                JsonObject j = new JsonObject();
                j.addProperty("name", name);
                j.addProperty("cooldown", plugin.store().kitCooldown(name));
                j.addProperty("items", plugin.store().kitItems(name).size());
                j.addProperty("permission", "rolbeheer.kit." + name);
                kits.add(j);
            }
            org.bukkit.Location location = plugin.store().spawn();
            if (location != null) {
                spawn = new JsonObject();
                spawn.addProperty("where", location.getWorld().getName() + " (" + location.getBlockX()
                        + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
            }
        }
        o.add("warps", warps);
        o.add("kits", kits);
        if (spawn != null) o.add("spawn", spawn);
        return o;
    }

    /** Startscherm: een samenvatting van de server, de rollen en de commands. */
    private JsonObject overzicht() {
        JsonObject o = new JsonObject();
        o.addProperty("name", plugin.displayName());
        o.addProperty("version", plugin.getPluginMeta().getVersion());
        o.addProperty("serverVersion", Bukkit.getMinecraftVersion());
        o.addProperty("online", Bukkit.getOnlinePlayers().size());
        o.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        o.addProperty("uptimeMinutes", (System.currentTimeMillis() - plugin.startedAt()) / 60000);
        o.addProperty("plugins", Bukkit.getPluginManager().getPlugins().length);

        JsonArray players = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", p.getName());
            java.util.List<Role> roles = plugin.roles().getEffectiveRoles(p.getUniqueId());
            Role role = roles.isEmpty() ? null : roles.get(0);
            j.addProperty("role", role == null ? "" : role.getName());
            j.addProperty("prefixHtml", role == null ? "" : ComponentHtml.toHtml(Text.parse(role.getPrefix())));
            players.add(j);
        }
        o.add("players", players);

        JsonArray roles = new JsonArray();
        for (Role r : plugin.roles().getRoles()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", r.getName());
            j.addProperty("priority", r.getPriority());
            j.addProperty("prefixHtml", ComponentHtml.toHtml(Text.parse(r.getPrefix())));
            j.addProperty("permissions", r.getPermissions().size());
            j.addProperty("members", r.getName().equals(plugin.roles().defaultRoleName())
                    ? -1 : plugin.roles().getMembers(r.getName()).size());
            roles.add(j);
        }
        o.add("roles", roles);

        JsonArray top = new JsonArray();
        if (plugin.stats() != null) {
            for (var entry : plugin.stats().top(false, 5)) {
                JsonObject j = new JsonObject();
                j.addProperty("name", entry.name());
                j.addProperty("deaths", entry.deaths());
                j.addProperty("kills", entry.kills());
                top.add(j);
            }
        }
        o.add("top", top);

        o.addProperty("commandsOn", plugin.store() != null);
        o.addProperty("warps", plugin.store() == null ? 0 : plugin.store().warpNames().size());
        o.addProperty("kits", plugin.store() == null ? 0 : plugin.store().kitNames().size());
        o.addProperty("spawnSet", plugin.store() != null && plugin.store().spawn() != null);
        o.addProperty("protection", plugin.getConfig().getBoolean("commands.spawn.bescherming.ingeschakeld", false));
        o.addProperty("whitelist", Bukkit.hasWhitelist());
        o.addProperty("scanned", plugin.scanner().groups().size());
        return o;
    }

    private JsonObject berichten(String message) {
        JsonObject o = new JsonObject();
        if (message != null) o.addProperty("message", message);
        o.addProperty("join", plugin.getConfig().getString("berichten.join", ""));
        o.addProperty("vertrek", plugin.getConfig().getString("berichten.vertrek", ""));
        o.addProperty("eersteJoin", plugin.getConfig().getString("berichten.eerste-join", ""));
        o.addProperty("welkom", String.join("\n", plugin.getConfig().getStringList("berichten.welkom")));
        o.addProperty("tablijst", plugin.getConfig().getBoolean("tablijst.ingeschakeld", true));
        o.addProperty("koptekst", String.join("\n", plugin.getConfig().getStringList("tablijst.koptekst")));
        o.addProperty("voettekst", String.join("\n", plugin.getConfig().getStringList("tablijst.voettekst")));
        o.addProperty("scorebord", plugin.getConfig().getBoolean("scorebord.ingeschakeld", false));
        o.addProperty("scorebordTitel", plugin.getConfig().getString("scorebord.titel", ""));
        o.addProperty("scorebordSoort", plugin.getConfig().getString("scorebord.soort", "doden"));
        o.addProperty("scorebordAantal", plugin.getConfig().getInt("scorebord.aantal", 5));

        JsonArray top = new JsonArray();
        if (plugin.stats() != null) {
            boolean kills = plugin.getConfig().getString("scorebord.soort", "doden").equalsIgnoreCase("kills");
            for (var entry : plugin.stats().top(kills, 10)) {
                JsonObject j = new JsonObject();
                j.addProperty("name", entry.name());
                j.addProperty("deaths", entry.deaths());
                j.addProperty("kills", entry.kills());
                top.add(j);
            }
        }
        o.add("top", top);
        return o;
    }

    private static java.util.List<String> lines(JsonObject req) {
        String value = str(req, "value").replace("\r", "");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : value.split("\n")) if (!line.isBlank() || !out.isEmpty()) out.add(line);
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
        return out;
    }

    private static int number(JsonObject req) {
        try {
            return req.get("value").getAsInt();
        } catch (RuntimeException e) {
            throw new ApiException("Vul een getal in.");
        }
    }

    // ---------------------------------------------------------------- schrijven

    private void createRole(JsonObject req) {
        String name = str(req, "name").toLowerCase(Locale.ROOT);
        if (!RoleManager.isValidName(name)) throw new ApiException("Ongeldige naam. Gebruik alleen a-z, 0-9, _ en -.");
        if (plugin.roles().getRole(name) != null) throw new ApiException("De rol " + name + " bestaat al.");
        plugin.roles().createRole(name);
        log("rol " + name + " aangemaakt");
    }

    private void deleteRole(JsonObject req) {
        Role r = role(req, "name");
        if (!plugin.roles().deleteRole(r.getName())) throw new ApiException("De standaardrol kun je niet verwijderen.");
        log("rol " + r.getName() + " verwijderd");
    }

    private void updateRole(JsonObject req) {
        Role r = role(req, "name");
        if (req.has("prefix")) r.setPrefix(str(req, "prefix"));
        if (req.has("suffix")) r.setSuffix(str(req, "suffix"));
        if (req.has("priority")) {
            try {
                r.setPriority(req.get("priority").getAsInt());
            } catch (RuntimeException e) {
                throw new ApiException("Prioriteit moet een getal zijn.");
            }
        }
        if (req.has("nameColor")) {
            String c = str(req, "nameColor").toLowerCase(Locale.ROOT);
            if (!c.isEmpty() && NamedTextColor.NAMES.value(c) == null) throw new ApiException("Onbekende kleur.");
            r.setNameColorName(c);
        }
        log("rol " + r.getName() + " bijgewerkt");
    }

    private void setPerm(JsonObject req) {
        Role r = role(req, "role");
        String node = node(str(req, "node"));
        r.setState(node, permState(req));
        log("rol " + r.getName() + ": " + node + " -> " + permState(req));
    }

    private void setPerms(JsonObject req) {
        Role r = role(req, "role");
        PermState state = permState(req);
        if (!req.has("nodes") || !req.get("nodes").isJsonArray()) throw new ApiException("Geen permissies opgegeven.");
        int count = 0;
        for (JsonElement e : req.getAsJsonArray("nodes")) {
            r.setState(node(e.getAsString()), state);
            count++;
        }
        log("rol " + r.getName() + ": " + count + " permissies -> " + state);
    }

    private void playerRole(JsonObject req) {
        Role r = role(req, "role");
        if (r.getName().equals(plugin.roles().defaultRoleName())) {
            throw new ApiException("Iedereen heeft de standaardrol al automatisch.");
        }
        String input = str(req, "player").trim();
        UUID uuid;
        String name;
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(input);
            if (op == null) throw new ApiException("Speler " + input + " is nog nooit op de server geweest.");
            uuid = op.getUniqueId();
            name = op.getName() != null ? op.getName() : input;
        }
        boolean add = !"remove".equals(str(req, "action"));
        if (add) plugin.roles().addRole(uuid, name, r.getName());
        else plugin.roles().removeRole(uuid, r.getName());
        log(name + (add ? " kreeg rol " : " verloor rol ") + r.getName());
    }

    // ---------------------------------------------------------------- hulpjes

    private Role role(JsonObject req, String key) {
        Role r = plugin.roles().getRole(str(req, key));
        if (r == null) throw new ApiException("Deze rol bestaat niet (meer).");
        return r;
    }

    private static String node(String raw) {
        String n = raw.toLowerCase(Locale.ROOT).replace(" ", "");
        if (!VALID_NODE.matcher(n).matches()) throw new ApiException("Ongeldige permissie: " + raw);
        return n;
    }

    private static PermState permState(JsonObject req) {
        try {
            return PermState.valueOf(str(req, "state").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException("Ongeldige status.");
        }
    }

    private static String str(JsonObject req, String key) {
        JsonElement e = req.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private void log(String msg) {
        plugin.getLogger().info("[Web] " + msg);
    }
}
