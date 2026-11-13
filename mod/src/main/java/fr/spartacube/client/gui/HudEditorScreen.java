package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Éditeur de position : on fait glisser les modules à la souris. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule dragging;
    private int offX, offY;
    private boolean wasDown;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Déplacer l'HUD"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // fond géré dans render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client.world == null) SpartaDraw.background(ctx, this.width, this.height);
        else ctx.fill(0, 0, this.width, this.height, 0x50000000);

        // Grille d'aide
        for (int gx = 0; gx < this.width; gx += 20) ctx.fill(gx, 0, gx + 1, this.height, 0x14FFFFFF);
        for (int gy = 0; gy < this.height; gy += 20) ctx.fill(0, gy, this.width, gy + 1, 0x14FFFFFF);

        HudConfig cfg = HudConfig.get();
        long window = GLFW.glfwGetCurrentContext();
        boolean down = window != 0L && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        HudRenderer.pollClicks();
        HudModule hovered = null;
        for (HudModule m : HudModule.values()) {
            if (!m.movable() || !cfg.isEnabled(m)) continue;
            int[] p = HudRenderer.clampedPos(m, this.client);
            int[] s = HudRenderer.size(m, this.client);
            boolean over = mouseX >= p[0] && mouseX < p[0] + s[0] && mouseY >= p[1] && mouseY < p[1] + s[1];
            if (over) hovered = m;
            HudRenderer.render(ctx, this.client, m, p[0], p[1]);
            int border = m == this.dragging ? 0xFFFFB02E : over ? 0xC0FFB02E : 0x60FFFFFF;
            ctx.fill(p[0] - 1, p[1] - 1, p[0] + s[0] + 1, p[1], border);
            ctx.fill(p[0] - 1, p[1] + s[1], p[0] + s[0] + 1, p[1] + s[1] + 1, border);
            ctx.fill(p[0] - 1, p[1], p[0], p[1] + s[1], border);
            ctx.fill(p[0] + s[0], p[1], p[0] + s[0] + 1, p[1] + s[1], border);
        }

        if (down && !this.wasDown && hovered != null) {
            int[] p = HudRenderer.clampedPos(hovered, this.client);
            this.dragging = hovered;
            this.offX = mouseX - p[0];
            this.offY = mouseY - p[1];
        }
        if (down && this.dragging != null) {
            int nx = mouseX - this.offX;
            int ny = mouseY - this.offY;
            // aimantation à 2 px des bords
            if (nx < 4) nx = 2;
            if (ny < 4) ny = 2;
            cfg.setPosition(this.dragging, nx, ny);
        }
        if (!down && this.dragging != null) {
            this.dragging = null;
            cfg.save();
        }
        this.wasDown = down;

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Fais glisser les modules avec la souris · Échap pour valider"),
                this.width / 2, this.height / 2 - 4, 0xFFFFB02E);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        HudConfig.get().save();
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
