package fr.spartacube.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

/** Freelook : maintenir Alt gauche pour regarder autour de soi sans tourner le personnage. */
public final class Freelook {
    private static boolean active;
    private static float yaw, pitch;
    private static Perspective previous;

    private Freelook() {}

    public static boolean isActive() {
        return active;
    }

    public static float yaw() { return yaw; }

    public static float pitch() { return pitch; }

    public static void tick(MinecraftClient mc) {
        long w = GLFW.glfwGetCurrentContext();
        boolean want = HudConfig.get().isEnabled(HudModule.FREELOOK) && mc.player != null && mc.currentScreen == null
                && w != 0L && GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
        if (want && !active) {
            active = true;
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            previous = mc.options.getPerspective();
            if (previous == Perspective.FIRST_PERSON) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!want && active) {
            active = false;
            if (previous != null) mc.options.setPerspective(previous);
        }
    }

    /** Appelé à la place de la rotation du joueur quand le freelook est actif. */
    public static void turn(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * 0.15f));
    }
}
