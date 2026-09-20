package nl.robin.rolbeheer.role;

import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class RoleManager {

    private static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    public static final Comparator<Role> BY_PRIORITY =
            Comparator.comparingInt(Role::getPriority).reversed().thenComparing(Role::getName);

    private final RolBeheer plugin;
    private final File rolesFile;
    private final File playersFile;

    private final Map<String, Role> roles = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerRoles = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

    public RoleManager(RolBeheer plugin) {
        this.plugin = plugin;
        this.rolesFile = new File(plugin.getDataFolder(), "roles.yml");
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public String defaultRoleName() {
        return plugin.getConfig().getString("standaard-rol", "speler").toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- laden / opslaan

    public void load() {
        roles.clear();
        playerRoles.clear();
        playerNames.clear();

        boolean dirty = false;
        YamlConfiguration rc = YamlConfiguration.loadConfiguration(rolesFile);
        ConfigurationSection section = rc.getConfigurationSection("rollen");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                String name = key.toLowerCase(Locale.ROOT);
                if (s == null || !isValidName(name)) {
                    plugin.getLogger().warning("Ongeldige rol overgeslagen: " + key);
                    continue;
                }
                Role role = new Role(name);
                role.setPrefix(s.getString("prefix", ""));
                role.setSuffix(s.getString("suffix", ""));
                role.setPriority(s.getInt("prioriteit", 10));
                role.setNameColorName(s.getString("naamkleur", ""));
                for (String perm : s.getStringList("permissies")) {
                    String p = perm.trim().toLowerCase(Locale.ROOT);
                    if (!p.isEmpty() && !role.getPermissions().contains(p)) role.getPermissions().add(p);
                }
                roles.put(name, role);
            }
        }

        if (roles.isEmpty()) {
            createExampleRoles();
            dirty = true;
        }
        if (!roles.containsKey(defaultRoleName())) {
            Role def = new Role(defaultRoleName());
            def.setPriority(0);
            roles.put(def.getName(), def);
            dirty = true;
        }

        YamlConfiguration pc = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection players = pc.getConfigurationSection("spelers");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection s = players.getConfigurationSection(key);
                if (s == null) continue;
                String name = s.getString("naam");
                if (name != null) playerNames.put(uuid, name);
                Set<String> set = ConcurrentHashMap.newKeySet();
                for (String r : s.getStringList("rollen")) set.add(r.toLowerCase(Locale.ROOT));
                if (!set.isEmpty()) playerRoles.put(uuid, set);
            }
        }

        if (dirty) save();
    }

    private void createExampleRoles() {
        Role speler = new Role(defaultRoleName());
        speler.setPrefix("<gray>[Speler]");
        speler.setPriority(0);
        roles.put(speler.getName(), speler);

        Role mod = new Role("moderator");
        mod.setPrefix("<aqua>[Mod]");
        mod.setPriority(50);
        mod.setNameColorName("aqua");
        roles.put(mod.getName(), mod);

        Role admin = new Role("admin");
        admin.setPrefix("<red>[Admin]");
        admin.setPriority(100);
        admin.setNameColorName("red");
        admin.getPermissions().add("*");
        roles.put(admin.getName(), admin);
    }

    public void save() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        YamlConfiguration rc = new YamlConfiguration();
        rc.options().setHeader(List.of(
                "Rollen van RolBeheer. Na handmatig bewerken: /rol herlaad",
                "Permissies met een - ervoor zijn verboden. * = alles, plugin.* = alles van die plugin."));
        for (Role r : getRoles()) {
            String p = "rollen." + r.getName() + ".";
            rc.set(p + "prefix", r.getPrefix());
            rc.set(p + "suffix", r.getSuffix());
            rc.set(p + "prioriteit", r.getPriority());
            rc.set(p + "naamkleur", r.getNameColorName());
            rc.set(p + "permissies", new ArrayList<>(r.getPermissions()));
        }

        YamlConfiguration pc = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : playerRoles.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            String p = "spelers." + e.getKey() + ".";
            pc.set(p + "naam", playerNames.getOrDefault(e.getKey(), "?"));
            pc.set(p + "rollen", new ArrayList<>(e.getValue()));
        }

        try {
            rc.save(rolesFile);
            pc.save(playersFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Kon rollen niet opslaan: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- rollen

    public Role getRole(String name) {
        return name == null ? null : roles.get(name.toLowerCase(Locale.ROOT));
    }

    /** Alle rollen, hoogste prioriteit eerst. */
    public List<Role> getRoles() {
        List<Role> list = new ArrayList<>(roles.values());
        list.sort(BY_PRIORITY);
        return list;
    }

    public Role createRole(String name) {
        Role role = new Role(name.toLowerCase(Locale.ROOT));
        roles.put(role.getName(), role);
        return role;
    }

    public boolean deleteRole(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals(defaultRoleName()) || roles.remove(n) == null) return false;
        playerRoles.values().forEach(set -> set.remove(n));
        playerRoles.values().removeIf(Set::isEmpty);
        return true;
    }

    // ---------------------------------------------------------------- spelers

    public Set<String> getAssignedRoles(UUID uuid) {
        return Set.copyOf(playerRoles.getOrDefault(uuid, Set.of()));
    }

    /** Alle rollen van een speler (incl. standaardrol), hoogste prioriteit eerst. */
    public List<Role> getEffectiveRoles(UUID uuid) {
        List<Role> list = new ArrayList<>();
        Role def = roles.get(defaultRoleName());
        if (def != null) list.add(def);
        for (String n : playerRoles.getOrDefault(uuid, Set.of())) {
            Role r = roles.get(n);
            if (r != null && !list.contains(r)) list.add(r);
        }
        list.sort(BY_PRIORITY);
        return list;
    }

    public boolean hasRole(UUID uuid, String role) {
        String n = role.toLowerCase(Locale.ROOT);
        return n.equals(defaultRoleName()) || playerRoles.getOrDefault(uuid, Set.of()).contains(n);
    }

    public boolean addRole(UUID uuid, String playerName, String role) {
        if (playerName != null) playerNames.put(uuid, playerName);
        return playerRoles.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(role.toLowerCase(Locale.ROOT));
    }

    public boolean removeRole(UUID uuid, String role) {
        Set<String> set = playerRoles.get(uuid);
        if (set == null) return false;
        boolean removed = set.remove(role.toLowerCase(Locale.ROOT));
        if (set.isEmpty()) playerRoles.remove(uuid);
        return removed;
    }

    public List<UUID> getMembers(String role) {
        String n = role.toLowerCase(Locale.ROOT);
        List<UUID> list = new ArrayList<>();
        playerRoles.forEach((uuid, set) -> {
            if (set.contains(n)) list.add(uuid);
        });
        return list;
    }

    public String getPlayerName(UUID uuid) {
        return playerNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public void updateName(UUID uuid, String name) {
        if (playerRoles.containsKey(uuid) && !name.equals(playerNames.get(uuid))) {
            playerNames.put(uuid, name);
            save();
        }
    }
}
