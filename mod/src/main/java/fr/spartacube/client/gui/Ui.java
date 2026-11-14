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

    /** Couleurs du thème (modifiables : voir applyTheme). */
    public static int ACCENT = 0xFFFF8C2E;
    public static int ACCENT_DARK = 0xFFE8731C;
    public static int ACCENT_LIGHT = 0xFFFF9A3C;
    /** Couleur de fond des panneaux (RVB, sans alpha). */
    public static int PANEL = 0x140B12;
    public static final int ACCENT_2 = 0xFFC4122F;

    public static final String[] THEME_NAMES = {"SpartaCube", "Braise douce", "Ardoise", "Océan", "Émeraude", "Lavande", "Rose", "Personnalisé"};
    private static final int[][] THEMES = {
            {0xFF8C2E, 0x140B12}, {0xE0A27A, 0x17120F}, {0x9FB0C8, 0x12141A}, {0x58A6F0, 0x0D1420},
            {0x4CC38A, 0x0D1612}, {0xA994F5, 0x14101F}, {0xF07BB0, 0x1A1016}
    };
    public static final int THEME_CUSTOM = THEMES.length;

    /** Recalcule les couleurs du menu depuis les réglages (appelé à chaque image des écrans SpartaCube). */
    public static void applyTheme() {
        fr.spartacube.client.hud.HudConfig cfg = fr.spartacube.client.hud.HudConfig.get();
        int preset = Math.min(cfg.themePreset, THEME_CUSTOM);
        int acc, panel;
        if (cfg.themeChroma) {
            float h = (System.currentTimeMillis() % 20000L) / 20000f;
            acc = java.awt.Color.HSBtoRGB(h, 0.6f, 1f) & 0xFFFFFF;
            panel = 0x121318;
        } else if (preset == THEME_CUSTOM) {
            acc = java.awt.Color.HSBtoRGB(cfg.themeHue, cfg.themeSat, 1f) & 0xFFFFFF;
            panel = mix(0xFF121318, 0xFF000000 | acc, 0.05f) & 0xFFFFFF;
        } else {
            acc = THEMES[preset][0];
            panel = THEMES[preset][1];
        }
        if (cfg.themeNeutral) panel = 0x121318;
        ACCENT = 0xFF000000 | acc;
        if (preset == 0 && !cfg.themeChroma) {
            ACCENT_DARK = 0xFFE8731C;
            ACCENT_LIGHT = 0xFFFF9A3C;
        } else {
            ACCENT_DARK = mix(ACCENT, 0xFF000000, 0.14f);
            ACCENT_LIGHT = mix(ACCENT, 0xFFFFFFFF, 0.16f);
        }
        PANEL = panel;
    }

    /** Couleur d'un préréglage (pour l'aperçu dans le sélecteur). */
    public static int themeColor(int preset) {
        return 0xFF000000 | THEMES[Math.max(0, Math.min(THEMES.length - 1, preset))][0];
    }

    /** Couleur d'accent avec l'alpha donné (0 à 255). */
    public static int accent(int a) {
        return (a << 24) | (ACCENT & 0xFFFFFF);
    }

    /** Fond de panneau avec l'alpha donné (0 à 255). */
    public static int panel(int a) {
        return (a << 24) | PANEL;
    }
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

    private static final Identifier CIRCLE = Identifier.of("spartacube", "textures/gui/shape/circle.png");
    private static final int[] RING_SIZES = {3, 4, 5, 6, 7, 8, 10};

    /** Quart de disque/anneau lissé (texture filtrée), teinté. q : 0=haut-gauche 1=haut-droit 2=bas-gauche 3=bas-droit. */
    private static void corner(DrawContext ctx, Identifier tex, int texSize, int x, int y, int r, int q, int color) {
        int half = texSize / 2;
        int u = (q == 1 || q == 3) ? half : 0;
        int v = (q == 2 || q == 3) ? half : 0;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, r, r, half, half, texSize, texSize, color);
    }

    /** Rectangle aux coins arrondis et lissés. */
    public static void round(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1 || (color >>> 24) == 0) return;
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        if (r == 0) { ctx.fill(x1, y1, x2, y2, color); return; }
        ctx.fill(x1 + r, y1, x2 - r, y2, color);
        ctx.fill(x1, y1 + r, x1 + r, y2 - r, color);
        ctx.fill(x2 - r, y1 + r, x2, y2 - r, color);
        corner(ctx, CIRCLE, 64, x1, y1, r, 0, color);
        corner(ctx, CIRCLE, 64, x2 - r, y1, r, 1, color);
        corner(ctx, CIRCLE, 64, x1, y2 - r, r, 2, color);
        corner(ctx, CIRCLE, 64, x2 - r, y2 - r, r, 3, color);
    }

    /** Contour arrondi et lissé (1 unité d'interface). */
    public static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        int rr = RING_SIZES[0];
        for (int s : RING_SIZES) if (s <= r) rr = s;
        if (r < 3) rr = 0;
        r = rr;
        ctx.fill(x1 + r, y1, x2 - r, y1 + 1, color);
        ctx.fill(x1 + r, y2 - 1, x2 - r, y2, color);
        ctx.fill(x1, y1 + r, x1 + 1, y2 - r, color);
        ctx.fill(x2 - 1, y1 + r, x2, y2 - r, color);
        if (r == 0) return;
        Identifier ring = Identifier.of("spartacube", "textures/gui/shape/ring" + r + ".png");
        int ts = 2 * r * 8;
        corner(ctx, ring, ts, x1, y1, r, 0, color);
        corner(ctx, ring, ts, x2 - r, y1, r, 1, color);
        corner(ctx, ring, ts, x1, y2 - r, r, 2, color);
        corner(ctx, ring, ts, x2 - r, y2 - r, r, 3, color);
    }

    /** Icône blanche (48 px, lissée) dessinée à la taille voulue. */
    public static void icon(DrawContext ctx, String name, int x, int y, int size) {
        icon(ctx, name, x, y, size, 0xFFFFFFFF);
    }

    public static void icon(DrawContext ctx, String name, int x, int y, int size, int color) {
        Identifier id = Identifier.of("spartacube", "textures/gui/icons/" + name + ".png");
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, size, size, 48, 48, 48, 48, color);
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
