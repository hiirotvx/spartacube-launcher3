package fr.spartacube.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Bouton « Voter » : demande au launcher SpartaCube (qui tourne en arrière-plan) d'ouvrir la page de vote
 * dans une fenêtre au-dessus du jeu. Communication locale uniquement (127.0.0.1 + jeton secret).
 */
public final class VoteBridge {
    private static final String FALLBACK_URL = "https://spartacube.fr/vote";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private VoteBridge() {}

    /** Contenu du fichier de pont écrit par le launcher (ou null). */
    public static JsonObject read() {
        try {
            Path file = FabricLoader.getInstance().getGameDir().resolve(".spartacube-bridge.json");
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** Commande de destination choisie dans le launcher (« Rejoindre directement »). */
    public static String joinCommand() {
        JsonObject j = read();
        if (j == null || !j.has("joinCommand")) return "";
        String c = j.get("joinCommand").getAsString();
        return c.matches("^/[\\w :.\\-]{1,60}$") ? c : "";
    }

    /** Demande au launcher si un vote est disponible (appel asynchrone). */
    public static void fetchVoteAvailable(java.util.function.Consumer<Boolean> callback) {
        JsonObject j = read();
        if (j == null) return;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + j.get("port").getAsInt() + "/vote-status"))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-SpartaCube-Token", j.get("token").getAsString())
                    .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
                if (res.statusCode() != 200) return;
                JsonObject st = JsonParser.parseString(res.body()).getAsJsonObject();
                if (st.has("anyAvailable")) callback.accept(st.get("anyAvailable").getAsBoolean());
            });
        } catch (Exception ignored) {
            // launcher fermé : pas de rappel
        }
    }

    public static void openVote() {
        Path file = FabricLoader.getInstance().getGameDir().resolve(".spartacube-bridge.json");
        String fallback = FALLBACK_URL;
        try {
            JsonObject j = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (j.has("voteUrl") && j.get("voteUrl").getAsString().startsWith("https://")) fallback = j.get("voteUrl").getAsString();
            int port = j.get("port").getAsInt();
            String token = j.get("token").getAsString();
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/vote"))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-SpartaCube-Token", token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            final String url = fallback;
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err != null || res.statusCode() != 204) openInBrowser(url);
            });
        } catch (Exception e) {
            openInBrowser(fallback);
        }
    }

    private static void openInBrowser(String url) {
        Util.getOperatingSystem().open(URI.create(url));
    }
}
