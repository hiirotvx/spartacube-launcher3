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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
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
            case SATURATION: {
                float sat = p == null ? 5f : p.getHungerManager().getSaturationLevel();
                int food = p == null ? 18 : p.getHungerManager().getFoodLevel();
                String v = String.format(java.util.Locale.ROOT, "%.1f", sat);
                return HudConfig.get().settings(HudModule.SATURATION).mode == 1 ? "Faim " + food + "/20 · Sat " + v : "Saturation " + v;
            }
            case DAY: return "Jour " + (mc.world == null ? 1 : mc.world.getTimeOfDay() / 24000L + 1);
            default: return null;
        }
    }

    /* ---------- Effets de potion : 0 icône + nom + durée, 1 icône + durée, 2 texte seul ---------- */

    private record Fx(RegistryEntry<StatusEffect> type, String name, String time, boolean good, boolean ending) {}

    private static List<Fx> effects(MinecraftClient mc) {
        List<Fx> out = new ArrayList<>();
        if (mc.player == null) {
            out.add(new Fx(StatusEffects.STRENGTH, "Force II", "1:30", true, false));
            out.add(new Fx(StatusEffects.POISON, "Poison", "0:08", false, true));
            return out;
        }
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            String name = e.getEffectType().value().getName().getString();
            int amp = e.getAmplifier() + 1;
            String lvl = amp < ROMAN.length ? ROMAN[amp] : " " + amp;
            String time;
            boolean ending = false;
            if (e.isInfinite()) time = "∞";
            else {
                int sec = e.getDuration() / 20;
                time = (sec / 60) + ":" + String.format("%02d", sec % 60);
                ending = sec < 10;
            }
            out.add(new Fx(e.getEffectType(), name + lvl, time, e.getEffectType().value().isBeneficial(), ending));
        }
        return out;
    }

    private static String fxLabel(Fx fx, int mode) {
        return mode == 1 ? fx.time() : fx.name() + "  " + fx.time();
    }

    private static int[] effectsSize(MinecraftClient mc, ModuleSettings st) {
        List<Fx> list = effects(mc);
        if (list.isEmpty()) return new int[]{0, 0};
        int icon = st.mode == 2 ? 0 : 12;
        if (st.horizontal) {
            int w = PAD;
            for (Fx fx : list) w += icon + mc.textRenderer.getWidth(fxLabel(fx, st.mode)) + 8;
            return new int[]{w + PAD - 8, 11 + PAD * 2 - 2};
        }
        int w = 0;
        for (Fx fx : list) w = Math.max(w, mc.textRenderer.getWidth(fxLabel(fx, st.mode)));
        return new int[]{icon + w + PAD * 2, list.size() * 11 + PAD * 2 - 2};
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
            case ARMOR: return armorSize(mc, HudConfig.get().settings(HudModule.ARMOR));
            case EFFECTS: return effectsSize(mc, HudConfig.get().settings(HudModule.EFFECTS));
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
        List<Fx> list = effects(mc);
        if (list.isEmpty()) return;
        int[] s = effectsSize(mc, st);
        if (st.background) Ui.round(ctx, 0, 0, s[0], s[1], 3, st.bgColor());
        int icon = st.mode == 2 ? 0 : 12;
        boolean blink = (System.currentTimeMillis() / 400L) % 2 == 0;
        int x = PAD, y = PAD;
        for (Fx fx : list) {
            int color;
            if (st.stateColors) color = fx.ending() ? (blink ? 0xFFFF5555 : 0xFFFFB4B4) : fx.good() ? 0xFF7BE0A0 : 0xFFFF7B7B;
            else color = st.textColor(x + y);
            if (icon > 0) ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, InGameHud.getEffectTexture(fx.type()), x, y - 1, 10, 10);
            String label = fxLabel(fx, st.mode);
            ctx.drawText(mc.textRenderer, label, x + icon, y, color, st.shadow);
            if (st.horizontal) x += icon + mc.textRenderer.getWidth(label) + 8;
            else y += 11;
        }
    }

    /* ---------- Armure : 0 icônes, 1 pourcentage, 2 barre, 3 durabilité restante ---------- */

    private static List<ItemStack> armorItems(MinecraftClient mc) {
        List<ItemStack> out = new ArrayList<>();
        ClientPlayerEntity p = mc.player;
        if (p == null) {
            // Aperçu dans l'éditeur hors partie
            out.add(new ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
            out.add(new ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
            out.add(new ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
            out.add(new ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
            out.add(new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
            return out;
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) out.add(p.getEquippedStack(slot));
        out.add(p.getMainHandStack());
        return out;
    }

    private static String armorLabel(ItemStack it, int mode) {
        if (it.isEmpty()) return "";
        if (!it.isDamageable()) return it.getCount() > 1 ? String.valueOf(it.getCount()) : "";
        int max = it.getMaxDamage(), left = max - it.getDamage();
        if (mode == 1) return Math.round(left * 100f / max) + "%";
        if (mode == 3) return String.valueOf(left);
        return "";
    }

    private static int durabilityColor(ItemStack it) {
        if (!it.isDamageable()) return 0xFFFFFFFF;
        float f = (it.getMaxDamage() - it.getDamage()) / (float) it.getMaxDamage();
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(f / 3f, 0.85f, 1f) & 0xFFFFFF);
    }

    private static int armorTextW(MinecraftClient mc, ModuleSettings st) {
        int w = 0;
        for (ItemStack it : armorItems(mc)) w = Math.max(w, mc.textRenderer.getWidth(armorLabel(it, st.mode)));
        return w;
    }

    private static int[] armorSize(MinecraftClient mc, ModuleSettings st) {
        int n = 5;
        int tw = (st.mode == 1 || st.mode == 3) ? armorTextW(mc, st) : 0;
        if (st.horizontal) {
            int cell = Math.max(18, tw + 4);
            int h = 18 + (st.mode == 2 ? 4 : tw > 0 ? 10 : 0);
            return new int[]{cell * n + 2, h + 2};
        }
        int w = 20 + (st.mode == 2 ? 28 : tw > 0 ? tw + 4 : 0);
        return new int[]{w, n * 17 + 2};
    }

    private static void renderArmor(DrawContext ctx, MinecraftClient mc, ModuleSettings st) {
        int[] s = armorSize(mc, st);
        if (st.background) Ui.round(ctx, 0, 0, s[0], s[1], 3, st.bgColor());
        List<ItemStack> items = armorItems(mc);
        int tw = (st.mode == 1 || st.mode == 3) ? armorTextW(mc, st) : 0;
        int cell = Math.max(18, tw + 4);
        for (int i = 0; i < items.size(); i++) {
            ItemStack it = items.get(i);
            int x = st.horizontal ? 1 + i * cell + (cell - 16) / 2 : 2;
            int y = st.horizontal ? 2 : 2 + i * 17;
            if (it.isEmpty()) continue;
            ctx.drawItem(it, x, y);
            // Icônes seules : barre vanilla ; sinon on n'affiche que le nombre d'objets
            if (st.mode == 0 || !it.isDamageable()) ctx.drawStackOverlay(mc.textRenderer, it, x, y);
            if (!it.isDamageable()) continue;
            int col = st.chroma || st.customColor ? st.textColor(x) : durabilityColor(it);
            float f = (it.getMaxDamage() - it.getDamage()) / (float) it.getMaxDamage();
            if (st.mode == 2) {
                int bx = st.horizontal ? x : 20, by = st.horizontal ? 19 : y + 6;
                int bw = st.horizontal ? 16 : 26;
                Ui.round(ctx, bx, by, bx + bw, by + 3, 1, 0x60000000);
                Ui.round(ctx, bx, by, bx + Math.max(1, Math.round(bw * f)), by + 3, 1, col);
            } else if (st.mode == 1 || st.mode == 3) {
                String label = armorLabel(it, st.mode);
                int lw = mc.textRenderer.getWidth(label);
                int tx = st.horizontal ? 1 + i * cell + (cell - lw) / 2 : 20;
                int ty = st.horizontal ? 19 : y + 4;
                ctx.drawText(mc.textRenderer, label, tx, ty, col, st.shadow);
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
