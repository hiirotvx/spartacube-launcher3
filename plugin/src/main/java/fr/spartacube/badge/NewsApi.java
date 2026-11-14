package fr.spartacube.badge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Petite API HTTP des actualités du launcher.
 *
 * GET  /news            : liste publique des actualités.
 * GET  /news/challenge  : nonce à usage unique (60 s).
 * POST /news/publish    : {username, nonce, payload} — payload = liste JSON (texte).
 *
 * Authentification sans mot de passe : le launcher de l'admin « rejoint » la session Mojang avec
 * serverId = sha1(nonce + ":" + sha256(payload)). Le plugin vérifie auprès de Mojang (hasJoined)
 * que ce compte a bien fait cette demande, puis que son UUID est dans la liste des admins.
 * Le contenu est ainsi lié à la signature : une modification en route invalide la publication.
 */
public final class NewsApi {
    private static final int MAX_BODY = 96 * 1024;
    private static final int MAX_NEWS = 30;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Logger log;
    private final Path file;
    private final Set<String> admins;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> nonces = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private HttpServer server;
    private volatile String cached = "[]";

    public NewsApi(Logger log, Path dataFolder, Set<String> admins) {
        this.log = log;
        this.file = dataFolder.resolve("news.json");
        this.admins = admins;
    }

    /** Charge news.json (à appeler une fois au démarrage). */
    public void init() {
        load();
    }

    public static final int MAX_REQUEST = MAX_BODY + 8192;

    /** Serveur HTTP sur un port dédié (optionnel). */
    public void start(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 16);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SpartaCube-NewsApi");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/news", this::handle);
        server.start();
        log.info("API des actualités du launcher sur le port " + port + ".");
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void load() {
        try {
            if (Files.exists(file)) cached = GSON.toJson(sanitize(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))));
        } catch (Exception e) {
            log.warning("news.json illisible, liste vide utilisée : " + e.getMessage());
            cached = "[]";
        }
    }

    /** Réponse HTTP minimale. */
    public record Resp(int code, String json) {}

    private void handle(HttpExchange ex) throws IOException {
        try {
            byte[] body;
            try (InputStream in = ex.getRequestBody()) {
                body = in.readNBytes(MAX_BODY + 1);
            }
            Resp r = handle(ex.getRequestMethod(), ex.getRequestURI().getPath(), ex.getRemoteAddress().getAddress().getHostAddress(), body);
            send(ex, r.code(), r.json());
        } finally {
            ex.close();
        }
    }

    /** Traite une requête (serveur HTTP dédié ou port Minecraft partagé). Peut bloquer (appel Mojang). */
    public Resp handle(String method, String path, String ip, byte[] body) {
        try {
            if ("GET".equals(method) && "/news".equals(path)) return new Resp(200, cached);
            if ("GET".equals(method) && "/news/challenge".equals(path)) {
                if (!allow(ip, 10)) return new Resp(429, error("Trop de demandes, réessaie dans une minute."));
                cleanNonces();
                byte[] b = new byte[16];
                random.nextBytes(b);
                String nonce = HexFormat.of().formatHex(b);
                nonces.put(nonce, System.currentTimeMillis());
                JsonObject o = new JsonObject();
                o.addProperty("nonce", nonce);
                return new Resp(200, o.toString());
            }
            if ("POST".equals(method) && "/news/publish".equals(path)) {
                if (!allow(ip, 10)) return new Resp(429, error("Trop de demandes, réessaie dans une minute."));
                return publish(body, ip);
            }
            return new Resp(404, error("Introuvable"));
        } catch (Exception e) {
            log.warning("API actualités : " + e.getMessage());
            return new Resp(500, error("Erreur interne"));
        }
    }

    private Resp publish(byte[] body, String ip) throws Exception {
        if (body.length > MAX_BODY) return new Resp(413, error("Contenu trop volumineux."));
        JsonObject req = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        String username = req.get("username").getAsString();
        String nonce = req.get("nonce").getAsString();
        String payload = req.get("payload").getAsString();
        if (!username.matches("[A-Za-z0-9_]{1,16}")) return new Resp(400, error("Pseudo invalide."));

        Long issued = nonces.remove(nonce); // usage unique
        if (issued == null || System.currentTimeMillis() - issued > 60_000) return new Resp(401, error("Demande expirée, réessaie."));

        String serverId = hex(sha1((nonce + ":" + hex(sha256(payload.getBytes(StandardCharsets.UTF_8)))).getBytes(StandardCharsets.UTF_8)));
        String url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&serverId=" + serverId;
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200 || res.body().isBlank()) {
            log.warning("Publication d'actualités refusée (session Mojang non vérifiée) pour " + username + " depuis " + ip);
            return new Resp(401, error("Ton compte Microsoft n'a pas pu être vérifié. Reconnecte-toi dans le launcher."));
        }
        String id = JsonParser.parseString(res.body()).getAsJsonObject().get("id").getAsString().toLowerCase();
        String dashed = id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
        if (!admins.contains(dashed)) {
            log.warning("Publication d'actualités refusée : " + username + " (" + dashed + ") n'est pas admin.");
            return new Resp(403, error("Ton compte n'a pas les droits d'administration."));
        }

        JsonArray clean = sanitize(JsonParser.parseString(payload));
        String json = GSON.toJson(clean);
        synchronized (this) {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("news.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            cached = json;
        }
        log.info(username + " a publié " + clean.size() + " actualité(s) depuis le launcher.");
        JsonObject ok = new JsonObject();
        ok.addProperty("ok", true);
        ok.addProperty("count", clean.size());
        return new Resp(200, ok.toString());
    }

    /** Ne garde que les champs connus, en texte brut, longueurs limitées, liens HTTPS uniquement. */
    private static JsonArray sanitize(JsonElement el) {
        JsonArray out = new JsonArray();
        if (el == null || !el.isJsonArray()) return out;
        for (JsonElement e : el.getAsJsonArray()) {
            if (out.size() >= MAX_NEWS || !e.isJsonObject()) continue;
            JsonObject n = e.getAsJsonObject();
            String title = str(n, "title", 120);
            if (title.isBlank()) continue;
            JsonObject c = new JsonObject();
            c.addProperty("title", title);
            c.addProperty("text", str(n, "text", 4000));
            c.addProperty("date", str(n, "date", 40));
            c.addProperty("tag", str(n, "tag", 24));
            String image = str(n, "image", 500), url = str(n, "url", 500);
            if (image.startsWith("https://")) c.addProperty("image", image);
            if (url.startsWith("https://")) c.addProperty("url", url);
            out.add(c);
        }
        return out;
    }

    private static String str(JsonObject o, String k, int max) {
        JsonElement v = o.get(k);
        if (v == null || !v.isJsonPrimitive()) return "";
        String s = v.getAsString().trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private boolean allow(String ip, int perMinute) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= perMinute) return false;
            q.addLast(now);
            return true;
        }
    }

    private void cleanNonces() {
        long now = System.currentTimeMillis();
        nonces.values().removeIf(t -> now - t > 60_000);
        if (nonces.size() > 500) nonces.clear();
    }

    private static String error(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        return o.toString();
    }

    private static void send(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static byte[] sha1(byte[] b) throws Exception { return MessageDigest.getInstance("SHA-1").digest(b); }

    private static byte[] sha256(byte[] b) throws Exception { return MessageDigest.getInstance("SHA-256").digest(b); }

    private static String hex(byte[] b) { return HexFormat.of().formatHex(b); }
}
