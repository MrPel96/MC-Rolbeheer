package nl.robin.rolbeheer.server;

import net.kyori.adventure.text.Component;
import nl.robin.rolbeheer.RolBeheer;
import nl.robin.rolbeheer.util.Text;
import nl.robin.rolbeheer.web.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leest en schrijft server.properties en de gameregels, met Nederlandse uitleg.
 * Wat live kan worden toegepast wordt meteen doorgevoerd; de rest vraagt om een herstart.
 */
public final class ServerSettings {

    public enum Type { TEXT, NUMBER, BOOL, CHOICE }

    /** Eén instelbare regel uit server.properties. */
    public record Setting(String key, String label, String description, Type type,
                          List<String> options, int min, int max, boolean restart) {}

    private static final List<Setting> SETTINGS = List.of(
            new Setting("motd", "Servernaam in de serverlijst",
                    "De tekst die spelers zien in hun serverlijst. Kleuren met §c werken hier.",
                    Type.TEXT, List.of(), 0, 0, false),
            new Setting("max-players", "Maximaal aantal spelers",
                    "Hoeveel spelers tegelijk kunnen inloggen.", Type.NUMBER, List.of(), 1, 1000, false),
            new Setting("difficulty", "Moeilijkheidsgraad",
                    "Vreedzaam betekent geen vijandige mobs.", Type.CHOICE,
                    List.of("peaceful", "easy", "normal", "hard"), 0, 0, false),
            new Setting("pvp", "Spelers kunnen elkaar aanvallen",
                    "Staat dit uit, dan doen spelers elkaar geen schade.", Type.BOOL, List.of(), 0, 0, false),
            new Setting("gamemode", "Standaard spelmodus",
                    "De spelmodus waarin nieuwe spelers beginnen.", Type.CHOICE,
                    List.of("survival", "creative", "adventure", "spectator"), 0, 0, false),
            new Setting("force-gamemode", "Spelmodus afdwingen",
                    "Zet spelers bij elke login terug in de standaard spelmodus.", Type.BOOL, List.of(), 0, 0, true),
            new Setting("allow-flight", "Vliegen toestaan",
                    "Nodig voor vlieg-plugins. Staat dit uit, dan schopt de server vliegende spelers eruit.",
                    Type.BOOL, List.of(), 0, 0, true),
            new Setting("hardcore", "Hardcore",
                    "Spelers die doodgaan worden toeschouwer en kunnen niet meer meespelen.",
                    Type.BOOL, List.of(), 0, 0, true),
            new Setting("view-distance", "Kijkafstand",
                    "Hoeveel chunks spelers zien. Lager is minder zwaar voor de server.",
                    Type.NUMBER, List.of(), 2, 32, false),
            new Setting("simulation-distance", "Simulatieafstand",
                    "Tot hoe ver mobs en redstone actief blijven. Lager is minder zwaar.",
                    Type.NUMBER, List.of(), 2, 32, false),
            new Setting("spawn-protection", "Beschermd gebied rond spawn",
                    "Straal in blokken waarbinnen alleen OP's mogen bouwen. 0 zet het uit.",
                    Type.NUMBER, List.of(), 0, 1000, true),
            new Setting("enable-command-block", "Commandblokken toestaan",
                    "Nodig als je met commandblokken wilt bouwen.", Type.BOOL, List.of(), 0, 0, true),
            new Setting("player-idle-timeout", "Spelers eruit na inactiviteit",
                    "Aantal minuten voordat een stilstaande speler eruit gaat. 0 zet het uit.",
                    Type.NUMBER, List.of(), 0, 1440, false),
            new Setting("enforce-whitelist", "Witte lijst streng toepassen",
                    "Schopt spelers die niet op de witte lijst staan er meteen uit.",
                    Type.BOOL, List.of(), 0, 0, false));

    /** Gameregels die je in de praktijk aanpast, met uitleg. */
    private static final Map<String, String[]> RULE_LABELS = new LinkedHashMap<>();

