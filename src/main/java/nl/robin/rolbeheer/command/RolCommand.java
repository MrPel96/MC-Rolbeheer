package nl.robin.rolbeheer.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.gui.RolesMenu;
import nl.robin.rolbeheer.role.PermState;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.role.RoleManager;
import nl.robin.rolbeheer.scan.PluginScanner.PermEntry;
import nl.robin.rolbeheer.scan.PluginScanner.PluginGroup;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RolCommand implements TabExecutor {

    private static final Pattern VALID_NODE = Pattern.compile("-?[a-z0-9_.*-]+");
    private static final List<String> SUBS = List.of("menu", "lijst", "maak", "verwijder", "info", "prefix",
            "suffix", "kleur", "prioriteit", "perm", "speler", "plugins", "plugin", "check", "web", "herlaad", "help");

    private final RolBeheer plugin;

    public RolCommand(RolBeheer plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String label, @NotNull String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("menu")) {
            if (s instanceof Player p) new RolesMenu(plugin).open(p);
            else help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "lijst" -> list(s);
            case "maak" -> create(s, a);
            case "verwijder" -> delete(s, a);
            case "info" -> info(s, a);
            case "prefix", "suffix" -> affix(s, a);
            case "kleur" -> color(s, a);
            case "prioriteit" -> priority(s, a);
            case "perm" -> perm(s, a);
            case "speler" -> player(s, a);
            case "plugins" -> plugins(s);
            case "plugin" -> pluginInfo(s, a);
            case "check" -> check(s, a);
            case "web" -> web(s);
            case "herlaad" -> {
                plugin.reloadAll();
                Text.send(s, "Config, rollen en plugins opnieuw geladen.");
            }
            default -> help(s);
        }
        return true;
    }

    // ---------------------------------------------------------------- subcommands

    private void help(CommandSender s) {
        Text.send(s, "<gold><bold>RolBeheer commands");
        String[][] lines = {
                {"/rol", "menu openen"},
                {"/rol lijst", "alle rollen"},
                {"/rol maak <rol>", "nieuwe rol"},
                {"/rol verwijder <rol>", "rol verwijderen"},
                {"/rol info <rol>", "details van een rol"},
                {"/rol prefix <rol> <tekst|geen>", "prefix instellen (&c of <red>)"},
                {"/rol suffix <rol> <tekst|geen>", "suffix instellen"},
                {"/rol kleur <rol> <kleur|geen>", "naamkleur"},
                {"/rol prioriteit <rol> <0-999>", "hoogste wint"},
                {"/rol perm <rol> toevoegen <perm>", "- ervoor = verbieden"},
                {"/rol perm <rol> verwijderen <perm>", "permissie weghalen"},
                {"/rol speler <naam> geef|neem <rol>", "rol geven of afpakken"},
                {"/rol speler <naam> info", "rollen van een speler"},
                {"/rol plugins", "gevonden plugins"},
                {"/rol plugin <naam>", "permissies/commands van een plugin"},
                {"/rol check <speler> <perm>", "heeft speler deze permissie?"},
                {"/rol web", "inloglink voor het webpaneel"},
                {"/rol herlaad", "alles opnieuw laden"}};
        for (String[] l : lines) {
            s.sendMessage(Text.mm("<white><u></white> <dark_gray>- <gray><d>",
                    Placeholder.unparsed("u", l[0]), Placeholder.unparsed("d", l[1])));
        }
    }

    private void list(CommandSender s) {
        Text.send(s, "<gold>Rollen <dark_gray>(hoogste prioriteit eerst)");
        for (Role r : plugin.roles().getRoles()) {
            s.sendMessage(Text.mm("<dark_gray>• <white><name> <dark_gray>[<gray><prio></gray>]</dark_gray> <prefix>",
                    Placeholder.unparsed("name", r.getName()),
                    Placeholder.unparsed("prio", String.valueOf(r.getPriority())),
                    Placeholder.component("prefix", Text.parse(r.getPrefix()))));
        }
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { usage(s, "/rol maak <rol>"); return; }
        String name = a[1].toLowerCase(Locale.ROOT);
        if (!RoleManager.isValidName(name)) { Text.send(s, "<red>Ongeldige naam. Gebruik alleen a-z, 0-9, _ en -."); return; }
        if (plugin.roles().getRole(name) != null) { Text.send(s, "<red>Die rol bestaat al."); return; }
        plugin.roles().createRole(name);
        plugin.commit();
        Text.send(s, "Rol <white><name></white> aangemaakt.", Placeholder.unparsed("name", name));
    }

    private void delete(CommandSender s, String[] a) {
        Role r = role(s, a, 1, "/rol verwijder <rol>");
        if (r == null) return;
        if (!plugin.roles().deleteRole(r.getName())) {
            Text.send(s, "<red>De standaardrol kun je niet verwijderen.");
            return;
        }
        plugin.commit();
        Text.send(s, "Rol <white><name></white> verwijderd.", Placeholder.unparsed("name", r.getName()));
    }

    private void info(CommandSender s, String[] a) {
        Role r = role(s, a, 1, "/rol info <rol>");
        if (r == null) return;
        Text.send(s, "<gold>Rol <white><name>", Placeholder.unparsed("name", r.getName()));
        s.sendMessage(Text.mm("<gray>Prefix: <p>  <gray>Suffix: <sf>",
                Placeholder.component("p", Text.parse(r.getPrefix())),
                Placeholder.component("sf", Text.parse(r.getSuffix()))));
        s.sendMessage(Text.mm("<gray>Prioriteit: <white>" + r.getPriority()
                + "  <gray>Naamkleur: <white>" + (r.getNameColorName().isEmpty() ? "geen" : r.getNameColorName())));
        boolean def = r.getName().equals(plugin.roles().defaultRoleName());
        s.sendMessage(Text.mm("<gray>Leden: <white>" + (def ? "iedereen (standaardrol)"
                : String.valueOf(plugin.roles().getMembers(r.getName()).size()))));
        s.sendMessage(Text.mm("<gray>Permissies (" + r.getPermissions().size() + "):"));
        for (String p : r.getPermissions()) {
            boolean deny = p.startsWith("-");
            s.sendMessage(Component.text("  " + (deny ? "✖ " + p.substring(1) : "✔ " + p),
                    deny ? NamedTextColor.RED : NamedTextColor.GREEN));
        }
    }

    private void affix(CommandSender s, String[] a) {
        boolean prefix = a[0].equalsIgnoreCase("prefix");
        String usage = "/rol " + (prefix ? "prefix" : "suffix") + " <rol> <tekst|geen>";
        Role r = role(s, a, 1, usage);
        if (r == null) return;
        if (a.length < 3) { usage(s, usage); return; }
        String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
        if (text.equalsIgnoreCase("geen")) text = "";
        if (prefix) r.setPrefix(text); else r.setSuffix(text);
        plugin.commit();
        Text.send(s, (prefix ? "Prefix" : "Suffix") + " van <white><name></white> is nu: <t>",
                Placeholder.unparsed("name", r.getName()), Placeholder.component("t", Text.parse(text)));
    }

    private void color(CommandSender s, String[] a) {
        Role r = role(s, a, 1, "/rol kleur <rol> <kleur|geen>");
        if (r == null) return;
        if (a.length < 3) { usage(s, "/rol kleur <rol> <kleur|geen>"); return; }
        String c = a[2].toLowerCase(Locale.ROOT);
        if (c.equals("geen")) c = "";
        else if (NamedTextColor.NAMES.value(c) == null) {
            Text.send(s, "<red>Onbekende kleur. Kies uit: <white><list>",
                    Placeholder.unparsed("list", String.join(", ", NamedTextColor.NAMES.keys())));
            return;
        }
        r.setNameColorName(c);
        plugin.commit();
        Text.send(s, "Naamkleur opgeslagen.");
    }

    private void priority(CommandSender s, String[] a) {
        Role r = role(s, a, 1, "/rol prioriteit <rol> <0-999>");
        if (r == null) return;
        try {
            r.setPriority(Integer.parseInt(a[2]));
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
            usage(s, "/rol prioriteit <rol> <0-999>");
            return;
        }
        plugin.commit();
        Text.send(s, "Prioriteit van <white><name></white> is nu " + r.getPriority() + ".",
                Placeholder.unparsed("name", r.getName()));
    }

    private void perm(CommandSender s, String[] a) {
        String usage = "/rol perm <rol> toevoegen|verwijderen <permissie>";
        Role r = role(s, a, 1, usage);
        if (r == null) return;
        if (a.length < 4) { usage(s, usage); return; }
        String node = a[3].toLowerCase(Locale.ROOT);
        if (!VALID_NODE.matcher(node).matches()) { Text.send(s, "<red>Ongeldige permissie."); return; }
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "toevoegen" -> r.setState(node, node.startsWith("-") ? PermState.DENY : PermState.ALLOW);
            case "verwijderen" -> r.setState(node, PermState.NONE);
            default -> { usage(s, usage); return; }
        }
        plugin.commit();
        Text.send(s, "Permissies van <white><name></white> bijgewerkt.", Placeholder.unparsed("name", r.getName()));
    }

    private void player(CommandSender s, String[] a) {
        String usage = "/rol speler <naam> geef|neem|info [rol]";
        if (a.length < 3) { usage(s, usage); return; }

        UUID uuid;
        String name;
        Player online = Bukkit.getPlayerExact(a[1]);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(a[1]);
            if (op == null) {
                Text.send(s, "<red>Speler <white><n></white> is nog nooit op de server geweest.", Placeholder.unparsed("n", a[1]));
                return;
            }
            uuid = op.getUniqueId();
            name = op.getName() != null ? op.getName() : a[1];
        }

        String action = a[2].toLowerCase(Locale.ROOT);
        if (action.equals("info")) {
            String roles = plugin.roles().getEffectiveRoles(uuid).stream().map(Role::getName).collect(Collectors.joining(", "));
            Text.send(s, "<white><n></white> heeft: <white><r>", Placeholder.unparsed("n", name), Placeholder.unparsed("r", roles));
            return;
        }

        Role r = role(s, a, 3, usage);
        if (r == null) return;
        if (r.getName().equals(plugin.roles().defaultRoleName())) {
            Text.send(s, "<red>Iedereen heeft de standaardrol al automatisch.");
            return;
        }
        boolean changed;
        switch (action) {
            case "geef" -> changed = plugin.roles().addRole(uuid, name, r.getName());
            case "neem" -> changed = plugin.roles().removeRole(uuid, r.getName());
            default -> { usage(s, usage); return; }
        }
        if (!changed) {
            Text.send(s, "<gray>Er is niets veranderd.");
            return;
        }
        plugin.commit();
        Text.send(s, "<white><n></white> " + (action.equals("geef") ? "heeft nu" : "heeft niet meer")
                        + " de rol <white><r></white>.",
                Placeholder.unparsed("n", name), Placeholder.unparsed("r", r.getName()));
    }

    private void plugins(CommandSender s) {
        Text.send(s, "<gold>Gevonden plugins");
        for (PluginGroup g : plugin.scanner().groups()) {
            s.sendMessage(Text.mm("<dark_gray>• <white><n> <gray>(" + g.entries().size() + " permissies, "
                    + g.commandCount() + " commands)", Placeholder.unparsed("n", g.name())));
        }
        s.sendMessage(Text.mm("<gray>Details: <white>/rol plugin \\<naam></white> of gebruik het menu."));
    }

    private void pluginInfo(CommandSender s, String[] a) {
        if (a.length < 2) { usage(s, "/rol plugin <naam>"); return; }
        String name = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        PluginGroup g = plugin.scanner().group(name);
        if (g == null) { Text.send(s, "<red>Plugin niet gevonden. Zie /rol plugins."); return; }
        Text.send(s, "<gold><n>", Placeholder.unparsed("n", g.name()));
        for (PermEntry pe : g.entries()) {
            String cmds = pe.isCommand() ? " " + String.join(", ", pe.commands()) : "";
            s.sendMessage(Text.mm("<dark_gray>• <white><node></white><yellow><cmds>",
                    Placeholder.unparsed("node", pe.node()), Placeholder.unparsed("cmds", cmds)));
        }
    }

    private void check(CommandSender s, String[] a) {
        if (a.length < 3) { usage(s, "/rol check <speler> <permissie>"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { Text.send(s, "<red>Speler moet online zijn."); return; }
        String node = a[2].toLowerCase(Locale.ROOT);
        boolean has = p.hasPermission(node);
        boolean set = p.isPermissionSet(node);
        Text.send(s, "<white><n></white> → <white><node></white>: " + (has ? "<green>ja" : "<red>nee")
                        + (set ? " <dark_gray>(door rol)" : " <dark_gray>(standaard van plugin)"),
                Placeholder.unparsed("n", p.getName()), Placeholder.unparsed("node", node));
    }

    private void web(CommandSender s) {
        if (!plugin.web().isRunning()) {
            Text.send(s, "<red>Het webpaneel staat uit of kon niet starten. Kijk in config.yml en de console.");
            return;
        }
        String link = plugin.web().createLoginLink();
        Text.send(s, "Klik om het webpaneel te openen <dark_gray>(link werkt één keer, 5 minuten geldig)</dark_gray>:");
        s.sendMessage(Component.text(link, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(link)));
    }

    // ---------------------------------------------------------------- hulpjes

    private Role role(CommandSender s, String[] a, int index, String usage) {
        if (a.length <= index) {
            usage(s, usage);
            return null;
        }
        Role r = plugin.roles().getRole(a[index]);
        if (r == null) Text.send(s, "<red>Rol <white><r></white> bestaat niet.", Placeholder.unparsed("r", a[index]));
        return r;
    }

    private void usage(CommandSender s, String usage) {
        Text.send(s, "<red>Gebruik: <white><u>", Placeholder.unparsed("u", usage));
    }

    // ---------------------------------------------------------------- tab-aanvulling

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String label, @NotNull String[] a) {
        List<String> options = new ArrayList<>();
        List<String> roles = plugin.roles().getRoles().stream().map(Role::getName).toList();
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (a.length == 1) {
            options.addAll(SUBS);
        } else if (a.length == 2) {
            switch (sub) {
                case "verwijder", "info", "prefix", "suffix", "kleur", "prioriteit", "perm" -> options.addAll(roles);
                case "speler", "check" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                case "plugin" -> plugin.scanner().groups().forEach(g -> options.add(g.name()));
                default -> { }
            }
        } else if (a.length == 3) {
            switch (sub) {
                case "kleur" -> { options.add("geen"); options.addAll(NamedTextColor.NAMES.keys()); }
                case "prefix", "suffix" -> options.add("geen");
                case "perm" -> options.addAll(List.of("toevoegen", "verwijderen"));
                case "speler" -> options.addAll(List.of("geef", "neem", "info"));
                case "check" -> options.addAll(permSuggestions(a[2]));
                default -> { }
            }
        } else if (a.length == 4) {
            if (sub.equals("speler")) options.addAll(roles);
            if (sub.equals("perm")) {
                Role r = plugin.roles().getRole(a[1]);
                if (a[2].equalsIgnoreCase("verwijderen") && r != null) options.addAll(r.getPermissions());
                else options.addAll(permSuggestions(a[3]));
            }
        }

        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).limit(100).toList();
    }

    private List<String> permSuggestions(String typed) {
        boolean neg = typed.startsWith("-");
        String start = (neg ? typed.substring(1) : typed).toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if ("*".startsWith(start)) out.add((neg ? "-" : "") + "*");
        for (String n : plugin.scanner().allNodes()) {
            if (n.startsWith(start)) out.add((neg ? "-" : "") + n);
        }
        out.sort(null);
        return out;
    }
}
