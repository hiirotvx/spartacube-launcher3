package fr.spartacube.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Petites briques de dessin « lisses » : rectangles arrondis, police Montserrat, icônes, animations. */
public final class Ui {
    public static final Identifier FONT = Identifier.of("spartacube", "ui");
    public static final Identifier FONT_BOLD = Identifier.of("spartacube", "ui_bold");

    public static final int ACCENT = 0xFFFF8C2E;
    public static final int ACCENT_2 = 0xFFC4122F;
    public static final int GREEN = 0xFF22A55B;
    public static final int RED = 0xFFC4253F;
    public static final int TEXT = 0xFFF5ECE6;
    public static final int MUTED = 0xFFB9A79C;

    private Ui() {}

    /* ---------- Texte ---------- */

    public static MutableText t(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT)));
    }

    public static MutableText b(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT_BOLD)));
    }

    public static void text(DrawContext ctx, Text t, int x, int y, int color) {
        ctx.drawText(MinecraftClient.getInstance().textRenderer, t, x, y, color, false);
    }

    public static void centered(DrawContext ctx, Text t, int cx, int y, int color) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawText(tr, t, cx - tr.getWidth(t) / 2, y, color, false);
    }

    public static int width(Text t) {
        return MinecraftClient.getInstance().textRenderer.getWidth(t);
    }

    /* ---------- Couleurs & animation ---------- */

    /** Multiplie l'alpha d'une couleur ARGB. */
    public static int alpha(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (argb & 0x00FFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
        int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
        int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
        int bl = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (float) Math.pow(1f - t, 3);
    }

    /** Rapproche doucement une valeur de sa cible (animation indépendante des FPS). */
    public static float approach(float current, float target, float speedPerSecond, float dtSeconds) {
        float k = 1f - (float) Math.exp(-speedPerSecond * dtSeconds);
        return current + (target - current) * k;
    }

    /* ---------- Formes ---------- */

    /** Rectangle aux coins arrondis (rayon en pixels d'interface). */
    public static void round(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1 || (color >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) { ctx.fill(x1, y1, x2, y2, color); return; }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** Contour arrondi d'1 px. */
    public static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        ctx.fill(x1 + r, y1, x2 - r, y1 + 1, color);
        ctx.fill(x1 + r, y2 - 1, x2 - r, y2, color);
        ctx.fill(x1, y1 + r, x1 + 1, y2 - r, color);
        ctx.fill(x2 - 1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x1 + inset, y1 + i, x1 + inset + 1, y1 + i + 1, color);
            ctx.fill(x2 - inset - 1, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x1 + inset + 1, y2 - i, color);
            ctx.fill(x2 - inset - 1, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** Icône blanche 64×64 dessinée à la taille voulue. */
    public static void icon(DrawContext ctx, String name, int x, int y, int size) {
        Identifier id = Identifier.of("spartacube", "textures/gui/icons/" + name + ".png");
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, size, size, 64, 64, 64, 64);
    }

    public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    /* ---------- Souris (lue directement, indépendante des versions) ---------- */

    public static final class Mouse {
        private boolean wasDown;
        public boolean down;
        public boolean pressed;
        public boolean released;

        /** À appeler une fois par image. */
        public void update() {
            long w = GLFW.glfwGetCurrentContext();
            boolean d = w != 0L && GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            this.pressed = d && !this.wasDown;
            this.released = !d && this.wasDown;
            this.down = d;
            this.wasDown = d;
        }

        /** Ignore le clic en cours (ex. le clic qui a ouvert l'écran). */
        public void swallow() {
            long w = GLFW.glfwGetCurrentContext();
            this.wasDown = w != 0L && GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        }
    }

    public static void click() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