    static {
        RULE_LABELS.put("keepInventory", new String[]{"Spullen behouden na de dood",
                "Spelers verliezen hun inventaris niet als ze doodgaan."});
        RULE_LABELS.put("doDaylightCycle", new String[]{"Dag- en nachtcyclus",
                "Staat dit uit, dan blijft de tijd stilstaan."});
        RULE_LABELS.put("doWeatherCycle", new String[]{"Weer verandert", "Staat dit uit, dan blijft het weer zoals het is."});
        RULE_LABELS.put("doMobSpawning", new String[]{"Mobs verschijnen vanzelf", "Geldt voor dieren én monsters."});
        RULE_LABELS.put("mobGriefing", new String[]{"Mobs mogen blokken kapotmaken",
                "Creepers, endermen en ravagers laten je bouwwerken dan met rust."});
        RULE_LABELS.put("doFireTick", new String[]{"Vuur verspreidt zich", "Uitzetten voorkomt afgebrande huizen."});
        RULE_LABELS.put("fallDamage", new String[]{"Valschade", ""});
        RULE_LABELS.put("fireDamage", new String[]{"Vuurschade", ""});
        RULE_LABELS.put("drowningDamage", new String[]{"Verdrinkingsschade", ""});
        RULE_LABELS.put("naturalRegeneration", new String[]{"Vanzelf hartjes bijkrijgen", ""});
        RULE_LABELS.put("showDeathMessages", new String[]{"Doodsberichten in de chat", ""});
        RULE_LABELS.put("announceAdvancements", new String[]{"Prestaties in de chat melden", ""});
        RULE_LABELS.put("doImmediateRespawn", new String[]{"Meteen weer verschijnen",
                "Slaat het scherm 'Je bent gestorven' over."});
        RULE_LABELS.put("doInsomnia", new String[]{"Phantoms bij lang niet slapen", ""});
        RULE_LABELS.put("doPatrolSpawning", new String[]{"Patrouilles van plunderaars", ""});
        RULE_LABELS.put("doTraderSpawning", new String[]{"Zwervende handelaren", ""});
        RULE_LABELS.put("doInsomnia", new String[]{"Phantoms bij lang niet slapen", ""});
        RULE_LABELS.put("playersSleepingPercentage", new String[]{"Percentage slapers voor de ochtend",
                "Hoeveel procent van de spelers moet slapen om het dag te maken."});
        RULE_LABELS.put("randomTickSpeed", new String[]{"Groeisnelheid van planten",
                "Standaard 3. Hoger laat gewassen sneller groeien, maar kost prestaties."});
        RULE_LABELS.put("spawnRadius", new String[]{"Spreiding rond het spawnpunt", ""});
        RULE_LABELS.put("disableRaids", new String[]{"Raids uitschakelen", ""});
        RULE_LABELS.put("doLimitedCrafting", new String[]{"Alleen recepten die je geleerd hebt", ""});
    }

    private final RolBeheer plugin;

