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
import nl.robin.rolbeheer.scan.PluginScanner.PluginGroup;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Alle acties van het webpaneel. Wordt altijd op de hoofdthread van de server uitgevoerd. */
public final class WebApi {

    private static final Pattern VALID_NODE = Pattern.compile("-?[a-z0-9_.*-]+");

    private final RolBeheer plugin;

    public WebApi(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public JsonElement handle(String method, String route, JsonObject req) {
        if (method.equals("GET")) {
            return switch (route) {
                case "state" -> state();
                case "plugins" -> plugins();
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
