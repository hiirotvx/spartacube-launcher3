package fr.spartacube.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Réglages du mod, sauvegardés dans config/spartacube-client.json. */
public final class HudConfig {
    public static final int[] COLORS = {0xFFFFFFFF, 0xFFFFB02E, 0xFFFF5A1F, 0xFFFF4D6D, 0xFF4ADE80, 0xFF60A5FA, 0xFFC084FC, 0xFF9CA3AF};
    public static final String[] COLOR_NAMES = {"Blanc", "Or", "Feu", "Rouge", "Vert", "Bleu", "Violet", "Gris"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spartacube-client.json");
    private static HudConfig instance;

    /** Options propres à un module. */
    public static final class ModuleSettings {
        public boolean enabled;
        public int x, y;
        public float scale = 1.0f;
        public int color = 0;
        public boolean background = true;
        public boolean shadow = true;

        public int textColor() {
            return COLORS[Math.floorMod(color, COLORS.length)];
        }
    }

    private static final class Data {
        Map<String, ModuleSettings> modules = new HashMap<>();
        boolean customTitleScreen = true;
        int zoomFov = 15;
        boolean flyBoost = true;
        float flyBoostMult = 2.5f;
    }

    private final Map<HudModule, ModuleSettings> modules = new EnumMap<>(HudModule.class);
    public boolean customTitleScreen = true;
    public int zoomFov = 15;
    public boolean flyBoost = true;
    public float flyBoostMult = 2.5f;

    public static HudConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static ModuleSettings defaults(HudModule m) {
        ModuleSettings s = new ModuleSettings();
        s.enabled = m.defaultEnabled;
        s.x = m.defaultX;
        s.y = m.defaultY;
        return s;
    }

    private static HudConfig load() {
        HudConfig cfg = new HudConfig();
        Data data = null;
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(r, Data.class);
            } catch (Exception ignored) {
                // fichier ancien ou corrompu : valeurs par défaut
            }
        }
        for (HudModule m : HudModule.values()) {
            ModuleSettings s = data != null && data.modules != null ? data.modules.get(m.name()) : null;
            if (s == null) s = defaults(m);
            s.scale = Math.max(0.5f, Math.min(2.0f, s.scale == 0 ? 1.0f : s.scale));
            cfg.modules.put(m, s);
        }
        if (data != null) {
            cfg.customTitleScreen = data.customTitleScreen;
            cfg.zoomFov = Math.max(5, Math.min(60, data.zoomFov == 0 ? 15 : data.zoomFov));
            cfg.flyBoost = data.flyBoost;
            cfg.flyBoostMult = Math.max(1f, Math.min(5f, data.flyBoostMult == 0 ? 2.5f : data.flyBoostMult));
        }
        return cfg;
    }

    public void save() {
        Data data = new Data();
        for (HudModule m : HudModule.values()) data.modules.put(m.name(), settings(m));
        data.customTitleScreen = customTitleScreen;
        data.zoomFov = zoomFov;
        data.flyBoost = flyBoost;
        data.flyBoostMult = flyBoostMult;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException ignored) {
            // impossible d'écrire : réglages gardés pour la session
        }
    }

    public ModuleSettings settings(HudModule m) {
        return modules.computeIfAbsent(m, HudConfig::defaults);
    }

    public boolean isEnabled(HudModule m) {
        return settings(m).enabled;
    }

    public void toggle(HudModule m) {
        settings(m).enabled = !settings(m).enabled;
        save();
    }

    public void setPosition(HudModule m, int x, int y) {
        settings(m).x = x;
        settings(m).y = y;
    }

    public void resetPositions() {
        for (HudModule m : HudModule.values()) {
            ModuleSettings s = settings(m);
            s.x = m.defaultX;
            s.y = m.defaultY;
        }
        save();
    }

    public void resetModule(HudModule m) {
        boolean on = isEnabled(m);
        ModuleSettings s = defaults(m);
        s.enabled = on;
        modules.put(m, s);
        save();
    }
}
