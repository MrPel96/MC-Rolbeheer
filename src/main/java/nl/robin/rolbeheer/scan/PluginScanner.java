package nl.robin.rolbeheer.scan;

import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zoekt automatisch uit welke plugins er op de server staan en welke permissies
 * en commands ze hebben. Commands zonder eigen permissie krijgen een "virtuele"
 * permissie (rolbeheer.command.plugin.label) zodat je ze toch kunt blokkeren.
 */
public final class PluginScanner {

    public static final String SERVER_GROUP = "Minecraft / Server";
    public static final String VIRTUAL_PREFIX = "rolbeheer.command.";
    public static final String INTERNAL_PREFIX = "rolbeheer.internal.";

    public record PermEntry(String node, String description, List<String> commands, boolean virtual) {
        public boolean isCommand() { return !commands.isEmpty(); }
    }

    public record PluginGroup(String name, boolean server, boolean enabled, List<PermEntry> entries) {
        public long commandCount() { return entries.stream().filter(PermEntry::isCommand).count(); }
    }

    private final RolBeheer plugin;
    private volatile List<PluginGroup> groups = List.of();
    private volatile Set<String> allNodes = Set.of();
    private volatile Map<String, String> virtualLabels = Map.of();
    private boolean rescanScheduled;

    public PluginScanner(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public List<PluginGroup> groups() { return groups; }

    public PluginGroup group(String name) {
        for (PluginGroup g : groups) if (g.name().equalsIgnoreCase(name)) return g;
        return null;
    }

    /** Alle bekende permissies (voor * en plugin.* uitbreiding). */
    public Set<String> allNodes() { return allNodes; }

    /** Virtuele permissie voor een command-label, of null. */
    public String virtualNode(String label) {
        return virtualLabels.get(label.toLowerCase(Locale.ROOT));
    }

    /** Opnieuw scannen na een korte vertraging (bij het laden/ontladen van plugins). */
    public void scheduleRescan() {
        if (rescanScheduled || !plugin.isEnabled()) return;
        rescanScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rescanScheduled = false;
            rescan();
            plugin.sync().applyAll();
        }, 20L);
    }

    public void rescan() {
        PluginManager pm = Bukkit.getPluginManager();
        Map<String, Builder> builders = new LinkedHashMap<>();
        Map<String, String> lowerToName = new HashMap<>();
        Builder server = new Builder(SERVER_GROUP, true, true);

        // 1. Permissies die plugins zelf opgeven in plugin.yml / paper-plugin.yml
        for (Plugin p : pm.getPlugins()) {
            lowerToName.put(p.getName().toLowerCase(Locale.ROOT), p.getName());
            Builder b = builders.computeIfAbsent(p.getName(), n -> new Builder(n, false, p.isEnabled()));
            try {
                for (Permission perm : p.getPluginMeta().getPermissions()) {
                    b.add(perm.getName(), perm.getDescription(), null, false);
                }
            } catch (Throwable ignored) {
                // Sommige plugins hebben afwijkende metadata; die slaan we over.
            }
        }

        // 2. Alle geregistreerde commands (inclusief aliassen)
        Map<Command, List<String>> labelsByCommand = new IdentityHashMap<>();
        for (Map.Entry<String, Command> e : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
            if (e.getValue() == null) continue;
            labelsByCommand.computeIfAbsent(e.getValue(), c -> new ArrayList<>())
                    .add(e.getKey().toLowerCase(Locale.ROOT));
        }

        Map<String, String> virtual = new HashMap<>();
        for (Map.Entry<Command, List<String>> e : labelsByCommand.entrySet()) {
            Command cmd = e.getKey();
            Builder owner = ownerOf(cmd, e.getValue(), builders, lowerToName, server);
            String label = cmd.getName().toLowerCase(Locale.ROOT);
            String perm = cmd.getPermission();

            if (perm == null || perm.isBlank()) {
                String node = VIRTUAL_PREFIX + sanitize(owner.server ? "minecraft" : owner.name) + "." + sanitize(label);
                owner.add(node, "Command zonder eigen permissie.", "/" + label, true);
                for (String l : e.getValue()) virtual.put(l, node);
            } else {
                for (String node : perm.split(";")) {
                    if (!node.isBlank()) owner.add(node.trim(), null, "/" + label, false);
                }
            }
        }

        // 3. Overige geregistreerde permissies (door plugins in code aangemaakt)
        Set<String> seen = new HashSet<>();
        builders.values().forEach(b -> seen.addAll(b.entries.keySet()));
        seen.addAll(server.entries.keySet());
        for (Permission perm : new ArrayList<>(pm.getPermissions())) {
            String n = perm.getName().toLowerCase(Locale.ROOT);
            if (n.startsWith(INTERNAL_PREFIX) || n.startsWith(VIRTUAL_PREFIX) || seen.contains(n)) continue;
            int dot = n.indexOf('.');
            String first = dot > 0 ? n.substring(0, dot) : n;
            Builder b;
            if (first.equals("minecraft") || first.equals("bukkit") || first.equals("paper") || first.equals("spigot")) {
                b = server;
            } else {
                String name = lowerToName.get(first);
                if (name == null) continue;
                b = builders.get(name);
            }
            b.add(n, perm.getDescription(), null, false);
        }

        // 4. Virtuele permissies registreren (standaard: iedereen mag het command)
        Set<String> virtualNodes = new HashSet<>(virtual.values());
        for (String node : virtualNodes) {
            if (pm.getPermission(node) == null) {
                pm.addPermission(new Permission(node, "RolBeheer: toegang tot command", PermissionDefault.TRUE));
            }
        }
        for (Permission p : new ArrayList<>(pm.getPermissions())) {
            String n = p.getName().toLowerCase(Locale.ROOT);
            if (n.startsWith(VIRTUAL_PREFIX) && !virtualNodes.contains(n)) pm.removePermission(p);
        }

        // 5. Resultaat opbouwen
        List<PluginGroup> result = new ArrayList<>();
        if (!server.entries.isEmpty()) result.add(server.build());
        builders.values().stream()
                .filter(b -> !b.entries.isEmpty())
                .sorted(Comparator.comparing(b -> b.name.toLowerCase(Locale.ROOT)))
                .map(Builder::build)
                .forEach(result::add);

        Set<String> nodes = new HashSet<>();
        for (PluginGroup g : result) for (PermEntry pe : g.entries()) nodes.add(pe.node());
        for (Permission p : pm.getPermissions()) {
            String n = p.getName().toLowerCase(Locale.ROOT);
            if (!n.startsWith(INTERNAL_PREFIX)) nodes.add(n);
        }

        groups = List.copyOf(result);
        allNodes = Set.copyOf(nodes);
        virtualLabels = Map.copyOf(virtual);
    }

    private static Builder ownerOf(Command cmd, List<String> keys, Map<String, Builder> builders,
                                   Map<String, String> lowerToName, Builder server) {
        if (cmd instanceof PluginIdentifiableCommand pic) {
            Plugin p = pic.getPlugin();
            return builders.computeIfAbsent(p.getName(), n -> new Builder(n, false, p.isEnabled()));
        }
        // Namespaced label (bijv. "essentials:home") verraadt de eigenaar
        for (String key : keys) {
            int i = key.indexOf(':');
            if (i > 0) {
                String name = lowerToName.get(key.substring(0, i));
                if (name != null) return builders.get(name);
            }
        }
        return server;
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private static final class Builder {
        final String name;
        final boolean server;
        final boolean enabled;
        final Map<String, EntryBuilder> entries = new LinkedHashMap<>();

        Builder(String name, boolean server, boolean enabled) {
            this.name = name;
            this.server = server;
            this.enabled = enabled;
        }

        void add(String node, String description, String command, boolean virtual) {
            String key = node.toLowerCase(Locale.ROOT);
            EntryBuilder e = entries.computeIfAbsent(key, k -> new EntryBuilder(k, virtual));
            if ((e.description == null || e.description.isBlank()) && description != null && !description.isBlank()) {
                e.description = description;
            }
            if (command != null) e.commands.add(command);
        }

        PluginGroup build() {
            List<PermEntry> list = entries.values().stream()
                    .sorted(Comparator.comparing(e -> e.node))
                    .map(e -> new PermEntry(e.node, e.description == null ? "" : e.description,
                            List.copyOf(e.commands), e.virtual))
                    .toList();
            return new PluginGroup(name, server, enabled, list);
        }
    }

    private static final class EntryBuilder {
        final String node;
        final boolean virtual;
        final Set<String> commands = new LinkedHashSet<>();
        String description;

        EntryBuilder(String node, boolean virtual) {
            this.node = node;
            this.virtual = virtual;
        }
    }
}
