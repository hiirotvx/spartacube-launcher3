package fr.spartacube.client.hud;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;

/** Interrupteur du mod 3D Skin Layers : active ou coupe ses couches 3D sans redémarrer le jeu. */
public final class Skin3D {
    private static Boolean applied;
    private static boolean failed;

    private Skin3D() {}

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("skinlayers3d");
    }

    public static void tick() {
        if (failed || !installed()) return;
        boolean want = HudConfig.get().isEnabled(HudModule.SKIN3D);
        if (applied == null && want) { applied = true; return; } // activé : on garde les réglages du mod
        if (applied != null && applied == want) return;
        try {
            Class<?> base = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase");
            Object config = base.getField("config").get(null);
            if (config == null) return; // pas encore chargé : on réessaie au prochain tick
            int n = 0;
            for (Field f : config.getClass().getFields()) {
                if (f.getType() == boolean.class && f.getName().startsWith("enable")) { f.setBoolean(config, want); n++; }
            }
            if (n == 0) throw new IllegalStateException("aucune option trouvée");
            applied = want;
        } catch (Throwable t) {
            failed = true;
            Toasts.show("info", "Skins 3D", "Impossible de régler le mod depuis ce menu. Désactive-le dans le launcher, onglet Mods.");
        }
    }
}
