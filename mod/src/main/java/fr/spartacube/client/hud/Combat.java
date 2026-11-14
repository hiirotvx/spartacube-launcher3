package fr.spartacube.client.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Suivi de combat et petits outils : combo, portée du dernier coup, chronomètre, temps de session, snaplook. */
public final class Combat {
    private static final long START = System.currentTimeMillis();
    public static int combo;
    private static long lastHit;
    public static double reach = -1;
    private static long reachAt;

    private static boolean swRunning, swKeyDown;
    private static long swStart, swElapsed;

    private static boolean snapActive;
    private static Perspective snapBefore;

    private Combat() {}

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && player == MinecraftClient.getInstance().player && entity instanceof LivingEntity) {
                Vec3d eye = player.getEyePos();
                Box b = entity.getBoundingBox();
                double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
                double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
                double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
                reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
                reachAt = System.currentTimeMillis();
                combo++;
                lastHit = reachAt;
            }
            return ActionResult.PASS;
        });
    }

    public static void tick(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        HudConfig cfg = HudConfig.get();
        // Combo : remis à zéro si on se fait toucher ou après 3 s sans frapper
        if (mc.player == null || mc.player.hurtTime > 0 || now - lastHit > 3000) combo = 0;
        if (now - reachAt > 5000) reach = -1;

        // Chronomètre : la touche choisie démarre / met en pause
        int swKey = cfg.settings(HudModule.STOPWATCH).mode;
        boolean down = cfg.isEnabled(HudModule.STOPWATCH) && swKey > 0 && mc.currentScreen == null && Keys.isDown(swKey);
        if (down && !swKeyDown) {
            if (swRunning) { swElapsed += now - swStart; swRunning = false; }
            else { swStart = now; swRunning = true; }
        }
        swKeyDown = down;

        // Snaplook : maintenir la touche pour regarder derrière soi
        int snapKey = cfg.settings(HudModule.SNAPLOOK).mode;
        boolean snap = cfg.isEnabled(HudModule.SNAPLOOK) && snapKey > 0 && mc.currentScreen == null && mc.player != null && Keys.isDown(snapKey);
        if (snap && !snapActive) {
            snapBefore = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            snapActive = true;
        } else if (!snap && snapActive) {
            mc.options.setPerspective(snapBefore == null ? Perspective.FIRST_PERSON : snapBefore);
            snapActive = false;
        }
    }

    public static void resetStopwatch() {
        swRunning = false;
        swElapsed = 0;
    }

    public static String stopwatch() {
        long ms = swElapsed + (swRunning ? System.currentTimeMillis() - swStart : 0);
        long s = ms / 1000;
        return String.format("%02d:%02d.%d", s / 60, s % 60, (ms % 1000) / 100);
    }

    public static String playtime() {
        long s = (System.currentTimeMillis() - START) / 1000;
        long h = s / 3600, m = (s % 3600) / 60;
        return h > 0 ? h + "h " + String.format("%02d", m) + "m" : m + "m " + String.format("%02d", s % 60) + "s";
    }
}
