package nl.robin.rolbeheer.spelers;

import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

/** Neemt homes (en het spawnpunt) over uit EssentialsX. */
public final class EssentialsImport {

    public record Result(int players, int homes, int skipped, boolean spawn, String problem) {}

    private final RolBeheer plugin;
    private final Store store;

    public EssentialsImport(RolBeheer plugin, Store store) {
        this.plugin = plugin;
        this.store = store;
    }

    private File userdata() {
        return new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata");
    }

    public boolean available() {
        File folder = userdata();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        return files != null && files.length > 0;
    }

    public Result run(boolean overwrite) {
        File folder = userdata();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return new Result(0, 0, 0, false, "Geen Essentials-gegevens gevonden in plugins/Essentials/userdata.");
        }

        int players = 0;
        int homes = 0;
        int skipped = 0;
        for (File file : files) {
            UUID uuid;
            try {
                uuid = UUID.fromString(file.getName().replace(".yml", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("homes");
            if (section == null) continue;

            boolean imported = false;
            for (String name : section.getKeys(false)) {
                ConfigurationSection home = section.getConfigurationSection(name);
                if (home == null) continue;
                String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
                if (key.length() > 24) key = key.substring(0, 24);
                if (!overwrite && store.home(uuid, key) != null) {
                    skipped++;
                    continue;
                }
                Location location = read(home);
                if (location == null) {
                    skipped++;
                    continue;
                }
                store.setHome(uuid, key, location);
                homes++;
                imported = true;
            }
            if (imported) players++;
        }

        boolean spawn = importSpawn(overwrite);
        return new Result(players, homes, skipped, spawn, null);
    }

    /** Essentials bewaart de wereld als UUID, met de naam als reserve. */
    private Location read(ConfigurationSection home) {
        World world = null;
        String worldId = home.getString("world");
        if (worldId != null) {
            try {
                world = Bukkit.getWorld(UUID.fromString(worldId));
            } catch (IllegalArgumentException e) {
                world = Bukkit.getWorld(worldId);
            }
        }
        if (world == null) world = Bukkit.getWorld(home.getString("world-name", ""));
        if (world == null) return null;
        return new Location(world, home.getDouble("x"), home.getDouble("y"), home.getDouble("z"),
                (float) home.getDouble("yaw"), (float) home.getDouble("pitch"));
    }

    private boolean importSpawn(boolean overwrite) {
        if (!overwrite && store.spawn() != null) return false;
        File file = new File(plugin.getDataFolder().getParentFile(), "Essentials/spawn.yml");
        if (!file.isFile()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection spawns = config.getConfigurationSection("spawns");
        if (spawns == null) return false;
        String key = spawns.contains("default") ? "default" : spawns.getKeys(false).stream().findFirst().orElse(null);
        if (key == null) return false;
        ConfigurationSection section = spawns.getConfigurationSection(key);
        Location location = section == null ? null : read(section);
        if (location == null) return false;
        store.setSpawn(location);
        return true;
    }
}
