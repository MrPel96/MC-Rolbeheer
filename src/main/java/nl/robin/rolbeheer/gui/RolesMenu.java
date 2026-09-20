package nl.robin.rolbeheer.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.role.RoleManager;
import nl.robin.rolbeheer.util.Items;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hoofdmenu: overzicht van alle rollen. */
public final class RolesMenu extends Menu {

    public RolesMenu(RolBeheer plugin) {
        super(plugin);
    }

    @Override protected Component title() { return Text.mm("<dark_gray>Rollen"); }
    @Override protected int size() { return 54; }

    @Override
    protected void draw() {
        List<Role> roles = plugin.roles().getRoles();
        String def = plugin.roles().defaultRoleName();

        for (int i = 0; i < Math.min(45, roles.size()); i++) {
            Role role = roles.get(i);
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<gray>Voorbeeld: <prefix><white>Steve",
                    Placeholder.component("prefix", preview(role))));
            lore.add(Text.item("<gray>Prioriteit: <white>" + role.getPriority()));
            lore.add(Text.item("<gray>Permissies: <white>" + role.getPermissions().size()));
            if (role.getName().equals(def)) {
                lore.add(Text.item("<gray>Spelers: <white>iedereen <dark_gray>(standaardrol)"));
            } else {
                lore.add(Text.item("<gray>Spelers: <white>" + plugin.roles().getMembers(role.getName()).size()));
            }
            lore.add(Component.empty());
            lore.add(Text.item("<yellow>Klik om te bewerken"));

            set(i, Items.of(Material.NAME_TAG, Text.item("<gold><bold><name>",
                    Placeholder.unparsed("name", role.getName())), lore),
                    e -> go((Player) e.getWhoClicked(), new RoleMenu(plugin, role.getName())));
        }

        set(45, Items.of(Material.BOOK, Text.item("<aqua>Hoe werkt het?"), List.of(
                Text.item("<gray>Iedere speler heeft de standaardrol."),
                Text.item("<gray>Extra rollen geef je via het rolmenu"),
                Text.item("<gray>of met <white>/rol speler \\<naam> geef \\<rol>"),
                Text.item("<gray>Hoogste prioriteit = prefix die je ziet"),
                Text.item("<gray>en wint bij tegenstrijdige permissies."))), null);

        set(49, Items.of(Material.EMERALD, Text.item("<green><bold>Nieuwe rol"), List.of(
                Text.item("<gray>Klik en typ de naam in de chat."),
                Text.item("<dark_gray>Alleen a-z, 0-9, _ en -"))), e -> {
            Player p = (Player) e.getWhoClicked();
            plugin.chatInput().ask(p, "Typ de naam van de nieuwe rol:", input -> {
                String name = input.toLowerCase(Locale.ROOT);
                if (!RoleManager.isValidName(name)) {
                    Text.send(p, "<red>Ongeldige naam. Gebruik alleen a-z, 0-9, _ en -.");
                    new RolesMenu(plugin).open(p);
                    return;
                }
                if (plugin.roles().getRole(name) != null) {
                    Text.send(p, "<red>Die rol bestaat al.");
                    new RolesMenu(plugin).open(p);
                    return;
                }
                plugin.roles().createRole(name);
                plugin.commit();
                Text.send(p, "Rol <white><name></white> aangemaakt.", Placeholder.unparsed("name", name));
                new RoleMenu(plugin, name).open(p);
            });
        });

        set(53, Items.of(Material.COMPASS, Text.item("<aqua>Plugins opnieuw scannen"), List.of(
                Text.item("<gray>Gevonden: <white>" + plugin.scanner().groups().size() + " groepen"),
                Text.item("<gray>Gebeurt ook automatisch bij het"),
                Text.item("<gray>laden van plugins."))), e -> {
            plugin.scanner().rescan();
            plugin.sync().applyAll();
            Text.send(e.getWhoClicked(), "Plugins opnieuw gescand.");
            redraw();
        });

        fillRow(5);
    }

    static Component preview(Role role) {
        if (role.getPrefix().isBlank()) return Component.empty();
        return Component.textOfChildren(Text.parse(role.getPrefix()), Component.space());
    }
}
