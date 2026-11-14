package fr.spartacube.client.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import fr.spartacube.client.mixin.AccessorMinecraftClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Comptes enregistrés dans le launcher : liste et changement de compte sans relancer le jeu. */
public final class Accounts {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public record Account(String uuid, String name, boolean active) {}

    private Accounts() {}

    private static HttpRequest.Builder request(JsonObject bridge, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + bridge.get("port").getAsInt() + path))
                .timeout(Duration.ofSeconds(25))
                .header("X-SpartaCube-Token", bridge.get("token").getAsString());
    }

    /** Liste des comptes (null si le launcher est fermé). Le résultat arrive sur le fil du jeu. */
    public static void list(Consumer<List<Account>> callback) {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject bridge = VoteBridge.read();
        if (bridge == null) { callback.accept(null); return; }
        try {
            HTTP.sendAsync(request(bridge, "/accounts").GET().build(), HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> {
                List<Account> out = null;
                try {
                    if (err == null && res.statusCode() == 200) {
                        out = new ArrayList<>();
                        JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
                        for (JsonElement e : arr) {
                            JsonObject o = e.getAsJsonObject();
                            out.add(new Account(o.get("uuid").getAsString(), o.get("name").getAsString(), o.get("active").getAsBoolean()));
                        }
                    }
                } catch (Exception ignored) {
                    out = null;
                }
                List<Account> result = out;
                mc.execute(() -> callback.accept(result));
            });
        } catch (Exception e) {
            callback.accept(null);
        }
    }

    /** Passe sur un autre compte. callback : null si tout va bien, sinon le message d'erreur. */
    public static void switchTo(Account account, Consumer<String> callback) {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject bridge = VoteBridge.read();
        if (bridge == null) { callback.accept("Le launcher est fermé."); return; }
        try {
            HttpRequest req = request(bridge, "/account-switch?uuid=" + account.uuid()).POST(HttpRequest.BodyPublishers.noBody()).build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) -> mc.execute(() -> {
                try {
                    if (err != null) { callback.accept("Le launcher ne répond pas."); return; }
                    JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (res.statusCode() != 200) { callback.accept(o.has("error") ? o.get("error").getAsString() : "Changement impossible."); return; }
                    apply(mc, o.get("name").getAsString(), o.get("uuid").getAsString(), o.get("accessToken").getAsString());
                    callback.accept(null);
                } catch (Throwable t) {
                    callback.accept("Changement impossible dans cette version du jeu.");
                }
            }));
        } catch (Exception e) {
            callback.accept("Changement impossible.");
        }
    }

    private static void apply(MinecraftClient mc, String name, String rawUuid, String token) {
        UUID uuid = UUID.fromString(rawUuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        Session session = new Session(name, uuid, token, Optional.empty(), Optional.empty());
        AccessorMinecraftClient acc = (AccessorMinecraftClient) mc;
        acc.spartacube$setSession(session);
        // Services liés au compte (signature du chat) : recréés pour le nouveau compte
        try {
            UserApiService api = new YggdrasilAuthenticationService(mc.getNetworkProxy()).createUserApiService(token);
            acc.spartacube$setUserApiService(api);
            acc.spartacube$setProfileKeys(ProfileKeys.create(api, session, mc.runDirectory.toPath()));
        } catch (Throwable ignored) {
            // la session suffit pour rejoindre un serveur
        }
    }
}
