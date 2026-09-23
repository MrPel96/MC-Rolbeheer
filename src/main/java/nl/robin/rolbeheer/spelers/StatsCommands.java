package nl.robin.rolbeheer.spelers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /stats en /top voor de dodenteller. */
public final class StatsCommands implements TabExecutor {

    private final RolBeheer plugin;
    private final Stats stats;

    public StatsCommands(RolBeheer plugin, Stats stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("top")) {
            boolean kills = args.length > 0 && args[0].toLowerCase(Locale.ROOT).startsWith("k");
            int limit = plugin.getConfig().getInt("scorebord.aantal", 5);
            List<Stats.Entry> top = stats.top(kills, Math.max(3, limit));
            if (top.isEmpty()) {
                Text.send(sender, "Er is nog niets bijgehouden.");
                return true;
            }
            Text.send(sender, kills ? "<gold>Meeste kills" : "<gold>Meeste doden");
            int place = 1;
            for (Stats.Entry entry : top) {
                sender.sendMessage(Component.text("  " + place++ + ". ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(entry.name(), NamedTextColor.WHITE))
                        .append(Component.text("  " + (kills ? entry.kills() : entry.deaths()), NamedTextColor.GOLD)));
            }
            return true;
        }

        // /stats [speler]
        OfflinePlayer target;
        if (args.length > 0) {
            Player online = Bukkit.getPlayerExact(args[0]);
            target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                Text.send(sender, "<red>Die speler is nog nooit op de server geweest.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            Text.send(sender, "<red>Gebruik: <white>/stats <speler>");
            return true;
        }

        Text.send(sender, "<white><n></white>: <red><d></red> keer doodgegaan, <green><k></green> spelers verslagen.",
                Placeholder.unparsed("n", target.getName() == null ? args[0] : target.getName()),
                Placeholder.unparsed("d", String.valueOf(stats.deaths(target.getUniqueId()))),
                Placeholder.unparsed("k", String.valueOf(stats.kills(target.getUniqueId()))));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("top")) {
            options.addAll(List.of("doden", "kills"));
        } else {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(start)).toList();
    }
}
