package nl.robin.rolbeheer.sync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.role.Role;
import nl.robin.rolbeheer.scan.PluginScanner;
import nl.robin.rolbeheer.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Past rollen toe op spelers: permissies, prefix/suffix in tab, chat en boven het hoofd.
 */
public final class PlayerSync {

    public record Display(Component prefix, Component suffix, NamedTextColor color) {}

    private static final String TEAM_PREFIX = "rolb";

    private final RolBeheer plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Display> displays = new ConcurrentHashMap<>();

    public PlayerSync(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public Display display(UUID uuid) {
        return displays.get(uuid);
    }

    public void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        cleanupTeams();
    }

    public void apply(Player player) {
        List<Role> roles = plugin.roles().getEffectiveRoles(player.getUniqueId());
        applyPermissions(player, roles);
        applyDisplay(player, roles.isEmpty() ? null : roles.get(0));
        player.updateCommands();
    }

    // ---------------------------------------------------------------- permissies

    /**
     * Alle permissies van alle rollen worden samengevoegd. Rollen met een hogere prioriteit
     * winnen bij conflicten. Binnen een rol winnen specifieke permissies van wildcards.
     * Het resultaat komt in één interne "ouder"-permissie per speler.
     */
    private void applyPermissions(Player player, List<Role> rolesHighFirst) {
        Set<String> known = plugin.scanner().allNodes();
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();

        for (int i = rolesHighFirst.size() - 1; i >= 0; i--) {
            List<String> perms = new ArrayList<>(rolesHighFirst.get(i).getPermissions());
            perms.sort(Comparator.comparingInt(PlayerSync::specificity));
            for (String raw : perms) {
                boolean value = !raw.startsWith("-");
                String node = (value ? raw : raw.substring(1)).trim().toLowerCase(Locale.ROOT);
                if (node.isEmpty()) continue;

                if (node.equals("*")) {
                    for (String n : known) put(result, n, value);
                } else if (node.endsWith(".*")) {
                    put(result, node, value);
                    String start = node.substring(0, node.length() - 1);
                    for (String n : known) if (n.startsWith(start)) put(result, n, value);
                } else {
                    put(result, node, value);
                }
            }
        }

        PluginManager pm = Bukkit.getPluginManager();
        String internal = PluginScanner.INTERNAL_PREFIX + player.getUniqueId();
        Permission perm = pm.getPermission(internal);
        if (perm == null) {
            perm = new Permission(internal, PermissionDefault.FALSE, result);
            pm.addPermission(perm);
        } else {
            perm.getChildren().clear();
            perm.getChildren().putAll(result);
        }

        PermissionAttachment attachment = attachments.get(player.getUniqueId());
        if (attachment == null) {
            attachment = player.addAttachment(plugin);
            attachment.setPermission(internal, true);
            attachments.put(player.getUniqueId(), attachment);
        }
        player.recalculatePermissions();
    }

    private static int specificity(String raw) {
        String n = raw.startsWith("-") ? raw.substring(1) : raw;
        if (n.equals("*")) return 0;
        if (n.endsWith(".*")) return 1;
        return 2;
    }

    private static void put(Map<String, Boolean> map, String node, boolean value) {
        map.remove(node); // opnieuw toevoegen zodat de volgorde (= voorrang) klopt
        map.put(node, value);
    }

    // ---------------------------------------------------------------- weergave

    private void applyDisplay(Player player, Role primary) {
        Component prefix = Component.empty();
        Component suffix = Component.empty();
        NamedTextColor color = null;
        if (primary != null) {
            if (!primary.getPrefix().isBlank()) {
                prefix = Component.textOfChildren(Text.parse(primary.getPrefix()), Component.space());
            }
            if (!primary.getSuffix().isBlank()) {
                suffix = Component.textOfChildren(Component.space(), Text.parse(primary.getSuffix()));
            }
            color = primary.getNameColor();
        }
        displays.put(player.getUniqueId(), new Display(prefix, suffix, color));

        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("tablijst.ingeschakeld", true)) {
            Component name = color == null ? Component.text(player.getName()) : Component.text(player.getName(), color);
            player.playerListName(Component.textOfChildren(prefix, name, suffix));
        } else {
            player.playerListName(null);
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = player.getName();
        boolean nametags = config.getBoolean("naamlabel.ingeschakeld", true);
        String target = primary != null && nametags ? teamName(primary) : null;

        for (Team t : board.getTeams()) {
            if (t.getName().startsWith(TEAM_PREFIX) && !t.getName().equals(target) && t.hasEntry(entry)) {
                t.removeEntry(entry);
            }
        }
        if (target == null) return;

        Team team = board.getTeam(target);
        if (team == null) team = board.registerNewTeam(target);
        team.prefix(prefix);
        team.suffix(suffix);
        team.color(color);
        if (!team.hasEntry(entry)) team.addEntry(entry);
    }

    /** Teamnaam bepaalt ook de volgorde in de tablijst: hoogste prioriteit bovenaan. */
    private static String teamName(Role role) {
        String name = role.getName().length() > 9 ? role.getName().substring(0, 9) : role.getName();
        return TEAM_PREFIX + String.format("%03d", 999 - role.getPriority()) + name;
    }

    private void cleanupTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(board.getTeams())) {
            if (t.getName().startsWith(TEAM_PREFIX) && t.getEntries().isEmpty()) t.unregister();
        }
    }

    // ---------------------------------------------------------------- opruimen

    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // al verwijderd
            }
        }
        Bukkit.getPluginManager().removePermission(PluginScanner.INTERNAL_PREFIX + uuid);
        displays.remove(uuid);

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : board.getTeams()) {
            if (t.getName().startsWith(TEAM_PREFIX) && t.hasEntry(player.getName())) t.removeEntry(player.getName());
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            remove(p);
            p.playerListName(null);
        }
        cleanupTeams();
    }
}
