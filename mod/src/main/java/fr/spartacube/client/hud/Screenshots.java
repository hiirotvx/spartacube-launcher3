package fr.spartacube.client.hud;

import com.google.gson.JsonObject;
import fr.spartacube.client.net.VoteBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Captures d'écran simplifiées : dès qu'une capture (F2) apparaît dans le dossier, elle est copiée dans le
 * presse-papiers par le launcher (pont local) et une notification le confirme.
 */
public final class Screenshots {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private static Set<String> known;
    private static long lastModified = -1;
    private static int ticks;
    private static String pending;
    private static int pendingTicks;

    private Screenshots() {}

    public static File dir() {
        return new File(MinecraftClient.getInstance().runDirectory, "screenshots");
    }

    public static void openFolder() {
        File d = dir();
        d.mkdirs();
        Util.getOperatingSystem().open(d.toURI());
    }

    private static Set<String> list(File dir) {
        Set<String> out = new HashSet<>();
        String[] names = dir.list((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
        if (names != null) java.util.Collections.addAll(out, names);
        return out;
    }

    public static void tick(MinecraftClient mc) {
        // Le fichier est écrit en arrière-plan : on attend une seconde avant de le traiter
        if (pending != null && --pendingTicks <= 0) {
            String name = pending;
            pending = null;
            handle(name);
        }
        if (++ticks % 10 != 0) return;
        if (!HudConfig.get().isEnabled(HudModule.SCREENSHOTS)) { known = null; return; }
        File dir = dir();
        long mod = dir.lastModified();
        if (known == null) { known = list(dir); lastModified = mod; return; }
        if (mod == lastModified) return;
        lastModified = mod;
        Set<String> now = list(dir);
        String newest = null;
        long newestTime = 0;
        for (String n : now) {
            if (known.contains(n)) continue;
            long t = new File(dir, n).lastModified();
            if (newest == null || t >= newestTime) { newest = n; newestTime = t; }
        }
        known = now;
        if (newest != null) { pending = newest; pendingTicks = 20; }
    }

    private static void handle(String name) {
        HudConfig cfg = HudConfig.get();
        if (!cfg.shotCopy) {
            if (cfg.shotToast) Toasts.show("info", "Capture enregistrée", "Retrouve-la dans le launcher, onglet Captures.");
            return;
        }
        JsonObject j = VoteBridge.read();
        if (j == null) { fallback(cfg); return; }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + j.get("port").getAsInt()
                            + "/screenshot-copy?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(4))
                    .header("X-SpartaCube-Token", j.get("token").getAsString())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err == null && res.statusCode() == 204) {
                    if (cfg.shotToast) Toasts.show("info", "Capture copiée", "Colle-la où tu veux avec Ctrl+V. Elle est aussi dans le launcher, onglet Captures.");
                } else {
                    fallback(cfg);
                }
            });
        } catch (Exception e) {
            fallback(cfg);
        }
    }

    private static void fallback(HudConfig cfg) {
        if (cfg.shotToast) Toasts.show("info", "Capture enregistrée", "Copie impossible (launcher fermé). Elle est dans le dossier des captures.");
    }
}