    public ServerSettings(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public static List<Setting> settings() {
        return SETTINGS;
    }

    public static Setting setting(String key) {
        return SETTINGS.stream().filter(s -> s.key().equals(key)).findFirst()
                .orElseThrow(() -> new ApiException("Deze instelling kun je hier niet wijzigen."));
    }

    // ---------------------------------------------------------------- server.properties

    private Path file() {
        Path path = new File("server.properties").toPath();
        if (!Files.exists(path)) throw new ApiException("server.properties is niet gevonden naast de server-jar.");
        return path;
    }

    public Map<String, String> readProperties() {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                int i = t.indexOf('=');
                if (i > 0) values.put(t.substring(0, i).trim(), t.substring(i + 1).trim());
            }
        } catch (IOException e) {
            throw new ApiException("server.properties kon niet gelezen worden: " + e.getMessage());
        }
        return values;
    }

    /** Schrijft één regel terug en laat de rest van het bestand met rust. */
    public void writeProperty(String key, String value) {
        Path path = file();
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (!t.startsWith("#") && t.startsWith(key + "=")) {
                    lines.set(i, key + "=" + value);
                    found = true;
                    break;
                }
            }
            if (!found) lines.add(key + "=" + value);
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ApiException("server.properties kon niet opgeslagen worden: " + e.getMessage());
        }
    }

    /** Waar mogelijk meteen toepassen, zodat je niet hoeft te herstarten. */
    public void applyLive(String key, String value) {
        List<World> worlds = Bukkit.getWorlds();
        switch (key) {
            case "motd" -> Bukkit.getServer().motd(legacy(value));
            case "max-players" -> Bukkit.getServer().setMaxPlayers(Integer.parseInt(value));
            case "difficulty" -> {
                Difficulty d = Difficulty.valueOf(value.toUpperCase(Locale.ROOT));
                worlds.forEach(w -> w.setDifficulty(d));
            }
            case "pvp" -> worlds.forEach(w -> w.setPVP(Boolean.parseBoolean(value)));
            case "gamemode" -> Bukkit.setDefaultGameMode(GameMode.valueOf(value.toUpperCase(Locale.ROOT)));
            case "view-distance" -> worlds.forEach(w -> w.setViewDistance(Integer.parseInt(value)));
            case "simulation-distance" -> worlds.forEach(w -> w.setSimulationDistance(Integer.parseInt(value)));
            case "player-idle-timeout" -> Bukkit.setIdleTimeout(Integer.parseInt(value));
            case "enforce-whitelist" -> Bukkit.setWhitelistEnforced(Boolean.parseBoolean(value));
            default -> { /* vraagt om een herstart */ }
        }
    }

    private static Component legacy(String value) {
        return Text.parse(value.replace('§', '&'));
    }

    // ---------------------------------------------------------------- gameregels

    public record RuleInfo(String key, String label, String description, boolean bool, String value) {}

    public List<RuleInfo> rules(World world) {
        List<RuleInfo> list = new ArrayList<>();
        for (Map.Entry<String, String[]> e : RULE_LABELS.entrySet()) {
            GameRule<?> rule = GameRule.getByName(e.getKey());
            if (rule == null) continue;
            Object value = world.getGameRuleValue(rule);
            list.add(new RuleInfo(e.getKey(), e.getValue()[0], e.getValue()[1],
                    rule.getType() == Boolean.class, String.valueOf(value)));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public void setRule(World world, String key, String value) {
        GameRule<?> rule = GameRule.getByName(key);
        if (rule == null || !RULE_LABELS.containsKey(key)) throw new ApiException("Onbekende gameregel.");
        if (rule.getType() == Boolean.class) {
            world.setGameRule((GameRule<Boolean>) rule, Boolean.parseBoolean(value));
        } else {
            try {
                world.setGameRule((GameRule<Integer>) rule, Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                throw new ApiException("Deze regel verwacht een getal.");
            }
        }
    }

    public World world(String name) {
        World world = name == null || name.isBlank() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(name);
        if (world == null) throw new ApiException("Deze wereld bestaat niet.");
        return world;
    }

    /** Controleert en normaliseert een ingevoerde waarde. */
    public static String check(Setting setting, String raw) {
        String value = raw == null ? "" : raw.trim();
        switch (setting.type()) {
            case BOOL -> {
                return String.valueOf(Boolean.parseBoolean(value));
            }
            case NUMBER -> {
                int n;
                try {
                    n = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ApiException(setting.label() + " verwacht een getal.");
                }
                if (n < setting.min() || n > setting.max()) {
                    throw new ApiException(setting.label() + " moet tussen " + setting.min() + " en " + setting.max() + " liggen.");
                }
                return String.valueOf(n);
            }
            case CHOICE -> {
                String v = value.toLowerCase(Locale.ROOT);
                if (!setting.options().contains(v)) throw new ApiException("Ongeldige keuze.");
                return v;
            }
            default -> {
                if (value.length() > 200) throw new ApiException("Deze tekst is te lang.");
                if (value.contains("\n") || value.contains("\r")) throw new ApiException("Gebruik één regel tekst.");
                return value;
            }
        }
    }
}
