package nl.robin.rolbeheer.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Haalt nieuwe versies op uit de GitHub-release van je eigen repository.
 * De download wordt klaargezet en pas bij het afsluiten van de server omgewisseld,
 * zodat er nooit twee versies tegelijk in de plugins-map staan.
 *
 * Draait bewust NIET op de hoofdthread: netwerkverkeer mag de server niet ophouden.
 */
public final class Updater {

    private static final String API = "https://api.github.com/repos/";

    private final RolBeheer plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private volatile String latestVersion;
    private volatile String assetUrl;
    private volatile String assetName;
    private volatile String notes = "";

    public Updater(RolBeheer plugin) {
        this.plugin = plugin;
    }

    public JsonElement handle(String method, String route, JsonObject req) {
        return switch (route) {
            case "update" -> status(null);
            case "update/check" -> check();
            case "update/install" -> install();
            default -> throw new ApiException("Onbekende actie.");
        };
    }

    private String repo() {
        String repo = plugin.getConfig().getString("updates.repo", "").trim();
        if (repo.isEmpty()) {
            throw new ApiException("Er staat nog geen repository in config.yml. Vul updates.repo in, "
                    + "bijvoorbeeld jouwnaam/rolbeheer.");
        }
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new ApiException("updates.repo moet er zo uitzien: jouwnaam/rolbeheer");
        }
        return repo;
    }

    private String token() {
        return plugin.getConfig().getString("updates.token", "").trim();
    }

    public String currentVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    private JsonObject status(String message) {
        JsonObject o = new JsonObject();
        FileConfiguration c = plugin.getConfig();
        o.addProperty("current", currentVersion());
        o.addProperty("repo", c.getString("updates.repo", ""));
        o.addProperty("privateRepo", !token().isEmpty());
        o.addProperty("latest", latestVersion == null ? "" : latestVersion);
        o.addProperty("notes", notes);
        o.addProperty("available", latestVersion != null && isNewer(latestVersion, currentVersion()));
        o.addProperty("staged", stagedFile() != null);
        if (message != null) o.addProperty("message", message);
        return o;
    }

    // ---------------------------------------------------------------- controleren

    private JsonObject check() {
        HttpResponse<String> res = send(HttpRequest.newBuilder()
                .uri(URI.create(API + repo() + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28"));

        if (res.statusCode() == 404) {
            throw new ApiException("Geen release gevonden. Heeft GitHub de build al afgerond? "
                    + "Bij een privé-repo moet updates.token ingevuld zijn.");
        }
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new ApiException("GitHub weigert de toegang (" + res.statusCode()
                    + "). Controleer updates.token in config.yml.");
        }
        if (res.statusCode() != 200) throw new ApiException("GitHub antwoordde met code " + res.statusCode() + ".");

        JsonObject release = JsonParser.parseString(res.body()).getAsJsonObject();
        String tag = text(release, "tag_name");
        latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
        notes = text(release, "name");

        assetUrl = null;
        assetName = null;
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets != null) {
            for (JsonElement e : assets) {
                JsonObject asset = e.getAsJsonObject();
                String name = text(asset, "name");
                if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    assetName = name;
                    assetUrl = text(asset, "url");
                    break;
                }
            }
        }
        if (assetUrl == null) throw new ApiException("De release bevat geen jar-bestand.");

        boolean newer = isNewer(latestVersion, currentVersion());
        return status(newer ? "Versie " + latestVersion + " is beschikbaar."
                : "Je hebt al de nieuwste versie (" + currentVersion() + ").");
    }

    // ---------------------------------------------------------------- installeren

    private JsonObject install() {
        if (assetUrl == null) check();
        if (latestVersion == null || !isNewer(latestVersion, currentVersion())) {
            throw new ApiException("Er is geen nieuwere versie om te installeren.");
        }

        Path staging = plugin.getDataFolder().toPath().resolve("update");
        Path target = staging.resolve("TheBlueprint-" + latestVersion + ".jar");
        try {
            Files.createDirectories(staging);
            HttpResponse<InputStream> res = http.send(auth(HttpRequest.newBuilder()
                            .uri(URI.create(assetUrl))
                            .header("Accept", "application/octet-stream")
                            .timeout(Duration.ofMinutes(2)))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                throw new ApiException("Downloaden mislukt (code " + res.statusCode() + ").");
            }
            try (InputStream in = res.body()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | InterruptedException e) {
            throw new ApiException("Downloaden mislukt: " + e.getMessage());
        }

        if (!isValidPlugin(target)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // niets aan te doen
            }
            throw new ApiException("Het gedownloade bestand is geen geldige plugin van The Blueprint.");
        }

        plugin.getLogger().info("[Update] versie " + latestVersion + " staat klaar: " + target);
        return status("Versie " + latestVersion + " staat klaar. Herstart de server om hem in gebruik te nemen.");
    }

    /** Controleert of het echt onze plugin is voordat we hem straks installeren. */
    private static boolean isValidPlugin(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) return false;
            try (InputStream in = zip.getInputStream(entry)) {
                String yml = new String(in.readAllBytes());
                return yml.contains("nl.robin.rolbeheer.RolBeheer");
            }
        } catch (IOException e) {
            return false;
        }
    }

    public Path stagedFile() {
        Path staging = plugin.getDataFolder().toPath().resolve("update");
        if (!Files.isDirectory(staging)) return null;
        try (var stream = Files.list(staging)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".jar")).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Wordt bij het afsluiten van de server aangeroepen: de nieuwe jar komt in de plugins-map
     * en de oude wordt verwijderd, zodat er na de herstart precies één versie staat.
     */
    public void applyStagedOnShutdown() {
        Path staged = stagedFile();
        if (staged == null) return;
        try {
            Path current = plugin.jarFile().toPath();
            Path destination = current.getParent().resolve(staged.getFileName());
            Files.copy(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            if (!destination.equals(current)) Files.deleteIfExists(current);
            Files.deleteIfExists(staged);
            plugin.getLogger().info("[Update] " + destination.getFileName() + " geïnstalleerd; oude versie verwijderd.");
        } catch (IOException e) {
            plugin.getLogger().warning("[Update] installeren mislukt: " + e.getMessage()
                    + ". Zet het bestand uit plugins/TheBlueprint/update/ zelf in de plugins-map.");
        }
    }

    // ---------------------------------------------------------------- hulpjes

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return http.send(auth(builder).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException("Kon GitHub niet bereiken: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("De controle werd onderbroken.");
        }
    }

    private HttpRequest.Builder auth(HttpRequest.Builder builder) {
        String token = token();
        if (!token.isEmpty()) builder.header("Authorization", "Bearer " + token);
        return builder.header("User-Agent", "TheBlueprint");
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** Vergelijkt versies als 1.10.0 tegen 1.9.0. */
    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[^0-9]+");
        String[] b = current.split("[^0-9]+");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isEmpty() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }
}
