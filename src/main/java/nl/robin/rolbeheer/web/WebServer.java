package nl.robin.rolbeheer.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nl.robin.rolbeheer.RolBeheer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Klein ingebouwd webpaneel. Inloggen gaat alleen via een eenmalige link uit /rol web.
 */
public final class WebServer {

    private static final String COOKIE = "rb_session";
    private static final long LOGIN_TOKEN_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long SESSION_MS = TimeUnit.HOURS.toMillis(12);

    private static final String LOGGED_OUT_PAGE = """
            <!doctype html><html lang="nl"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>RolBeheer</title>
            <style>body{font:16px/1.5 system-ui,sans-serif;background:#23272e;color:#e8e4dc;display:grid;
            place-items:center;min-height:100vh;margin:0}main{max-width:26rem;padding:2rem}
            code{background:#000a;padding:.15rem .4rem;border-radius:3px;color:#f2b83a}</style></head>
            <body><main><h1>Je bent niet ingelogd</h1>
            <p>Typ <code>/rol web</code> in de game of in de console. Je krijgt dan een inloglink die
            5 minuten geldig is.</p></main></body></html>
            """;

    private final RolBeheer plugin;
    private final WebApi api;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> loginTokens = new ConcurrentHashMap<>();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    private HttpServer server;
    private ExecutorService executor;
    private byte[] indexHtml;

    public WebServer(RolBeheer plugin) {
        this.plugin = plugin;
        this.api = new WebApi(plugin);
    }

    public boolean isRunning() {
        return server != null;
    }

    public void start() {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("web.ingeschakeld", true)) return;
        String host = c.getString("web.adres", "127.0.0.1");
        int port = c.getInt("web.poort", 8765);

        try (InputStream in = plugin.getResource("web/index.html")) {
            if (in == null) throw new IOException("web/index.html ontbreekt in de plugin");
            indexHtml = in.readAllBytes();

            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "RolBeheer-Web");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            plugin.getLogger().info("Webpaneel draait op http://" + host + ":" + port + " (inloggen via /rol web)");
        } catch (IOException | RuntimeException | LinkageError e) {
            plugin.getLogger().warning("Webpaneel kon niet starten: " + e.getMessage());
            stop();
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
    }

    /** Eenmalige inloglink, 5 minuten geldig. */
    public String createLoginLink() {
        String token = randomToken();
        long now = System.currentTimeMillis();
        loginTokens.values().removeIf(exp -> exp < now);
        loginTokens.put(token, now + LOGIN_TOKEN_MS);
        FileConfiguration c = plugin.getConfig();
        return "http://" + c.getString("web.publiek-adres", "localhost") + ":" + c.getInt("web.poort", 8765)
                + "/login?token=" + token;
    }

    // ---------------------------------------------------------------- verzoeken

    private void handle(HttpExchange ex) {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "DENY");
            ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");

            if (path.equals("/login")) {
                login(ex);
                return;
            }
            if (path.equals("/") || path.equals("/index.html")) {
                if (!authed(ex)) {
                    send(ex, 401, "text/html; charset=utf-8", LOGGED_OUT_PAGE.getBytes(StandardCharsets.UTF_8));
                } else {
                    ex.getResponseHeaders().set("Cache-Control", "no-store");
                    send(ex, 200, "text/html; charset=utf-8", indexHtml);
                }
                return;
            }
            if (path.startsWith("/api/")) {
                api(ex, method, path.substring(5));
                return;
            }
            send(ex, 404, "text/plain; charset=utf-8", "Niet gevonden".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Webpaneel-fout: " + e);
        }
    }

    private void api(HttpExchange ex, String method, String route) throws IOException {
        if (!authed(ex)) {
            json(ex, 401, error("Je bent niet (meer) ingelogd. Typ /rol web voor een nieuwe link."));
            return;
        }
        // Eigen header verplicht bij wijzigingen: een andere website kan die niet meesturen.
        if (method.equals("POST") && !"1".equals(ex.getRequestHeaders().getFirst("X-RolBeheer"))) {
            json(ex, 403, error("Verzoek geweigerd."));
            return;
        }

        JsonObject req = new JsonObject();
        if (method.equals("POST")) {
            String body = new String(ex.getRequestBody().readNBytes(1_000_000), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                try {
                    req = JsonParser.parseString(body).getAsJsonObject();
                } catch (RuntimeException e) {
                    json(ex, 400, error("Ongeldige gegevens."));
                    return;
                }
            }
        }

        JsonObject finalReq = req;
        try {
            JsonElement result = Bukkit.getScheduler()
                    .callSyncMethod(plugin, () -> api.handle(method, route, finalReq))
                    .get(10, TimeUnit.SECONDS);
            json(ex, 200, result);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ApiException ae) {
                json(ex, 400, error(ae.getMessage()));
            } else {
                plugin.getLogger().warning("Webpaneel-fout: " + e.getCause());
                json(ex, 500, error("Er ging iets mis op de server. Kijk in de console."));
            }
        } catch (Exception e) {
            json(ex, 503, error("De server reageert niet. Probeer het opnieuw."));
        }
    }

    private void login(HttpExchange ex) throws IOException {
        String token = query(ex, "token");
        Long exp = token == null ? null : loginTokens.remove(token);
        if (exp == null || exp < System.currentTimeMillis()) {
            send(ex, 401, "text/html; charset=utf-8", LOGGED_OUT_PAGE.replace("Je bent niet ingelogd",
                    "Deze link is verlopen of al gebruikt").getBytes(StandardCharsets.UTF_8));
            return;
        }
        String session = randomToken();
        long now = System.currentTimeMillis();
        sessions.values().removeIf(e -> e < now);
        sessions.put(session, now + SESSION_MS);
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + session
                + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (SESSION_MS / 1000));
        ex.getResponseHeaders().set("Location", "/");
        ex.sendResponseHeaders(302, -1);
    }

    private boolean authed(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return false;
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith(COOKIE + "=")) {
                Long exp = sessions.get(p.substring(COOKIE.length() + 1));
                return exp != null && exp > System.currentTimeMillis();
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- hulpjes

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && pair.substring(0, i).equals(key)) {
                return URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static JsonObject error(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        return o;
    }

    private static void json(HttpExchange ex, int status, JsonElement body) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        send(ex, status, "application/json; charset=utf-8", body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
