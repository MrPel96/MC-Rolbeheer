package nl.robin.rolbeheer.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.PermState;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.util.Items;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Eén rol bewerken. */
public final class RoleMenu extends Menu {

    static final Pattern VALID_NODE = Pattern.compile("-?[a-z0-9_.*-]+");

    /** Volgorde van kleuren bij doorklikken; "" = geen kleur. */
    private static final String[] COLORS = {"", "white", "gray", "dark_gray", "black", "red", "dark_red",
            "gold", "yellow", "green", "dark_green", "aqua", "dark_aqua", "blue", "dark_blue",
            "light_purple", "dark_purple"};
    private static final Material[] COLOR_ITEMS = {Material.WHITE_WOOL, Material.WHITE_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.GRAY_WOOL, Material.BLACK_WOOL, Material.RED_WOOL,
            Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
            Material.GREEN_WOOL, Material.LIGHT_BLUE_WOOL, Material.CYAN_WOOL, Material.BLUE_WOOL,
            Material.BLUE_WOOL, Material.MAGENTA_WOOL, Material.PURPLE_WOOL};

    private final String roleName;
    private boolean confirmDelete;

    public RoleMenu(RolBeheer plugin, String roleName) {
        super(plugin);
        this.roleName = roleName;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>Rol: <name>", Placeholder.unparsed("name", roleName));
    }

    @Override protected int size() { return 36; }

