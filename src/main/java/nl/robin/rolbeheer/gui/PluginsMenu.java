package nl.robin.rolbeheer.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.PermState;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.scan.PluginScanner.PermEntry;
import nl.robin.rolbeheer.scan.PluginScanner.PluginGroup;
import nl.robin.rolbeheer.util.Items;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Lijst van alle gevonden plugins voor één rol. */
public final class PluginsMenu extends Menu {

    private final String roleName;
    private int page;

    public PluginsMenu(RolBeheer plugin, String roleName) {
        super(plugin);
        this.roleName = roleName;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>Plugins · <name>", Placeholder.unparsed("name", roleName));
    }

    @Override protected int size() { return 54; }

    @Override
    protected void draw() {
        Role role = plugin.roles().getRole(roleName);
        if (role == null) return;
        List<PluginGroup> groups = plugin.scanner().groups();
        int pages = Math.max(1, (groups.size() + 44) / 45);
        page = Math.min(page, pages - 1);

        for (int i = 0; i < 45; i++) {
            int index = page * 45 + i;
            if (index >= groups.size()) break;
            PluginGroup g = groups.get(index);

            int allowed = 0, denied = 0;
            for (PermEntry pe : g.entries()) {
                PermState s = role.state(pe.node());
                if (s == PermState.ALLOW) allowed++;
                else if (s == PermState.DENY) denied++;
            }

            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<gray>Permissies: <white>" + g.entries().size()));
            lore.add(Text.item("<gray>Commands: <white>" + g.commandCount()));
            lore.add(Text.item("<green>Toegestaan: " + allowed + "  <red>Verboden: " + denied));
            if (!g.enabled()) lore.add(Text.item("<red>Plugin staat uit"));
            lore.add(Component.empty());
            lore.add(Text.item("<yellow>Klik om permissies te beheren"));

            Material icon = g.server() ? Material.GRASS_BLOCK : (g.enabled() ? Material.BOOK : Material.GRAY_DYE);
            set(i, Items.of(icon, Text.item("<gold><bold><name>", Placeholder.unparsed("name", g.name())), lore),
                    e -> go((Player) e.getWhoClicked(), new PermsMenu(plugin, roleName, g.name())));
        }

        set(45, Items.of(Material.ARROW, Text.item("<gray>Terug")),
                e -> go((Player) e.getWhoClicked(), new RoleMenu(plugin, roleName)));
        if (page > 0) {
            set(48, Items.of(Material.PAPER, Text.item("<yellow>« Vorige")), e -> { page--; redraw(); });
        }
        set(49, Items.of(Material.MAP, Text.item("<gray>Pagina " + (page + 1) + " / " + pages)), null);
        if (page < pages - 1) {
            set(50, Items.of(Material.PAPER, Text.item("<yellow>Volgende »")), e -> { page++; redraw(); });
        }
        fillRow(5);
    }
}
