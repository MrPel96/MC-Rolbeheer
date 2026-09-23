package nl.robin.rolbeheer.spelers;

import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Houdt bij hoe vaak spelers doodgaan en hoeveel spelers ze verslaan. */
public final class Stats {

    public record Entry(UUID uuid, String name, int deaths, int kills) {}

    private final RolBeheer plugin;
    private final File file;
    private YamlConfiguration data;
    private boolean dirty;

    public Stats(RolBeheer plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        // Elke minuut opslaan, zodat we niet bij elke dood naar de schijf schrijven.
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveIfNeeded, 1200L, 1200L);
    }

    public int deaths(UUID uuid) {
        return data.getInt(uuid + ".doden", 0);
    }

    public int kills(UUID uuid) {
        return data.getInt(uuid + ".kills", 0);
    }

    public void addDeath(UUID uuid, String name) {
        data.set(uuid + ".naam", name);
        data.set(uuid + ".doden", deaths(uuid) + 1);
        dirty = true;
    }

    public void addKill(UUID uuid, String name) {
        data.set(uuid + ".naam", name);
        data.set(uuid + ".kills", kills(uuid) + 1);
        dirty = true;
    }

    public void reset(UUID uuid) {
        data.set(uuid.toString(), null);
        dirty = true;
        saveIfNeeded();
    }

    public void resetAll() {
        data = new YamlConfiguration();
        dirty = true;
        saveIfNeeded();
    }

    /** Ranglijst, hoogste eerst. */
    public List<Entry> top(boolean byKills, int limit) {
        List<Entry> entries = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            ConfigurationSection section = data.getConfigurationSection(key);
            if (section == null) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            entries.add(new Entry(uuid, section.getString("naam", "?"),
                    section.getInt("doden", 0), section.getInt("kills", 0)));
        }
        entries.sort(Comparator.comparingInt(byKills ? Entry::kills : Entry::deaths).reversed()
                .thenComparing(Entry::name));
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    public void saveIfNeeded() {
        if (!dirty) return;
        dirty = false;
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Kon stats.yml niet opslaan: " + e.getMessage());
        }
    }
}
