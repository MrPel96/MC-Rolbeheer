package nl.robin.rolbeheer.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/** Permissies en commands van één plugin aan/uit zetten voor één rol. */
public final class PermsMenu extends Menu {

    private final String roleName;
    private final String groupName;
    private int page;
    private boolean onlyCommands;

    public PermsMenu(RolBeheer plugin, String roleName, String groupName) {
        super(plugin);
        this.roleName = roleName;
        this.groupName = groupName;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray><group> · <role>",
                Placeholder.unparsed("group", groupName), Placeholder.unparsed("role", roleName));
    }

    @Override protected int size() { return 54; }

    @Override
    protected void draw() {
        Role role = plugin.roles().getRole(roleName);
        PluginGroup group = plugin.scanner().group(groupName);
        if (role == null || group == null) return;

        List<PermEntry> entries = new ArrayList<>();
        for (PermEntry pe : group.entries()) if (!onlyCommands || pe.isCommand()) entries.add(pe);

        int pages = Math.max(1, (entries.size() + 44) / 45);
        page = Math.min(page, pages - 1);

        for (int i = 0; i < 45; i++) {
            int index = page * 45 + i;
            if (index >= entries.size()) break;
            PermEntry pe = entries.get(index);
            PermState state = role.state(pe.node());
            set(i, entryItem(pe, state), e -> {
                role.setState(pe.node(), e.isRightClick() ? PermState.NONE : state.next());
                plugin.commit();
                redraw();
            });
        }

        set(45, Items.of(Material.ARROW, Text.item("<gray>Terug")),
                e -> go((Player) e.getWhoClicked(), new PluginsMenu(plugin, roleName)));

        set(46, Items.of(Material.LIME_CONCRETE, Text.item("<green>Alles toestaan"),
                List.of(Text.item("<gray>Voor alles wat nu zichtbaar is."))), e -> bulk(role, entries, PermState.ALLOW));
        set(47, Items.of(Material.RED_CONCRETE, Text.item("<red>Alles verbieden"),
                List.of(Text.item("<gray>Voor alles wat nu zichtbaar is."))), e -> bulk(role, entries, PermState.DENY));
        set(48, page > 0 ? Items.of(Material.PAPER, Text.item("<yellow>« Vorige")) : Items.filler(),
                page > 0 ? e -> { page--; redraw(); } : null);
        set(49, Items.of(onlyCommands ? Material.COMMAND_BLOCK : Material.CHEST,
                Text.item(onlyCommands ? "<gold>Toon: alleen commands" : "<gold>Toon: alles"), List.of(
                        Text.item("<gray>Pagina " + (page + 1) + " / " + pages),
                        Text.item("<yellow>Klik om te wisselen"))), e -> {
            onlyCommands = !onlyCommands;
            page = 0;
            redraw();
        });
        set(50, page < pages - 1 ? Items.of(Material.PAPER, Text.item("<yellow>Volgende »")) : Items.filler(),
                page < pages - 1 ? e -> { page++; redraw(); } : null);
        set(51, Items.of(Material.WHITE_CONCRETE, Text.item("<gray>Alles resetten"),
                List.of(Text.item("<gray>Terug naar standaard van de plugin."))), e -> bulk(role, entries, PermState.NONE));
        set(53, Items.of(Material.BOOK, Text.item("<aqua>Uitleg"), List.of(
                Text.item("<green>Toegestaan <gray>= rol krijgt het"),
                Text.item("<red>Verboden <gray>= rol krijgt het niet"),
                Text.item("<gray>Niet ingesteld <dark_gray>= plugin-standaard"),
                Text.item("<dark_gray>(meestal: alleen OP)"),
                Component.empty(),
                Text.item("<yellow>Linksklik: <gray>wisselen"),
                Text.item("<yellow>Rechtsklik: <gray>terug naar niet ingesteld"))), null);
        fillRow(5);
    }

    private void bulk(Role role, List<PermEntry> entries, PermState state) {
        for (PermEntry pe : entries) role.setState(pe.node(), state);
        plugin.commit();
        redraw();
    }

    private static org.bukkit.inventory.ItemStack entryItem(PermEntry pe, PermState state) {
        Material material = switch (state) {
            case ALLOW -> Material.LIME_DYE;
            case DENY -> Material.RED_DYE;
            case NONE -> Material.GRAY_DYE;
        };
        NamedTextColor color = switch (state) {
            case ALLOW -> NamedTextColor.GREEN;
            case DENY -> NamedTextColor.RED;
            case NONE -> NamedTextColor.WHITE;
        };

        Component name = pe.isCommand()
                ? Component.text(String.join(", ", pe.commands()), color)
                : Component.text(pe.node(), color);

        List<Component> lore = new ArrayList<>();
        if (pe.isCommand()) lore.add(Text.noItalic(Component.text(pe.node(), NamedTextColor.DARK_GRAY)));
        if (pe.virtual()) {
            lore.addAll(Text.wrap("Geen eigen permissie: iedereen mag dit standaard. Zet op Verboden om het te blokkeren.",
                    38, NamedTextColor.YELLOW));
        } else {
            lore.addAll(Text.wrap(pe.description(), 38, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Text.item("<gray>Status: " + state.label()));
        return Items.of(material, name, lore);
    }
}
