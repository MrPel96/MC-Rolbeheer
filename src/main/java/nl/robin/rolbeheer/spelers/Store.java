package nl.robin.rolbeheer.spelers;

import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Opslag voor homes, warps, kits en het spawnpunt. */
public final class Store {

    public static final Pattern VALID_NAME = Pattern.compile("[a-z0-9_-]{1,24}");

    private final RolBeheer plugin;
    private final File homesFile;
    private final File warpsFile;
    private final File kitsFile;
    private final File dataFile;

    private YamlConfiguration homes;
    private YamlConfiguration warps;
    private YamlConfiguration kits;
    private YamlConfiguration data;

    public Store(RolBeheer plugin) {
        this.plugin = plugin;
        this.homesFile = new File(plugin.getDataFolder(), "homes.yml");
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        this.kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    public void load() {
        homes = YamlConfiguration.loadConfiguration(homesFile);
        warps = YamlConfiguration.loadConfiguration(warpsFile);
        kits = YamlConfiguration.loadConfiguration(kitsFile);
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save(YamlConfiguration config, File file) {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Kon " + file.getName() + " niet opslaan: " + e.getMessage());
        }
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    // ---------------------------------------------------------------- homes

    public Map<String, Location> homes(UUID uuid) {
        Map<String, Location> result = new LinkedHashMap<>();
        ConfigurationSection section = homes.getConfigurationSection(uuid.toString());
        if (section == null) return result;
        for (String name : section.getKeys(false)) {
            Location location = section.getLocation(name);
            if (location != null) result.put(name, location);
        }
        return result;
    }

    public Location home(UUID uuid, String name) {
        return homes.getLocation(uuid + "." + name.toLowerCase(Locale.ROOT));
    }

    public void setHome(UUID uuid, String name, Location location) {
        homes.set(uuid + "." + name.toLowerCase(Locale.ROOT), location);
        save(homes, homesFile);
    }

    public boolean deleteHome(UUID uuid, String name) {
        String path = uuid + "." + name.toLowerCase(Locale.ROOT);
        if (homes.getLocation(path) == null) return false;
        homes.set(path, null);
        save(homes, homesFile);
        return true;
    }

    // ---------------------------------------------------------------- warps

    public List<String> warpNames() {
        ConfigurationSection section = warps.getConfigurationSection("warps");
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
    }

    public Location warp(String name) {
        return warps.getLocation("warps." + name.toLowerCase(Locale.ROOT));
    }

    public void setWarp(String name, Location location) {
        warps.set("warps." + name.toLowerCase(Locale.ROOT), location);
        save(warps, warpsFile);
    }

    public boolean deleteWarp(String name) {
        String path = "warps." + name.toLowerCase(Locale.ROOT);
        if (warps.getLocation(path) == null) return false;
        warps.set(path, null);
        save(warps, warpsFile);
        return true;
    }

    // ---------------------------------------------------------------- spawn

    public Location spawn() {
        return data.getLocation("spawn");
    }

    public void setSpawn(Location location) {
        data.set("spawn", location);
        save(data, dataFile);
    }

    // ---------------------------------------------------------------- kits

    public List<String> kitNames() {
        ConfigurationSection section = kits.getConfigurationSection("kits");
        return section == null ? new ArrayList<>() : new ArrayList<>(section.getKeys(false));
    }

    public boolean hasKit(String name) {
        return kits.isConfigurationSection("kits." + name.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    public List<ItemStack> kitItems(String name) {
        List<?> raw = kits.getList("kits." + name.toLowerCase(Locale.ROOT) + ".items", new ArrayList<>());
        List<ItemStack> items = new ArrayList<>();
        for (Object o : raw) if (o instanceof ItemStack item) items.add(item.clone());
        return items;
    }

    public int kitCooldown(String name) {
        return kits.getInt("kits." + name.toLowerCase(Locale.ROOT) + ".cooldown", 0);
    }

    public void setKit(String name, List<ItemStack> items, int cooldownSeconds) {
        String path = "kits." + name.toLowerCase(Locale.ROOT);
        kits.set(path + ".items", items);
        kits.set(path + ".cooldown", Math.max(0, cooldownSeconds));
        save(kits, kitsFile);
    }

    public void setKitCooldown(String name, int cooldownSeconds) {
        String path = "kits." + name.toLowerCase(Locale.ROOT);
        if (!kits.isConfigurationSection(path)) return;
        kits.set(path + ".cooldown", Math.max(0, cooldownSeconds));
        save(kits, kitsFile);
    }

    public boolean deleteKit(String name) {
        String path = "kits." + name.toLowerCase(Locale.ROOT);
        if (!kits.isConfigurationSection(path)) return false;
        kits.set(path, null);
        save(kits, kitsFile);
        return true;
    }

    /** Tijdstip (millis) waarop deze speler de kit voor het laatst pakte. */
    public long lastKitUse(UUID uuid, String kit) {
        return data.getLong("kitgebruik." + uuid + "." + kit.toLowerCase(Locale.ROOT), 0L);
    }

    public void setLastKitUse(UUID uuid, String kit) {
        data.set("kitgebruik." + uuid + "." + kit.toLowerCase(Locale.ROOT), System.currentTimeMillis());
        save(data, dataFile);
    }
}