    @Override
    protected void draw() {
        Role role = plugin.roles().getRole(roleName);
        if (role == null) return;
        boolean isDefault = role.getName().equals(plugin.roles().defaultRoleName());

        // Prefix
        set(10, Items.of(Material.NAME_TAG, Text.item("<gold>Prefix"), List.of(
                Text.item("<gray>Nu: <prefix>", Placeholder.component("prefix", orNone(role.getPrefix()))),
                Text.item("<gray>Voorbeeld: <prefix><white>Steve",
                        Placeholder.component("prefix", RolesMenu.preview(role))),
                Component.empty(),
                Text.item("<yellow>Linksklik: <gray>wijzigen"),
                Text.item("<yellow>Rechtsklik: <gray>leegmaken"),
                Text.item("<dark_gray>Kleuren: &c of \\<red>"))), e -> {
            if (e.isRightClick()) {
                role.setPrefix("");
                changed();
                return;
            }
            askText((Player) e.getWhoClicked(), "Typ de nieuwe prefix, bijv. <white>&c[Admin]</white>:", role::setPrefix);
        });

        // Suffix
        set(11, Items.of(Material.OAK_SIGN, Text.item("<gold>Suffix"), List.of(
                Text.item("<gray>Nu: <suffix>", Placeholder.component("suffix", orNone(role.getSuffix()))),
                Component.empty(),
                Text.item("<yellow>Linksklik: <gray>wijzigen"),
                Text.item("<yellow>Rechtsklik: <gray>leegmaken"))), e -> {
            if (e.isRightClick()) {
                role.setSuffix("");
                changed();
                return;
            }
            askText((Player) e.getWhoClicked(), "Typ de nieuwe suffix:", role::setSuffix);
        });

        // Naamkleur
        int ci = colorIndex(role.getNameColorName());
        NamedTextColor current = role.getNameColor();
        set(12, Items.of(COLOR_ITEMS[ci], Text.item("<gold>Naamkleur"), List.of(
                Text.item("<gray>Nu: ").append(current == null
                        ? Component.text("geen", NamedTextColor.WHITE)
                        : Component.text(role.getNameColorName(), current)),
                Component.empty(),
                Text.item("<yellow>Linksklik: <gray>volgende kleur"),
                Text.item("<yellow>Rechtsklik: <gray>vorige kleur"))), e -> {
            int next = (ci + (e.isRightClick() ? COLORS.length - 1 : 1)) % COLORS.length;
            role.setNameColorName(COLORS[next]);
            changed();
        });

        // Prioriteit
        set(13, Items.of(Material.EXPERIENCE_BOTTLE, Text.item("<gold>Prioriteit: <white>" + role.getPriority()), List.of(
                Text.item("<gray>Hoogste prioriteit bepaalt de prefix"),
                Text.item("<gray>en wint bij tegenstrijdige permissies."),
                Component.empty(),
                Text.item("<yellow>Linksklik: <gray>+1   <yellow>Shift: <gray>+10"),
                Text.item("<yellow>Rechtsklik: <gray>-1   <yellow>Shift: <gray>-10"))), e -> {
            int step = e.isShiftClick() ? 10 : 1;
            role.setPriority(role.getPriority() + (e.isRightClick() ? -step : step));
            changed();
        });

        // Permissies per plugin
        set(14, Items.of(Material.BOOK, Text.item("<gold>Permissies per plugin"), List.of(
                Text.item("<gray>Automatisch gevonden plugins,"),
                Text.item("<gray>hun commands en permissies."),
                Component.empty(),
                Text.item("<yellow>Klik om te openen"))),
                e -> go((Player) e.getWhoClicked(), new PluginsMenu(plugin, roleName)));

        // Handmatige permissies
        List<Component> permLore = new ArrayList<>();
        List<String> perms = role.getPermissions();
        if (perms.isEmpty()) permLore.add(Text.item("<dark_gray>Nog geen permissies"));
        for (int i = 0; i < Math.min(12, perms.size()); i++) {
            String p = perms.get(i);
            permLore.add(Text.noItalic(Component.text(p.startsWith("-") ? "✖ " + p.substring(1) : "✔ " + p,
                    p.startsWith("-") ? NamedTextColor.RED : NamedTextColor.GREEN)));
        }
        if (perms.size() > 12) permLore.add(Text.item("<dark_gray>… en nog " + (perms.size() - 12)));
        permLore.add(Component.empty());
        permLore.add(Text.item("<yellow>Linksklik: <gray>permissie toevoegen"));
        permLore.add(Text.item("<yellow>Rechtsklik: <gray>permissie verwijderen"));
        permLore.add(Text.item("<dark_gray>Met - ervoor = verbieden, * = alles"));
        set(15, Items.of(Material.WRITABLE_BOOK, Text.item("<gold>Handmatige permissies"), permLore), e -> {
            Player p = (Player) e.getWhoClicked();
            boolean remove = e.isRightClick();
            plugin.chatInput().ask(p, remove ? "Typ de permissie die je wilt verwijderen:"
                    : "Typ de permissie (bijv. <white>essentials.fly</white> of <white>-essentials.fly</white>):", input -> {
                String node = input.toLowerCase(Locale.ROOT).replace(" ", "");
                if (!VALID_NODE.matcher(node).matches()) {
                    Text.send(p, "<red>Ongeldige permissie.");
                } else {
                    Role r = plugin.roles().getRole(roleName);
                    if (r != null) {
                        if (remove) r.setState(node, PermState.NONE);
                        else r.setState(node, node.startsWith("-") ? PermState.DENY : PermState.ALLOW);
                        plugin.commit();
                        Text.send(p, remove ? "Permissie verwijderd." : "Permissie opgeslagen.");
                    }
                }
                new RoleMenu(plugin, roleName).open(p);
            });
        });

        // Spelers
        if (isDefault) {
            set(16, Items.of(Material.PLAYER_HEAD, Text.item("<gold>Spelers"), List.of(
                    Text.item("<gray>Dit is de standaardrol:"),
                    Text.item("<gray>iedere speler heeft deze automatisch."))), null);
        } else {
            set(16, Items.of(Material.PLAYER_HEAD, Text.item("<gold>Spelers"), List.of(
                    Text.item("<gray>Leden: <white>" + plugin.roles().getMembers(roleName).size()),
                    Component.empty(),
                    Text.item("<yellow>Klik om spelers toe te voegen/weg te halen"))),
                    e -> go((Player) e.getWhoClicked(), new PlayersMenu(plugin, roleName)));
        }

        // Terug
        set(27, Items.of(Material.ARROW, Text.item("<gray>Terug")),
                e -> go((Player) e.getWhoClicked(), new RolesMenu(plugin)));

        // Verwijderen
        if (!isDefault) {
            set(35, Items.of(Material.BARRIER, Text.item(confirmDelete ? "<red><bold>Zeker weten? Klik nogmaals" : "<red>Rol verwijderen"),
                    List.of(Text.item("<gray>Spelers verliezen deze rol."))), e -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    redraw();
                    return;
                }
                plugin.roles().deleteRole(roleName);
                plugin.commit();
                Text.send(e.getWhoClicked(), "Rol <white><name></white> verwijderd.", Placeholder.unparsed("name", roleName));
                go((Player) e.getWhoClicked(), new RolesMenu(plugin));
            });
        }

        fillRow(3);
    }

    private void changed() {
        plugin.commit();
        redraw();
    }

    private void askText(Player p, String question, java.util.function.Consumer<String> setter) {
        plugin.chatInput().ask(p, question, input -> {
            if (plugin.roles().getRole(roleName) != null) {
                setter.accept(input);
                plugin.commit();
                Text.send(p, "Opgeslagen.");
            }
            new RoleMenu(plugin, roleName).open(p);
        });
    }

    private static Component orNone(String text) {
        return text.isBlank() ? Component.text("geen", NamedTextColor.DARK_GRAY) : Text.parse(text);
    }

    private static int colorIndex(String name) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(name)) return i;
        return 0;
    }

}
