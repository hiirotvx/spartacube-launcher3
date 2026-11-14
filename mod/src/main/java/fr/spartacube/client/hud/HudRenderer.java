package fr.spartacube.client.hud;

import fr.spartacube.client.gui.Ui;
import fr.spartacube.client.hud.HudConfig.ModuleSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Dessin des modules d'HUD (en jeu et dans l'éditeur de position). */
public final class HudRenderer {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int PAD = 3;
    private static final int KEY = 20;
    private static final int GAP = 2;
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static boolean leftWasDown, rightWasDown;

    private static double lastX, lastZ;
    private static double speed;

    private HudRenderer() {}

    /* ---------- Mesures mises à jour en continu ---------- */

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

    /** Tick client : vitesse du joueur. */
    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        double x = mc.player.getX(), z = mc.player.getZ();
        double inst = Math.hypot(x - lastX, z - lastZ) * 20.0;
        speed = inst > 200 ? 0 : speed * 0.6 + inst * 0.4;
        lastX = x;
        lastZ = z;
    }

    /* ---------- Contenu ---------- */

    private static String prettify(String id) {
        String s = id.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String text(HudModule m, MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        switch (m) {
            case FPS: return mc.getCurrentFps() + " FPS";
            case PING: {
                ClientPlayNetworkHandler net = mc.getNetworkHandler();
                PlayerListEntry e = net != null && p != null ? net.getPlayerListEntry(p.getUuid()) : null;
                return (e != null ? e.getLatency() : 0) + " ms";
            }
            case CPS: return LEFT_CLICKS.size() + " | " + RIGHT_CLICKS.size() + " CPS";
            case COORDS: return p == null ? "X 0  Y 64  Z 0" : "X " + p.getBlockX() + "  Y " + p.getBlockY() + "  Z " + p.getBlockZ();
            case DIRECTION:
                if (p == null) return "Nord (-Z)";
                switch (p.getHorizontalFacing()) {
                    case NORTH: return "Nord (-Z)";
                    case SOUTH: return "Sud (+Z)";
                    case EAST: return "Est (+X)";
                    case WEST: return "Ouest (-X)";
                    default: return "";
                }
            case CLOCK: return LocalTime.now().format(CLOCK);
            case SERVER: {
                ServerInfo info = mc.getCurrentServerEntry();
                return info != null ? info.address : "Solo";
            }
            case MEMORY: {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
                return used + " / " + rt.maxMemory() / 1048576L + " Mo";
            }
            case SPEED: return String.format("%.1f b/s", speed);
            case BIOME:
                if (p == null || mc.world == null) return "Plaines";
                return mc.world.getBiome(p.getBlockPos()).getKey().map(k -> prettify(k.getValue().getPath())).orElse("?");
            case DAY: return "Jour " + (mc.world == null ? 1 : mc.world.getTimeOfDay() / 24000L + 1);
            default: return null;
        }
    }

    private static List<String> effectLines(MinecraftClient mc) {
        List<String> out = new ArrayList<>();
        if (mc.player == null) {
            out.add("Force II  1:30");
            out.add("Vitesse  3:00");
            return out;
        }
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString();
            int amp = e.getAmplifier() + 1;
            String lvl = amp < ROMAN.length ? ROMAN[amp] : " " + amp;
            String time;
            if (e.isInfinite()) time = "∞";
            else {
                int s = e.getDuration() / 20;
                time = (s / 60) + ":" + String.format("%02d", s % 60);
            }
            out.add(name + lvl + "  " + time);
        }
        return out;
    }

    /** Texte affiché (avec crochets si l'option est activée). */
    private static String display(HudModule m, MinecraftClient mc) {
        String t = text(m, mc);
        if (t == null) return null;
        return HudConfig.get().settings(m).brackets ? "[" + t + "]" : t;
    }

    /** Taille non mise à l'échelle. */
    private static int[] baseSize(HudModule m, MinecraftClient mc) {
        TextRenderer tr = mc.textRenderer;
        switch (m) {
            case KEYSTROKES: return new int[]{KEY * 3 + GAP * 2, KEY * 2 + GAP * 3 + 10 + 14};
            case ARMOR: return new int[]{20, 5 * 17 + 2};
            case EFFECTS: {
                List<String> lines = effectLines(mc);
                int w = 0;
                for (String l : lines) w = Math.max(w, tr.getWidth(l));
                return new int[]{lines.isEmpty() ? 0 : w + PAD * 2, lines.isEmpty() ? 0 : lines.size() * 11 + PAD * 2 - 2};
            }
            default: {
                String t = display(m, mc);
                if (t == null) return new int[]{0, 0};
                return new int[]{tr.getWidth(t) + PAD * 2, tr.fontHeight + PAD * 2 - 1};
            }
        }
    }

    public static int[] size(HudModule m, MinecraftClient mc) {
        int[] s = baseSize(m, mc);
        float k = HudConfig.get().settings(m).scale;
        return new int[]{(int) Math.ceil(s[0] * k), (int) Math.ceil(s[1] * k)};
    }

    /** Position réelle à l'écran (les valeurs négatives partent du bord droit / bas). */
    public static int[] clampedPos(HudModule m, MinecraftClient mc) {
        ModuleSettings st = HudConfig.get().settings(m);
        int[] s = size(m, mc);
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int x = st.x < 0 ? w - s[0] + st.x + 4 : st.x;
        int y = st.y < 0 ? h - s[1] + st.y + 4 : st.y;
        return new int[]{Math.max(0, Math.min(x, w - s[0])), Math.max(0, Math.min(y, h - s[1]))};
    }

    /* ---------- Dessin ---------- */

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
        ModuleSettings st = HudConfig.get().settings(m);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(st.scale, st.scale);
        switch (m) {
            case KEYSTROKES -> renderKeystrokes(ctx, mc, st);
            case ARMOR -> renderArmor(ctx, mc, st);
            case EFFECTS -> renderEffects(ctx, mc, st);
            default -> {
                String t = display(m, mc);
                if (t != null) {
                    int[] s = baseSize(m, mc);
                    if (st.background) Ui.round(ctx, 0, 0, s[0], s[1], 3, st.bgColor());
                    if (st.chroma) {
                        // Arc-en-ciel qui ondule lettre par lettre
                        int cx = PAD;
                        for (int i = 0; i < t.length(); ) {
                            int cp = t.codePointAt(i);
                            String ch = new String(Character.toChars(cp));
                            ctx.drawText(mc.textRenderer, ch, cx, PAD, st.textColor(x + cx), st.shadow);
                            cx += mc.textRenderer.getWidth(ch);
                            i += Character.charCount(cp);
                        }
                    } else {
                        ctx.drawText(mc.textRenderer, t, PAD, PAD, st.textColor(), st.shadow);
                    }
                }
            }
        }
        ctx.getMatrices().popMatrix();
    }

    private static void renderEffects(DrawContext ctx, MinecraftClient mc, ModuleSettings st) {
        List<String> lines = effectLines(mc);
        if (lines.isEmpty()) return;
        int[] s = baseSize(HudModule.EFFECTS, mc);
        if (st.background) Ui.round(ctx, 0, 0, s[0], s[1], 3, st.bgColor());
        int y = PAD;
        for (String l : lines) {
            ctx.drawText(mc.textRenderer, l, PAD, y, st.textColor(), st.shadow);
            y += 11;
        }
    }

    private static void renderArmor(DrawContext ctx, MinecraftClient mc, ModuleSettings st) {
        int[] s = baseSize(HudModule.ARMOR, mc);
        if (st.background) Ui.round(ctx, 0, 0, s[0], s[1], 3, st.bgColor());
        ClientPlayerEntity p = mc.player;
        int y = 2;
        if (p != null) {
            ItemStack hand = p.getMainHandStack();
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack it = p.getEquippedStack(slot);
                if (!it.isEmpty()) {
                    ctx.drawItem(it, 2, y);
                    ctx.drawStackOverlay(mc.textRenderer, it, 2, y);
                }
                y += 17;
            }
            if (!hand.isEmpty()) {
                ctx.drawItem(hand, 2, y);
                ctx.drawStackOverlay(mc.textRenderer, hand, 2, y);
            }
        }
    }

    private static void renderKeystrokes(DrawContext ctx, MinecraftClient mc, ModuleSettings st) {
        GameOptions o = mc.options;
        int full = KEY * 3 + GAP * 2;
        key(ctx, mc, st, o.forwardKey, KEY + GAP, 0, KEY, KEY);
        key(ctx, mc, st, o.leftKey, 0, KEY + GAP, KEY, KEY);
        key(ctx, mc, st, o.backKey, KEY + GAP, KEY + GAP, KEY, KEY);
        key(ctx, mc, st, o.rightKey, (KEY + GAP) * 2, KEY + GAP, KEY, KEY);
        int yRow = (KEY + GAP) * 2;
        int half = (full - GAP) / 2;
        box(ctx, mc, st, 0, yRow, half, 14, o.attackKey.isPressed(), LEFT_CLICKS.size() + " CPS");
        box(ctx, mc, st, half + GAP, yRow, full - half - GAP, 14, o.useKey.isPressed(), RIGHT_CLICKS.size() + " CPS");
        box(ctx, mc, st, 0, yRow + 14 + GAP, full, 10, o.jumpKey.isPressed(), "▬▬▬");
    }

    private static void key(DrawContext ctx, MinecraftClient mc, ModuleSettings st, KeyBinding k, int x, int y, int w, int h) {
        String label = k.getBoundKeyLocalizedText().getString();
        if (label.length() > 3) label = label.substring(0, 3);
        box(ctx, mc, st, x, y, w, h, k.isPressed(), label.toUpperCase());
    }

    private static void box(DrawContext ctx, MinecraftClient mc, ModuleSettings st, int x, int y, int w, int h, boolean pressed, String label) {
        Ui.round(ctx, x, y, x + w, y + h, 3, pressed ? 0xD0FFFFFF : (st.background ? st.bgColor() : 0x30000000));
        TextRenderer tr = mc.textRenderer;
        int tw = tr.getWidth(label);
        int color = pressed ? 0xFF140B12 : st.textColor();
        ctx.drawText(tr, label, x + (w - tw) / 2, y + (h - tr.fontHeight) / 2 + 1, color, !pressed && st.shadow);
    }
}
