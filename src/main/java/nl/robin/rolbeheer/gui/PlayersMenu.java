package nl.robin.rolbeheer.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Items;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Online spelers + huidige leden; klik om de rol te geven of af te pakken. */
public final class PlayersMenu extends Menu {

    private final String roleName;
    private int page;

    public PlayersMenu(RolBeheer plugin, String roleName) {
        super(plugin);
        this.roleName = roleName;
    }

    @Override
    protected Component title() {
        return Text.mm("<dark_gray>Spelers · <name>", Placeholder.unparsed("name", roleName));
    }

    @Override protected int size() { return 54; }

    @Override
    protected void draw() {
        if (plugin.roles().getRole(roleName) == null) return;

        Map<UUID, String> players = new LinkedHashMap<>();
        for (UUID uuid : plugin.roles().getMembers(roleName)) players.put(uuid, plugin.roles().getPlayerName(uuid));
        for (Player p : Bukkit.getOnlinePlayers()) players.put(p.getUniqueId(), p.getName());
        List<Map.Entry<UUID, String>> list = new ArrayList<>(players.entrySet());

        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.min(page, pages - 1);

        for (int i = 0; i < 45; i++) {
            int index = page * 45 + i;
            if (index >= list.size()) break;
            UUID uuid = list.get(index).getKey();
            String name = list.get(index).getValue();
            boolean has = plugin.roles().hasRole(uuid, roleName);
            boolean online = Bukkit.getPlayer(uuid) != null;
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);

            set(i, Items.head(op, Text.item((has ? "<green>" : "<gray>") + "<name>", Placeholder.unparsed("name", name)), List.of(
                    Text.item(has ? "<green>Heeft deze rol" : "<gray>Heeft deze rol niet"),
                    Text.item(online ? "<dark_gray>Online" : "<dark_gray>Offline"),
                    Component.empty(),
                    Text.item(has ? "<yellow>Klik om rol af te pakken" : "<yellow>Klik om rol te geven"))), e -> {
                if (has) plugin.roles().removeRole(uuid, roleName);
                else plugin.roles().addRole(uuid, name, roleName);
                plugin.commit();
                redraw();
            });
        }

        set(45, Items.of(Material.ARROW, Text.item("<gray>Terug")),
                e -> go((Player) e.getWhoClicked(), new RoleMenu(plugin, roleName)));
        if (page > 0) set(48, Items.of(Material.PAPER, Text.item("<yellow>« Vorige")), e -> { page--; redraw(); });
        set(49, Items.of(Material.MAP, Text.item("<gray>Pagina " + (page + 1) + " / " + pages), List.of(
                Text.item("<gray>Offline spelers toevoegen:"),
                Text.item("<white>/rol speler \\<naam> geef " + roleName))), null);
        if (page < pages - 1) set(50, Items.of(Material.PAPER, Text.item("<yellow>Volgende »")), e -> { page++; redraw(); });
        fillRow(5);
    }
}
