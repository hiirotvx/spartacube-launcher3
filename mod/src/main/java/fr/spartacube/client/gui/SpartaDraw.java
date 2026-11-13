package fr.spartacube.client.gui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Fond et logo SpartaCube partagés par les écrans du mod. */
public final class SpartaDraw {
    public static final Identifier BACKGROUND = Identifier.of("spartacube", "textures/gui/background.png");
    public static final Identifier LOGO = Identifier.of("spartacube", "textures/gui/logo.png");
    public static final int LOGO_W = 512, LOGO_H = 306;
    private static final int BG_W = 1280, BG_H = 853;

    private SpartaDraw() {}

    /** Fond « cover » avec léger effet de parallaxe (px, py entre -1 et 1). */
    public static void background(DrawContext ctx, int width, int height, float px, float py) {
        float scale = Math.max(width / (float) BG_W, height / (float) BG_H) * 1.06f;
        int regionW = Math.round(width / scale);
        int regionH = Math.round(height / scale);
        int maxU = BG_W - regionW, maxV = BG_H - regionH;
        int u = Math.round(maxU / 2f + px * maxU / 2f);
        int v = Math.round(maxV / 2f + py * maxV / 2f);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, u, v, width, height, regionW, regionH, BG_W, BG_H);
        ctx.fillGradient(0, 0, width, height / 3, 0x50000000, 0x00000000);
        ctx.fillGradient(0, height / 2, width, height, 0x00000000, 0xD0100810);
    }

    public static void background(DrawContext ctx, int width, int height) {
        background(ctx, width, height, 0f, 0f);
    }

    /** Logo centré horizontalement autour de cx. */
    public static void logo(DrawContext ctx, int cx, int top, int w) {
        int h = w * LOGO_H / LOGO_W;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, cx - w / 2, top, 0, 0, w, h, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
    }
}
