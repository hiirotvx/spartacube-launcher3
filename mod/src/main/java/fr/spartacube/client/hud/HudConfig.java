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
        /** Opacité du fond (0 à 100 %). */
        public int bgOpacity = 50;
        /** Texte entre crochets : [60 FPS]. */
        public boolean brackets = false;
        /** Couleur personnalisée (teinte / saturation / luminosité). */
        public boolean customColor = false;
        public float hue = 0.07f, sat = 0.85f, bri = 1.0f;
        /** Mode arc-en-ciel. */
        public boolean chroma = false;
        public float chromaSpeed = 1.0f;
        /** Mode d'affichage propre au module (ex. armure : 0 icônes, 1 %, 2 barre, 3 restant). */
        public int mode = 0;
        public boolean horizontal = false;
        /** Couleurs selon l'état (ex. effets : vert = positif, rouge = négatif). */
        public boolean stateColors = true;

        public int textColor() {
            return textColor(0);
        }

        /** offset : décalage (en pixels) pour que l'arc-en-ciel « ondule » d'un élément à l'autre. */
        public int textColor(int offset) {
            if (chroma) {
                float h = (System.currentTimeMillis() % 100000L) / 1000f * 0.25f * chromaSpeed - offset / 300f;
                return 0xFF000000 | (java.awt.Color.HSBtoRGB(h - (float) Math.floor(h), 0.75f, 1f) & 0xFFFFFF);
            }
            if (customColor) return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF);
            return COLORS[Math.floorMod(color, COLORS.length)];
        }

        public int bgColor() {
            return (Math.round(Math.max(0, Math.min(100, bgOpacity)) * 2.55f) << 24);
        }
    }

    private static final class Data {
        Map<String, ModuleSettings> modules = new HashMap<>();
        boolean customTitleScreen = true;
        int zoomFov = 15;
        float flyBoostMult = 2.5f;
        boolean flyBoostSprintOnly = false;
        boolean toastSound = true;
        float toastVolume = 0.6f;
        boolean freelookHold = true;
        int zoomKey = 67;       // C
        int freelookKey = 342;  // Alt gauche
        int menuKey = 344;      // Shift droit
        int fullbrightKey = 0;  // aucune
        int healthMode = 0;
        boolean healthTargetOnly = true;
        boolean shotCopy = true;
        boolean shotToast = true;
        int themePreset = 0;
        float themeHue = 0.58f;
        float themeSat = 0.55f;
        boolean themeChroma = false;
        boolean themeNeutral = false;
    }

    private final Map<HudModule, ModuleSettings> modules = new EnumMap<>(HudModule.class);
    public boolean customTitleScreen = true;
    public int zoomFov = 15;
    public float flyBoostMult = 2.5f;
    public boolean flyBoostSprintOnly = false;
    public boolean toastSound = true;
    public float toastVolume = 0.6f;
    public boolean freelookHold = true;
    public int zoomKey = 67;
    public int freelookKey = 342;
    public int menuKey = 344;
    public int fullbrightKey = 0;
    /** 0 : « ❤ 9.5 », 1 : « 19 / 20 PV », 2 : barre de cœurs. */
    public int healthMode = 0;
    public boolean healthTargetOnly = true;
    /** Captures d'écran : copie automatique et notification. */
    public boolean shotCopy = true;
    public boolean shotToast = true;
    /** Thème du menu : préréglage, couleur perso (teinte / intensité), arc-en-ciel, fond neutre. */
    public int themePreset = 0;
    public float themeHue = 0.58f;
    public float themeSat = 0.55f;
    public boolean themeChroma = false;
    public boolean themeNeutral = false;

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
            cfg.flyBoostSprintOnly = data.flyBoostSprintOnly;
            cfg.toastSound = data.toastSound;
            cfg.toastVolume = Math.max(0f, Math.min(1f, data.toastVolume));
            cfg.freelookHold = data.freelookHold;
            cfg.zoomKey = data.zoomKey > 0 ? data.zoomKey : 67;
            cfg.freelookKey = data.freelookKey > 0 ? data.freelookKey : 342;
            cfg.menuKey = data.menuKey > 0 ? data.menuKey : 344;
            cfg.fullbrightKey = Math.max(0, data.fullbrightKey);
            cfg.healthMode = Math.floorMod(data.healthMode, 3);
            cfg.healthTargetOnly = data.healthTargetOnly;
            cfg.shotCopy = data.shotCopy;
            cfg.shotToast = data.shotToast;
            cfg.themePreset = Math.max(0, data.themePreset);
            cfg.themeHue = Math.max(0f, Math.min(1f, data.themeHue));
            cfg.themeSat = Math.max(0.1f, Math.min(1f, data.themeSat == 0 ? 0.55f : data.themeSat));
            cfg.themeChroma = data.themeChroma;
            cfg.themeNeutral = data.themeNeutral;
            cfg.flyBoostMult = Math.max(1f, Math.min(5f, data.flyBoostMult == 0 ? 2.5f : data.flyBoostMult));
        }
        return cfg;
    }

    public void save() {
        Data data = new Data();
        for (HudModule m : HudModule.values()) data.modules.put(m.name(), settings(m));
        data.customTitleScreen = customTitleScreen;
        data.zoomFov = zoomFov;
        data.flyBoostSprintOnly = flyBoostSprintOnly;
        data.toastSound = toastSound;
        data.toastVolume = toastVolume;
        data.freelookHold = freelookHold;
        data.zoomKey = zoomKey;
        data.freelookKey = freelookKey;
        data.menuKey = menuKey;
        data.fullbrightKey = fullbrightKey;
        data.healthMode = healthMode;
        data.healthTargetOnly = healthTargetOnly;
        data.shotCopy = shotCopy;
        data.shotToast = shotToast;
        data.themePreset = themePreset;
        data.themeHue = themeHue;
        data.themeSat = themeSat;
        data.themeChroma = themeChroma;
        data.themeNeutral = themeNeutral;
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
