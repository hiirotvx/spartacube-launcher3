package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.Keys;
import fr.spartacube.client.hud.Macros;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Macros : associe une touche à une commande ou à un message. */
public class MacrosScreen extends Screen {
    private static final int ROW = 24;
    private final Screen parent;
    private final Ui.Mouse mouse = new Ui.Mouse();
    private int capturing = -1;
    private boolean armed;

    public MacrosScreen(Screen parent) {
        super(Text.literal("Macros"));
        this.parent = parent;
    }

    private int panelW() { return Math.min(380, this.width - 20); }
    private int px1() { return (this.width - panelW()) / 2; }
    private int py1() { return Math.max(8, this.height / 2 - (60 + Macros.MAX * ROW / 2)); }
    private int rowY(int i) { return py1() + 44 + i * ROW; }

    @Override
    protected void init() {
        this.mouse.swallow();
        rebuild();
    }

    private void rebuild() {
        this.clearChildren();
        HudConfig cfg = HudConfig.get();
        int x = px1() + 98, w = panelW() - 98 - 34;
        for (int i = 0; i < cfg.macros.size(); i++) {
            HudConfig.Macro m = cfg.macros.get(i);
            TextFieldWidget f = new TextFieldWidget(this.textRenderer, x + 7, rowY(i) + 6, w - 14, 12, Text.literal("Commande"));
            f.setDrawsBackground(false);
            f.setPlaceholder(Ui.t("/spawn, /home, un message…").styled(st -> st.withColor(0x80808A)));
            f.setMaxLength(200);
            f.setText(m.command == null ? "" : m.command);
            f.setChangedListener(s -> m.command = s);
            this.addDrawableChild(f);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world != null) this.applyBlur(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.applyTheme();
        HudConfig cfg = HudConfig.get();
        this.mouse.update();
        boolean click = this.mouse.pressed;
        pollCapture(cfg);

        int n = cfg.macros.size();
        int x1 = px1(), x2 = x1 + panelW();
        int y1 = py1(), y2 = rowY(n) + 54;
        ctx.fill(0, 0, this.width, this.height, 0x60000000);
        Ui.round(ctx, x1, y1, x2, y2, 10, Ui.panel(0xD8));
        Ui.outline(ctx, x1, y1, x2, y2, 10, 0x30FFFFFF);
        Ui.text(ctx, Ui.b("Macros"), x1 + 14, y1 + 12, 0xFFFFFFFF);
        Ui.text(ctx, Ui.t("Une touche envoie une commande (/spawn) ou un message."), x1 + 14, y1 + 25, Ui.MUTED);

        // Fond des champs de texte (arrondi, contour à la couleur du thème quand le champ est actif)
        int fx = x1 + 98, fw = panelW() - 98 - 34, fi = 0;
        for (var child : this.children()) {
            if (!(child instanceof TextFieldWidget tf)) continue;
            int fy = rowY(fi++);
            Ui.round(ctx, fx, fy, fx + fw, fy + 20, 6, 0x66000000);
            Ui.outline(ctx, fx, fy, fx + fw, fy + 20, 6, tf.isFocused() ? Ui.accent(0xD0) : 0x26FFFFFF);
        }
        super.render(ctx, mouseX, mouseY, delta); // champs de texte

        for (int i = 0; i < n; i++) {
            HudConfig.Macro m = cfg.macros.get(i);
            int ry = rowY(i);
            // Touche
            boolean cap = this.capturing == i;
            Text kt = Ui.t(cap ? "Appuie…" : m.key > 0 ? Keys.name(m.key) : "Touche ?");
            boolean overKey = Ui.inside(mouseX, mouseY, x1 + 14, ry + 1, x1 + 92, ry + 19);
            Ui.round(ctx, x1 + 14, ry + 1, x1 + 92, ry + 19, 4, cap ? Ui.ACCENT_DARK : overKey ? 0x50FFFFFF : 0x30FFFFFF);
            Ui.text(ctx, kt, x1 + 53 - Ui.width(kt) / 2, ry + 6, 0xFFFFFFFF);
            if (click && overKey) { Ui.click(); this.capturing = i; this.armed = false; this.setFocused(null); click = false; }
            // Supprimer
            boolean overDel = Ui.inside(mouseX, mouseY, x2 - 30, ry + 1, x2 - 14, ry + 19);
            Ui.round(ctx, x2 - 30, ry + 1, x2 - 14, ry + 19, 4, overDel ? 0x90E0424F : 0x30FFFFFF);
            Ui.text(ctx, Ui.t("✕"), x2 - 25, ry + 6, 0xFFFFFFFF);
            if (click && overDel) { Ui.click(); cfg.macros.remove(i); cfg.save(); this.capturing = -1; rebuild(); return; }
        }
        if (n == 0) Ui.text(ctx, Ui.t("Aucune macro pour l'instant."), x1 + 14, rowY(0) + 6, Ui.MUTED);

        int by = rowY(Math.max(n, 1)) + 8;
        boolean canAdd = n < Macros.MAX;
        boolean overAdd = canAdd && Ui.inside(mouseX, mouseY, x1 + 14, by, x1 + 134, by + 20);
        Ui.round(ctx, x1 + 14, by, x1 + 134, by + 20, 5, overAdd ? Ui.ACCENT_LIGHT : canAdd ? Ui.ACCENT_DARK : 0x30FFFFFF);
        Ui.text(ctx, Ui.t(canAdd ? "+ Nouvelle macro" : "Maximum atteint"), x1 + 24, by + 6, 0xFFFFFFFF);
        if (click && overAdd) { Ui.click(); cfg.macros.add(new HudConfig.Macro()); cfg.save(); rebuild(); return; }

        boolean overBack = Ui.inside(mouseX, mouseY, x2 - 94, by, x2 - 14, by + 20);
        Ui.round(ctx, x2 - 94, by, x2 - 14, by + 20, 5, overBack ? 0x50FFFFFF : 0x30FFFFFF);
        Ui.text(ctx, Ui.t("Terminé"), x2 - 74, by + 6, 0xFFFFFFFF);
        if (click && overBack) { Ui.click(); close(); }
    }

    private void pollCapture(HudConfig cfg) {
        if (this.capturing < 0 || this.capturing >= cfg.macros.size()) { this.capturing = -1; return; }
        int k = Keys.firstPressed();
        if (!this.armed) { if (k < 0) this.armed = true; return; }
        long w = GLFW.glfwGetCurrentContext();
        if (w != 0L && GLFW.glfwGetKey(w, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) { this.capturing = -1; return; }
        if (k > 0) {
            cfg.macros.get(this.capturing).key = k;
            this.capturing = -1;
            cfg.save();
            Ui.click();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.capturing < 0;
    }

    @Override
    public void close() {
        HudConfig.get().save();
        this.client.setScreen(this.parent);
    }
}
