package fr.spartacube.client.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Fond et logo SpartaCube partagés par les écrans du mod. */
public final class SpartaDraw {
    public static final Identifier BACKGROUND = Identifier.of("spartacube", "textures/gui/background.png");
    public static final Identifier LOGO = Identifier.of("spartacube", "textures/gui/logo.png");
    private static final int BG_W = 1280, BG_H = 853;
    private static final int LOGO_W = 512, LOGO_H = 306;

    private SpartaDraw() {}

    /** Fond « cover » (remplit l'écran sans déformer) + léger dégradé sombre. */
    public static void background(DrawContext ctx, int width, int height) {
        float scale = Math.max(width / (float) BG_W, height / (float) BG_H);
        int regionW = Math.round(width / scale);
        int regionH = Math.round(height / scale);
        int u = (BG_W - regionW) / 2;
        int v = (BG_H - regionH) / 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, u, v, width, height, regionW, regionH, BG_W, BG_H);
        ctx.fillGradient(0, height / 2, width, height, 0x00000000, 0xC0140B12);
    }

    /** Logo centré ; renvoie la coordonnée Y du bas du logo. */
    public static int logo(DrawContext ctx, int width, int top, int maxWidth) {
        int w = Math.min(maxWidth, LOGO_W);
        int h = w * LOGO_H / LOGO_W;
        int x = (width - w) / 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, x, top, 0, 0, w, h, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        return top + h;
    }

    /** Panneau semi-transparent avec liseré orange. */
    public static void panel(DrawContext ctx, int x1, int y1, int x2, int y2) {
        ctx.fill(x1, y1, x2, y2, 0xB0140B12);
        ctx.fill(x1, y1, x2, y1 + 1, 0xFFFF8C3C);
        ctx.fill(x1, y2 - 1, x2, y2, 0x60FF8C3C);
    }
}
