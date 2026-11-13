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

/** Réglages de l'HUD, sauvegardés dans config/spartacube-client.json. */
public final class HudConfig {
    public static final int[] COLORS = {0xFFFFFFFF, 0xFFFFB02E, 0xFFFF5A1F, 0xFF4ADE80, 0xFF60A5FA, 0xFFF472B6};
    public static final String[] COLOR_NAMES = {"Blanc", "Or", "Feu", "Vert", "Bleu", "Rose"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spartacube-client.json");
    private static HudConfig instance;

    /** Données sérialisées. */
    private static final class Data {
        Map<String, Boolean> enabled = new HashMap<>();
        Map<String, int[]> positions = new HashMap<>();
        int color = 0;
        boolean background = true;
        boolean shadow = true;
        boolean customTitleScreen = true;
    }

    private final Map<HudModule, Boolean> enabled = new EnumMap<>(HudModule.class);
    private final Map<HudModule, int[]> positions = new EnumMap<>(HudModule.class);
    public int color = 0;
    public boolean background = true;
    public boolean shadow = true;
    public boolean customTitleScreen = true;

    public static HudConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static HudConfig load() {
        HudConfig cfg = new HudConfig();
        Data data = null;
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(r, Data.class);
            } catch (Exception ignored) {
                // fichier corrompu : on repart des valeurs par défaut
            }
        }
        for (HudModule m : HudModule.values()) {
            Boolean on = data != null && data.enabled != null ? data.enabled.get(m.name()) : null;
            cfg.enabled.put(m, on != null ? on : m.defaultEnabled);
            int[] pos = data != null && data.positions != null ? data.positions.get(m.name()) : null;
            cfg.positions.put(m, pos != null && pos.length == 2 ? pos : new int[]{m.defaultX, m.defaultY});
        }
        if (data != null) {
            cfg.color = Math.floorMod(data.color, COLORS.length);
            cfg.background = data.background;
            cfg.shadow = data.shadow;
            cfg.customTitleScreen = data.customTitleScreen;
        }
        return cfg;
    }

    public void save() {
        Data data = new Data();
        for (HudModule m : HudModule.values()) {
            data.enabled.put(m.name(), isEnabled(m));
            data.positions.put(m.name(), position(m));
        }
        data.color = color;
        data.background = background;
        data.shadow = shadow;
        data.customTitleScreen = customTitleScreen;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (IOException ignored) {
            // impossible d'écrire : les réglages restent valables pour cette session
        }
    }

    public boolean isEnabled(HudModule m) {
        return enabled.getOrDefault(m, m.defaultEnabled);
    }

    public void toggle(HudModule m) {
        enabled.put(m, !isEnabled(m));
        save();
    }

    public int[] position(HudModule m) {
        return positions.computeIfAbsent(m, k -> new int[]{k.defaultX, k.defaultY});
    }

    public void setPosition(HudModule m, int x, int y) {
        positions.put(m, new int[]{x, y});
    }

    public void resetPositions() {
        for (HudModule m : HudModule.values()) positions.put(m, new int[]{m.defaultX, m.defaultY});
        save();
    }

    public int textColor() {
        return COLORS[Math.floorMod(color, COLORS.length)];
    }
}
