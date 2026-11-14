package fr.spartacube.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.lwjgl.glfw.GLFW;

/** Mécaniques : toggle sprint / sneak (options vanilla) et zoom fluide sur la touche C. */
public final class Mechanics {
    private static Boolean appliedSprint, appliedSneak;
    private static Double savedSensitivity;
    private static float zoomProgress;
    private static boolean zoomTarget;
    private static long lastFrame;
    private static boolean flyBoosted;
    /** Un autre mod de zoom (Zoomify, Ok Zoomer…) gère déjà la touche C : on s'efface. */
    private static final boolean OTHER_ZOOM_MOD = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("zoomify")
            || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ok_zoomer");

    private Mechanics() {}

    public static void tick(MinecraftClient mc) {
        HudConfig cfg = HudConfig.get();
        GameOptions o = mc.options;

        boolean sprint = cfg.isEnabled(HudModule.TOGGLE_SPRINT);
        if (appliedSprint == null || appliedSprint != sprint) {
            o.getSprintToggled().setValue(sprint);
            appliedSprint = sprint;
        }
        boolean sneak = cfg.isEnabled(HudModule.TOGGLE_SNEAK);
        if (appliedSneak == null || appliedSneak != sneak) {
            o.getSneakToggled().setValue(sneak);
            appliedSneak = sneak;
        }

        // Fly Boost (module indépendant) : vitesse de vol augmentée
        if (mc.player != null) {
            var ab = mc.player.getAbilities();
            boolean sprinting = o.sprintKey.isPressed() || mc.player.isSprinting();
            boolean boost = ab.flying && cfg.isEnabled(HudModule.FLY_BOOST) && (!cfg.flyBoostSprintOnly || sprinting);
            float wanted = boost ? 0.05f * cfg.flyBoostMult : 0.05f;
            if (boost || flyBoosted) {
                if (Math.abs(ab.getFlySpeed() - wanted) > 1e-4f) ab.setFlySpeed(wanted);
                flyBoosted = boost;
            }
        }

        // Zoom : maintenir C (hors menus). Le champ de vision est modifié par MixinGameRenderer.
        long w = GLFW.glfwGetCurrentContext();
        zoomTarget = cfg.isEnabled(HudModule.ZOOM) && !OTHER_ZOOM_MOD && mc.currentScreen == null && mc.player != null
                && w != 0L && GLFW.glfwGetKey(w, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        if (zoomTarget && savedSensitivity == null) {
            savedSensitivity = o.getMouseSensitivity().getValue();
            o.getMouseSensitivity().setValue(savedSensitivity * 0.45);
        } else if (!zoomTarget && savedSensitivity != null) {
            o.getMouseSensitivity().setValue(savedSensitivity);
            savedSensitivity = null;
        }
    }

    /** Facteur appliqué au champ de vision (1 = normal), animé à chaque image. */
    public static float fovFactor() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        zoomProgress += ((zoomTarget ? 1f : 0f) - zoomProgress) * (1f - (float) Math.exp(-14f * dt));
        float target = HudConfig.get().zoomFov / 70f;
        return 1f + (target - 1f) * zoomProgress;
    }

    /** Restaure la sensibilité si le jeu se ferme pendant un zoom. */
    public static void restore(MinecraftClient mc) {
        if (savedSensitivity != null) {
            mc.options.getMouseSensitivity().setValue(savedSensitivity);
            savedSensitivity = null;
        }
    }
}
