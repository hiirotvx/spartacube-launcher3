package fr.spartacube.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/** Dessin des modules d'HUD (utilisé en jeu et dans l'éditeur de position). */
public final class HudRenderer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int PAD = 3;
    private static final int KEY = 20;
    private static final int GAP = 2;

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static boolean leftWasDown;
    private static boolean rightWasDown;

    private HudRenderer() {}

    /** À appeler à chaque image : compte les clics (CPS). */
    public static void pollClicks() {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0L) return;
        boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (left && !leftWasDown) LEFT_CLICKS.addLast(now);
        if (right && !rightWasDown) RIGHT_CLICKS.addLast(now);
        leftWasDown = left;
        rightWasDown = right;
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000) LEFT_CLICKS.pollFirst();
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000) RIGHT_CLICKS.pollFirst();
    }

    /** Texte d'un module (null pour les modules dessinés autrement). */
    public static String text(HudModule m, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        switch (m) {
            case FPS:
                return mc.getCurrentFps() + " FPS";
            case PING: {
                ClientPlayNetworkHandler net = mc.getNetworkHandler();
                PlayerListEntry e = net != null && p != null ? net.getPlayerListEntry(p.getUuid()) : null;
                return (e != null ? e.getLatency() : 0) + " ms";
            }
            case CPS:
                return LEFT_CLICKS.size() + " | " + RIGHT_CLICKS.size() + " CPS";
            case COORDS:
                return p == null ? "X 0  Y 0  Z 0" : "X " + p.getBlockX() + "  Y " + p.getBlockY() + "  Z " + p.getBlockZ();
            case DIRECTION:
                if (p == null) return "Nord";
                switch (p.getHorizontalFacing()) {
                    case NORTH: return "Nord (-Z)";
                    case SOUTH: return "Sud (+Z)";
                    case EAST: return "Est (+X)";
                    case WEST: return "Ouest (-X)";
                    default: return "";
                }
            case CLOCK:
                return LocalTime.now().format(CLOCK);
            case SERVER: {
                ServerInfo info = mc.getCurrentServerEntry();
                return info != null ? info.address : "Solo";
            }
            default:
                return null;
        }
    }

    /** Largeur / hauteur d'un module à l'écran. */
    public static int[] size(HudModule m, MinecraftClient mc) {
        if (m == HudModule.KEYSTROKES) return new int[]{KEY * 3 + GAP * 2, KEY * 2 + GAP * 3 + 10 + 14};
        String t = text(m, mc);
        if (t == null) return new int[]{0, 0};
        return new int[]{mc.textRenderer.getWidth(t) + PAD * 2, mc.textRenderer.fontHeight + PAD * 2 - 1};
    }

    /** Position réelle (bornée à l'écran). */
    public static int[] clampedPos(HudModule m, MinecraftClient mc) {
        int[] pos = HudConfig.get().position(m);
        int[] s = size(m, mc);
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        return new int[]{Math.max(0, Math.min(pos[0], w - s[0])), Math.max(0, Math.min(pos[1], h - s[1]))};
    }

    /** Dessine tous les modules actifs. */
    public static void renderAll(DrawContext ctx, MinecraftClient mc) {
        pollClicks();
        HudConfig cfg = HudConfig.get();
        for (HudModule m : HudModule.values()) {
            if (!m.movable() || !cfg.isEnabled(m)) continue;
            int[] pos = clampedPos(m, mc);
            render(ctx, mc, m, pos[0], pos[1]);
        }
    }

    public static void render(DrawContext ctx, MinecraftClient mc, HudModule m, int x, int y) {
        HudConfig cfg = HudConfig.get();
        TextRenderer tr = mc.textRenderer;
        if (m == HudModule.KEYSTROKES) {
            renderKeystrokes(ctx, mc, x, y);
            return;
        }
        String t = text(m, mc);
        if (t == null) return;
        int[] s = size(m, mc);
        if (cfg.background) ctx.fill(x, y, x + s[0], y + s[1], 0x80000000);
        ctx.drawText(tr, t, x + PAD, y + PAD, cfg.textColor(), cfg.shadow);
    }

    private static void renderKeystrokes(DrawContext ctx, MinecraftClient mc, int x, int y) {
        GameOptions o = mc.options;
        int full = KEY * 3 + GAP * 2;
        key(ctx, mc, o.forwardKey, x + KEY + GAP, y, KEY, KEY);
        key(ctx, mc, o.leftKey, x, y + KEY + GAP, KEY, KEY);
        key(ctx, mc, o.backKey, x + KEY + GAP, y + KEY + GAP, KEY, KEY);
        key(ctx, mc, o.rightKey, x + (KEY + GAP) * 2, y + KEY + GAP, KEY, KEY);
        int yRow = y + (KEY + GAP) * 2;
        int half = (full - GAP) / 2;
        box(ctx, mc, x, yRow, half, 14, o.attackKey.isPressed(), LEFT_CLICKS.size() + " G");
        box(ctx, mc, x + half + GAP, yRow, full - half - GAP, 14, o.useKey.isPressed(), RIGHT_CLICKS.size() + " D");
        box(ctx, mc, x, yRow + 14 + GAP, full, 10, o.jumpKey.isPressed(), "———");
    }

    private static void key(DrawContext ctx, MinecraftClient mc, KeyBinding k, int x, int y, int w, int h) {
        String label = k.getBoundKeyLocalizedText().getString();
        if (label.length() > 3) label = label.substring(0, 3);
        box(ctx, mc, x, y, w, h, k.isPressed(), label.toUpperCase());
    }

    private static void box(DrawContext ctx, MinecraftClient mc, int x, int y, int w, int h, boolean pressed, String label) {
        HudConfig cfg = HudConfig.get();
        ctx.fill(x, y, x + w, y + h, pressed ? 0xC0FFFFFF : 0x80000000);
        TextRenderer tr = mc.textRenderer;
        int tw = tr.getWidth(label);
        int color = pressed ? 0xFF140B12 : cfg.textColor();
        ctx.drawText(tr, label, x + (w - tw) / 2, y + (h - tr.fontHeight) / 2 + 1, color, !pressed && cfg.shadow);
    }
}
