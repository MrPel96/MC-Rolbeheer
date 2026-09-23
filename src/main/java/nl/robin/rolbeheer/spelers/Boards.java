package nl.robin.rolbeheer.spelers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

/** De tekst boven en onder de tablijst, en het scorebord met de ranglijst. */
public final class Boards {

    private static final String OBJECTIVE = "rolbeheer_top";

    private final RolBeheer plugin;
    private final Stats stats;
    private BukkitTask task;

    public Boards(RolBeheer plugin, Stats stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    public void start() {
        stop();
        // Elke 10 seconden bijwerken: spelersaantallen en de ranglijst veranderen vanzelf.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 200L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) updateTab(player);
        updateSidebar();
    }

    // ---------------------------------------------------------------- tablijst

    public void updateTab(Player player) {
        if (!plugin.getConfig().getBoolean("tablijst.ingeschakeld", true)) return;
        List<String> header = plugin.getConfig().getStringList("tablijst.koptekst");
        List<String> footer = plugin.getConfig().getStringList("tablijst.voettekst");
        if (header.isEmpty() && footer.isEmpty()) return;
        player.sendPlayerListHeaderAndFooter(lines(header, player), lines(footer, player));
    }

    private Component lines(List<String> raw, Player player) {
        Component result = Component.empty();
        for (int i = 0; i < raw.size(); i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(render(raw.get(i), player));
        }
        return result;
    }

    /** Vult <naam>, <prefix>, <rol>, <spelers>, <max>, <doden> en <kills> in. */
    public Component render(String template, Player player) {
        List<Role> roles = player == null ? List.of() : plugin.roles().getEffectiveRoles(player.getUniqueId());
        Role role = roles.isEmpty() ? null : roles.get(0);
        TagResolver[] resolvers = {
                Placeholder.unparsed("naam", player == null ? "" : player.getName()),
                Placeholder.component("prefix", role == null ? Component.empty() : Text.parse(role.getPrefix())),
                Placeholder.component("suffix", role == null ? Component.empty() : Text.parse(role.getSuffix())),
                Placeholder.unparsed("rol", role == null ? "" : role.getName()),
                Placeholder.unparsed("spelers", String.valueOf(Bukkit.getOnlinePlayers().size())),
                Placeholder.unparsed("max", String.valueOf(Bukkit.getMaxPlayers())),
                Placeholder.unparsed("doden", player == null ? "0" : String.valueOf(stats.deaths(player.getUniqueId()))),
                Placeholder.unparsed("kills", player == null ? "0" : String.valueOf(stats.kills(player.getUniqueId())))
        };
        return Text.mm(Text.codesToTags(template), resolvers);
    }

    // ---------------------------------------------------------------- scorebord

    public void updateSidebar() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = board.getObjective(OBJECTIVE);

        if (!plugin.getConfig().getBoolean("scorebord.ingeschakeld", false)) {
            if (objective != null) objective.unregister();
            return;
        }

        boolean kills = plugin.getConfig().getString("scorebord.soort", "doden").equalsIgnoreCase("kills");
        Component title = render(plugin.getConfig().getString("scorebord.titel",
                kills ? "<gold><bold>Top kills" : "<gold><bold>Top doden"), null);

        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
        } else {
            objective.displayName(title);
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Oude regels weghalen, anders blijven vertrokken spelers staan.
        for (String entry : board.getEntries()) {
            if (objective.getScore(entry).isScoreSet()) board.resetScores(entry);
        }
        int limit = Math.max(1, Math.min(10, plugin.getConfig().getInt("scorebord.aantal", 5)));
        for (Stats.Entry entry : stats.top(kills, limit)) {
            int value = kills ? entry.kills() : entry.deaths();
            if (value <= 0) continue;
            objective.getScore(entry.name()).setScore(value);
        }
    }
}
