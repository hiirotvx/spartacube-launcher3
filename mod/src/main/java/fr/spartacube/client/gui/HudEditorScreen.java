package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudConfig.ModuleSettings;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Éditeur d'HUD : glisser pour déplacer, molette pour redimensionner, guides d'alignement. */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 4;

    private final Screen parent;
    private final Ui.Mouse mouse = new Ui.Mouse();
    private HudModule dragging;
    private HudModule hovered;
    private int offX, offY;
    private long openedAt, lastFrame;
    private float doneHover;
    private int guideX = -1, guideY = -1;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Éditer l'HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.openedAt == 0) this.openedAt = Util.getMeasuringTimeMs();
        this.mouse.swallow();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.hovered != null) {
            ModuleSettings st = HudConfig.get().settings(this.hovered);
            st.scale = Math.max(0.5f, Math.min(2.0f, Math.round((st.scale + (float) verticalAmount * 0.1f) * 20f) / 20f));
            HudConfig.get().save();
            return true;
        }
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // fond dessiné dans render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = this.lastFrame == 0 ? 0f : Math.min(0.1f, (now - this.lastFrame) / 1000f);
        this.lastFrame = now;
        float open = Ui.easeOut((now - this.openedAt) / 250f);
        this.mouse.update();

        if (this.client.world == null) SpartaDraw.background(ctx, this.width, this.height);
        ctx.fill(0, 0, this.width, this.height, Ui.alpha(0x60000000, open));
        int cx = this.width / 2, cy = this.height / 2;
        ctx.fill(cx, 0, cx + 1, this.height, Ui.alpha(0x18FFFFFF, open));
        ctx.fill(0, cy, this.width, cy + 1, Ui.alpha(0x18FFFFFF, open));

        HudConfig cfg = HudConfig.get();
        HudRenderer.pollClicks();

        // Bouton Terminé
        int bw = 90, bx = cx - bw / 2, by = this.height - 34;
        boolean overDone = Ui.inside(mouseX, mouseY, bx, by, bx + bw, by + 20);

        this.hovered = null;
        if (this.dragging == null && !overDone) {
            for (HudModule m : HudModule.values()) {
                if (!m.movable() || !cfg.isEnabled(m)) continue;
                int[] p = HudRenderer.clampedPos(m, this.client);
                int[] s = HudRenderer.size(m, this.client);
                if (Ui.inside(mouseX, mouseY, p[0] - 6, p[1] - 8, p[0] + s[0] + 8, p[1] + s[1] + 4)) this.hovered = m;
            }
        }

        if (this.mouse.pressed && this.hovered != null && overClose(this.hovered, mouseX, mouseY)) {
            Ui.click();
            cfg.toggle(this.hovered);
            this.hovered = null;
        }
        if (this.mouse.pressed && this.hovered != null) {
            int[] p = HudRenderer.clampedPos(this.hovered, this.client);
            this.dragging = this.hovered;
            this.offX = mouseX - p[0];
            this.offY = mouseY - p[1];
        }
        this.guideX = -1;
        this.guideY = -1;
        if (this.mouse.down && this.dragging != null) {
            int[] s = HudRenderer.size(this.dragging, this.client);
            int nx = mouseX - this.offX, ny = mouseY - this.offY;
            // Aimantation : bords de l'écran et centre
            if (Math.abs(nx) < SNAP) nx = 0;
            if (Math.abs(ny) < SNAP) ny = 0;
            if (Math.abs(nx + s[0] - this.width) < SNAP) nx = this.width - s[0];
            if (Math.abs(ny + s[1] - this.height) < SNAP) ny = this.height - s[1];
            if (Math.abs(nx + s[0] / 2 - cx) < SNAP) { nx = cx - s[0] / 2; this.guideX = cx; }
            if (Math.abs(ny + s[1] / 2 - cy) < SNAP) { ny = cy - s[1] / 2; this.guideY = cy; }
            cfg.setPosition(this.dragging, Math.max(0, nx), Math.max(0, ny));
        }
        if (this.mouse.released && this.dragging != null) {
            this.dragging = null;
            cfg.save();
        }

        if (this.guideX >= 0) ctx.fill(this.guideX, 0, this.guideX + 1, this.height, 0xC0FF8C2E);
        if (this.guideY >= 0) ctx.fill(0, this.guideY, this.width, this.guideY + 1, 0xC0FF8C2E);

        for (HudModule m : HudModule.values()) {
            if (!m.movable() || !cfg.isEnabled(m)) continue;
            int[] p = HudRenderer.clampedPos(m, this.client);
            int[] s = HudRenderer.size(m, this.client);
            boolean active = m == this.dragging || m == this.hovered;
            Ui.round(ctx, p[0] - 2, p[1] - 2, p[0] + s[0] + 2, p[1] + s[1] + 2, 3, active ? 0x30FF8C2E : 0x18FFFFFF);
            HudRenderer.render(ctx, this.client, m, p[0], p[1]);
            Ui.outline(ctx, p[0] - 2, p[1] - 2, p[0] + s[0] + 2, p[1] + s[1] + 2, 3, active ? 0xFFFF8C2E : 0x50FFFFFF);
            if (m == this.hovered && this.dragging == null) {
                int[] c = closeRect(m);
                boolean overX = overClose(m, mouseX, mouseY);
                Ui.round(ctx, c[0], c[1], c[0] + 11, c[1] + 11, 5, overX ? 0xFFE0324F : 0xE0C4253F);
                Ui.icon(ctx, "close", c[0] + 3, c[1] + 3, 5);
            }
            if (active) {
                Text label = Ui.t(m.label + "  " + Math.round(cfg.settings(m).scale * 100) + " %");
                int lw = Ui.width(label) + 8;
                int ly = p[1] > 16 ? p[1] - 15 : p[1] + s[1] + 4;
                Ui.round(ctx, p[0] - 2, ly, p[0] - 2 + lw, ly + 12, 3, 0xE0140B12);
                Ui.text(ctx, label, p[0] + 2, ly + 2, Ui.ACCENT);
            }
        }

        // Aide + bouton Terminé
        Text help = Ui.t("Glisse pour déplacer  ·  Molette : taille  ·  ✕ : désactiver  ·  Échap pour terminer");
        int hw = Ui.width(help) + 16;
        Ui.round(ctx, cx - hw / 2, 8, cx + hw / 2, 24, 6, Ui.alpha(0xD0140B12, open));
        Ui.centered(ctx, help, cx, 12, Ui.alpha(Ui.TEXT, open));
        this.doneHover = Ui.approach(this.doneHover, overDone ? 1f : 0f, 14f, dt);
        Ui.round(ctx, bx, by, bx + bw, by + 20, 6, Ui.alpha(Ui.mix(0xFFE8731C, 0xFFFF9A3C, this.doneHover), open));
        Ui.centered(ctx, Ui.b("Terminé"), cx, by + 6, 0xFFFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
        if (this.mouse.released && overDone) { Ui.click(); this.close(); }
    }

    /** Croix « désactiver » en haut à droite du module. */
    private int[] closeRect(HudModule m) {
        int[] p = HudRenderer.clampedPos(m, this.client);
        int[] s = HudRenderer.size(m, this.client);
        return new int[]{p[0] + s[0] - 5, p[1] - 7};
    }

    private boolean overClose(HudModule m, int mx, int my) {
        int[] c = closeRect(m);
        return Ui.inside(mx, my, c[0] - 1, c[1] - 1, c[0] + 12, c[1] + 12);
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
