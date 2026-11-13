package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudConfig.ModuleSettings;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudModule.Category;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Menu SpartaCube (Shift droit) : modules en cartes, catégories, recherche, options par module. */
public class SpartaModsScreen extends Screen {
    private static final int COLS = 3;
    private static final int CARD_H = 82;
    private static final int GAP = 6;
    private static final int SIDEBAR = 104;

    private final Screen parent;
    private final Ui.Mouse mouse = new Ui.Mouse();
    private final Map<HudModule, Float> hoverCard = new EnumMap<>(HudModule.class);
    private final Map<String, Float> hoverMisc = new java.util.HashMap<>();

    private TextFieldWidget search;
    private Category category = Category.ALL;
    private HudModule optionsFor;
    private float optionsAnim;
    private long openedAt;
    private long lastFrame;
    private float scroll, scrollTarget;
    private int px1, py1, px2, py2;
    private String dragging;

    public SpartaModsScreen(Screen parent) {
        super(Text.literal("Modules SpartaCube"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (this.openedAt == 0) this.openedAt = Util.getMeasuringTimeMs();
        this.mouse.swallow();
        int w = Math.min(this.width - 24, 560);
        int h = Math.min(this.height - 24, 330);
        this.px1 = (this.width - w) / 2;
        this.py1 = (this.height - h) / 2;
        this.px2 = this.px1 + w;
        this.py2 = this.py1 + h;

        String old = this.search == null ? "" : this.search.getText();
        int sx = this.px1 + SIDEBAR + 34;
        int sw = this.px2 - 14 - sx - 14;
        this.search = new TextFieldWidget(this.textRenderer, sx, this.py1 + 38, sw, 12, Text.literal("Rechercher"));
        this.search.setDrawsBackground(false);
        this.search.setMaxLength(32);
        this.search.setPlaceholder(Ui.t("Rechercher un module…").withColor(0x8A7A70));
        this.search.setText(old);
        this.search.setChangedListener(s -> this.scrollTarget = 0);
        this.addDrawableChild(this.search);
    }

    private List<HudModule> visibleModules() {
        String q = this.search == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);
        List<HudModule> out = new ArrayList<>();
        for (HudModule m : HudModule.values()) {
            if (this.category != Category.ALL && m.category != this.category) continue;
            if (!q.isEmpty() && !m.label.toLowerCase(Locale.ROOT).contains(q) && !m.description.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollTarget -= (float) verticalAmount * 30f;
        return true;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // fond dessiné dans render()
    }

    private float hover(String key, boolean over, float dt) {
        float v = Ui.approach(this.hoverMisc.getOrDefault(key, 0f), over ? 1f : 0f, 14f, dt);
        this.hoverMisc.put(key, v);
        return v;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = this.lastFrame == 0 ? 0f : Math.min(0.1f, (now - this.lastFrame) / 1000f);
        this.lastFrame = now;
        float open = Ui.easeOut((now - this.openedAt) / 260f);
        this.mouse.update();
        HudConfig cfg = HudConfig.get();
        boolean click = this.mouse.released && open > 0.9f;

        // Fond
        if (this.client.world == null) SpartaDraw.background(ctx, this.width, this.height);
        ctx.fill(0, 0, this.width, this.height, Ui.alpha(0x99000000, open));

        // Panneau (apparition : léger zoom)
        float s = 0.94f + 0.06f * open;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(this.width / 2f, this.height / 2f);
        ctx.getMatrices().scale(s, s);
        ctx.getMatrices().translate(-this.width / 2f, -this.height / 2f);

        Ui.round(ctx, this.px1, this.py1, this.px2, this.py2, 10, Ui.alpha(0xE6140B12, open));
        Ui.outline(ctx, this.px1, this.py1, this.px2, this.py2, 10, Ui.alpha(0x40FF8C2E, open));

        // En-tête
        SpartaDraw.logo(ctx, this.px1 + 52, this.py1 + 6, 70);
        Ui.text(ctx, Ui.b("MODULES"), this.px1 + SIDEBAR + 14, this.py1 + 14, Ui.ACCENT);
        int cx2 = this.px2 - 24, cy1 = this.py1 + 8;
        boolean overClose = Ui.inside(mouseX, mouseY, cx2, cy1, cx2 + 16, cy1 + 16);
        float hc = hover("close", overClose, dt);
        Ui.round(ctx, cx2, cy1, cx2 + 16, cy1 + 16, 4, Ui.mix(0x30FFFFFF, 0xFFC4253F, hc));
        Ui.icon(ctx, "close", cx2 + 4, cy1 + 4, 8);
        if (click && overClose) { Ui.click(); this.close(); ctx.getMatrices().popMatrix(); return; }

        // Barre latérale : catégories
        int sy = this.py1 + 52;
        for (Category c : Category.values()) {
            int x1 = this.px1 + 8, x2 = this.px1 + SIDEBAR - 4;
            boolean over = Ui.inside(mouseX, mouseY, x1, sy, x2, sy + 20);
            float hv = hover("cat" + c.name(), over || c == this.category, dt);
            Ui.round(ctx, x1, sy, x2, sy + 20, 5, Ui.mix(0x00000000, c == this.category ? 0xFFE8731C : 0x30FFFFFF, hv));
            Ui.text(ctx, Ui.t(c.label), x1 + 8, sy + 6, c == this.category ? 0xFFFFFFFF : Ui.TEXT);
            int count = 0;
            for (HudModule m : HudModule.values()) if ((c == Category.ALL || m.category == c) && cfg.isEnabled(m)) count++;
            Text n = Ui.t(String.valueOf(count));
            Ui.text(ctx, n, x2 - 8 - Ui.width(n), sy + 6, c == this.category ? 0xFFFFE2C4 : Ui.MUTED);
            if (click && over) { Ui.click(); this.category = c; this.optionsFor = null; this.scrollTarget = 0; }
            sy += 23;
        }

        // Bas de la barre latérale
        int bx1 = this.px1 + 8, bx2 = this.px1 + SIDEBAR - 4;
        int by = this.py2 - 30;
        boolean overEdit = Ui.inside(mouseX, mouseY, bx1, by, bx2, by + 22);
        float he = hover("edit", overEdit, dt);
        Ui.round(ctx, bx1, by, bx2, by + 22, 6, Ui.mix(0xFFE8731C, 0xFFFF9A3C, he));
        Ui.icon(ctx, "move", bx1 + 7, by + 6, 10);
        Ui.text(ctx, Ui.b("Éditer l'HUD"), bx1 + 21, by + 7, 0xFFFFFFFF);
        if (click && overEdit) { Ui.click(); this.client.setScreen(new HudEditorScreen(this)); ctx.getMatrices().popMatrix(); return; }

        by -= 26;
        boolean overTitle = Ui.inside(mouseX, mouseY, bx1, by, bx2, by + 22);
        float ht = hover("title", overTitle, dt);
        Ui.round(ctx, bx1, by, bx2, by + 22, 6, Ui.mix(0x30FFFFFF, 0x50FFFFFF, ht));
        Ui.text(ctx, Ui.t("Menu principal"), bx1 + 6, by + 7, Ui.TEXT);
        toggle(ctx, bx2 - 26, by + 6, cfg.customTitleScreen, hover("titleSw", cfg.customTitleScreen, dt));
        if (click && overTitle) { Ui.click(); cfg.customTitleScreen = !cfg.customTitleScreen; cfg.save(); }

        // Recherche
        int sx1 = this.px1 + SIDEBAR + 14, sx2 = this.px2 - 14;
        Ui.round(ctx, sx1, this.py1 + 32, sx2, this.py1 + 52, 6, 0x60000000);
        Ui.outline(ctx, sx1, this.py1 + 32, sx2, this.py1 + 52, 6, this.search.isFocused() ? 0xC0FF8C2E : 0x30FFFFFF);
        Ui.icon(ctx, "search", sx1 + 6, this.py1 + 37, 10);

        // Contenu : grille ou options
        this.optionsAnim = Ui.approach(this.optionsAnim, this.optionsFor != null ? 1f : 0f, 12f, dt);
        int gx1 = sx1, gy1 = this.py1 + 60, gx2 = sx2, gy2 = this.py2 - 10;
        ctx.enableScissor(gx1, gy1, gx2, gy2);
        if (this.optionsFor == null || this.optionsAnim < 0.99f) drawGrid(ctx, mouseX, mouseY, gx1, gy1, gx2, gy2, dt, click && this.optionsFor == null);
        if (this.optionsFor != null) drawOptions(ctx, mouseX, mouseY, gx1, gy1, gx2, gy2, dt, click);
        ctx.disableScissor();

        ctx.getMatrices().popMatrix();
        Ui.centered(ctx, Ui.t("Shift droit : ouvrir / fermer · Molette : défiler"), this.width / 2, this.py2 + 4, Ui.alpha(0xFF9A8578, open));
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void toggle(DrawContext ctx, int x, int y, boolean on, float anim) {
        Ui.round(ctx, x, y, x + 20, y + 10, 5, Ui.mix(0x50FFFFFF, Ui.GREEN, anim));
        int kx = x + 1 + Math.round(anim * 10);
        Ui.round(ctx, kx, y + 1, kx + 8, y + 9, 4, 0xFFFFFFFF);
    }

    private void drawGrid(DrawContext ctx, int mouseX, int mouseY, int gx1, int gy1, int gx2, int gy2, float dt, boolean click) {
        HudConfig cfg = HudConfig.get();
        List<HudModule> list = visibleModules();
        int cardW = (gx2 - gx1 - (COLS - 1) * GAP) / COLS;
        int rows = (list.size() + COLS - 1) / COLS;
        int contentH = rows * (CARD_H + GAP);
        float maxScroll = Math.max(0, contentH - (gy2 - gy1));
        this.scrollTarget = Math.max(0, Math.min(maxScroll, this.scrollTarget));
        this.scroll = Ui.approach(this.scroll, this.scrollTarget, 16f, dt);
        int slide = Math.round(-this.optionsAnim * 40);
        float fade = 1f - this.optionsAnim;

        if (list.isEmpty()) Ui.centered(ctx, Ui.t("Aucun module trouvé"), (gx1 + gx2) / 2, gy1 + 30, Ui.MUTED);
        boolean inArea = Ui.inside(mouseX, mouseY, gx1, gy1, gx2, gy2);
        for (int i = 0; i < list.size(); i++) {
            HudModule m = list.get(i);
            int x1 = gx1 + (i % COLS) * (cardW + GAP) + slide;
            int y1 = gy1 + (i / COLS) * (CARD_H + GAP) - Math.round(this.scroll);
            int x2 = x1 + cardW, y2 = y1 + CARD_H;
            if (y2 < gy1 || y1 > gy2) continue;
            boolean on = cfg.isEnabled(m);
            boolean overCard = inArea && Ui.inside(mouseX, mouseY, x1, y1, x2, y2);
            float hv = Ui.approach(this.hoverCard.getOrDefault(m, 0f), overCard ? 1f : 0f, 14f, dt);
            this.hoverCard.put(m, hv);

            Ui.round(ctx, x1, y1, x2, y2, 7, Ui.alpha(Ui.mix(0x30FFFFFF, 0x48FFFFFF, hv), fade));
            Ui.outline(ctx, x1, y1, x2, y2, 7, Ui.alpha(on ? 0x6022A55B : 0x30FFFFFF, fade));
            int iy = y1 + 8 - Math.round(hv * 2);
            Ui.icon(ctx, m.icon, (x1 + x2) / 2 - 9, iy, 18);
            Ui.centered(ctx, Ui.t(m.label), (x1 + x2) / 2, y1 + 31, Ui.alpha(on ? 0xFFFFFFFF : 0xFFC9B9AF, fade));

            // Ligne OPTIONS
            int oy1 = y2 - 36, oy2 = y2 - 20;
            boolean overOpt = overCard && Ui.inside(mouseX, mouseY, x1, oy1, x2, oy2);
            ctx.fill(x1 + 1, oy1, x2 - 1, oy2, Ui.alpha(overOpt ? 0x40FFFFFF : 0x22000000, fade));
            Ui.centered(ctx, Ui.b("OPTIONS"), (x1 + x2) / 2 - 6, oy1 + 4, Ui.alpha(0xFFE8DED8, fade));
            Ui.icon(ctx, "gear", x2 - 16, oy1 + 3, 10);

            // Ligne ACTIVÉ / DÉSACTIVÉ
            int ty1 = y2 - 19;
            boolean overTog = overCard && Ui.inside(mouseX, mouseY, x1, ty1, x2, y2);
            int base = on ? Ui.GREEN : Ui.RED;
            Ui.round(ctx, x1 + 1, ty1, x2 - 1, y2 - 1, 6, Ui.alpha(Ui.mix(base, 0xFFFFFFFF, overTog ? 0.15f : 0f), fade));
            ctx.fill(x1 + 1, ty1, x2 - 1, ty1 + 4, Ui.alpha(Ui.mix(base, 0xFFFFFFFF, overTog ? 0.15f : 0f), fade));
            Ui.centered(ctx, Ui.b(on ? "ACTIVÉ" : "DÉSACTIVÉ"), (x1 + x2) / 2, ty1 + 6, Ui.alpha(0xFFFFFFFF, fade));

            if (click && overTog) { Ui.click(); cfg.toggle(m); }
            else if (click && (overOpt || overCard)) { Ui.click(); this.optionsFor = m; }
        }
        // Barre de défilement
        if (maxScroll > 0) {
            int trackH = gy2 - gy1;
            int barH = Math.max(20, trackH * trackH / contentH);
            int barY = gy1 + Math.round((trackH - barH) * (this.scroll / maxScroll));
            Ui.round(ctx, gx2 - 3, barY, gx2, barY + barH, 1, 0x80FF8C2E);
        }
    }

    private void drawOptions(DrawContext ctx, int mouseX, int mouseY, int gx1, int gy1, int gx2, int gy2, float dt, boolean click) {
        HudConfig cfg = HudConfig.get();
        HudModule m = this.optionsFor;
        ModuleSettings st = cfg.settings(m);
        int off = Math.round((1 - this.optionsAnim) * 40);
        int x = gx1 + off;
        float a = this.optionsAnim;

        // Retour
        boolean overBack = Ui.inside(mouseX, mouseY, x, gy1, x + 60, gy1 + 16);
        Ui.round(ctx, x, gy1, x + 60, gy1 + 16, 4, Ui.alpha(overBack ? 0x50FFFFFF : 0x30FFFFFF, a));
        Ui.text(ctx, Ui.t("‹ Retour"), x + 9, gy1 + 4, Ui.alpha(Ui.TEXT, a));
        if (click && overBack) { Ui.click(); this.optionsFor = null; return; }

        int y = gy1 + 24;
        Ui.icon(ctx, m.icon, x, y, 20);
        Ui.text(ctx, Ui.b(m.label), x + 28, y + 1, Ui.alpha(0xFFFFFFFF, a));
        Ui.text(ctx, Ui.t(m.description), x + 28, y + 12, Ui.alpha(Ui.MUTED, a));
        y += 32;

        // Activé
        y = optionRow(ctx, mouseX, mouseY, x, gx2, y, "Activé", st.enabled, click, () -> cfg.toggle(m), dt, a);

        if (m == HudModule.ZOOM) {
            y = slider(ctx, mouseX, mouseY, x, gx2, y, "Puissance du zoom", (60 - cfg.zoomFov) / 55f,
                    v -> cfg.zoomFov = Math.round(60 - v * 55), "x" + String.format(Locale.ROOT, "%.1f", 70f / cfg.zoomFov), a);
        } else if (m.movable()) {
            y = slider(ctx, mouseX, mouseY, x, gx2, y, "Taille", (st.scale - 0.5f) / 1.5f,
                    v -> st.scale = Math.round((0.5f + v * 1.5f) * 20f) / 20f, Math.round(st.scale * 100) + " %", a);
            y = optionRow(ctx, mouseX, mouseY, x, gx2, y, "Fond", st.background, click, () -> { st.background = !st.background; cfg.save(); }, dt, a);
            y = optionRow(ctx, mouseX, mouseY, x, gx2, y, "Ombre du texte", st.shadow, click, () -> { st.shadow = !st.shadow; cfg.save(); }, dt, a);
            Ui.text(ctx, Ui.t("Couleur"), x, y + 4, Ui.alpha(Ui.TEXT, a));
            int cx = gx2 - HudConfig.COLORS.length * 16;
            for (int i = 0; i < HudConfig.COLORS.length; i++) {
                int sx = cx + i * 16;
                boolean over = Ui.inside(mouseX, mouseY, sx, y + 1, sx + 12, y + 13);
                if (st.color == i) Ui.round(ctx, sx - 2, y - 1, sx + 14, y + 15, 4, Ui.alpha(0xFFFFFFFF, a));
                Ui.round(ctx, sx, y + 1, sx + 12, y + 13, 3, Ui.alpha(HudConfig.COLORS[i], a));
                if (click && over) { Ui.click(); st.color = i; cfg.save(); }
            }
            y += 22;
            boolean overReset = Ui.inside(mouseX, mouseY, x, y, x + 110, y + 16);
            Ui.round(ctx, x, y, x + 110, y + 16, 4, Ui.alpha(overReset ? 0x50FFFFFF : 0x30FFFFFF, a));
            Ui.text(ctx, Ui.t("Réinitialiser le module"), x + 7, y + 4, Ui.alpha(Ui.TEXT, a));
            if (click && overReset) { Ui.click(); cfg.resetModule(m); }
        } else {
            Ui.text(ctx, Ui.t("Ce module n'a pas d'autre option."), x, y + 4, Ui.alpha(Ui.MUTED, a));
        }
        if (this.mouse.released && this.dragging != null) { this.dragging = null; cfg.save(); }
    }

    private int optionRow(DrawContext ctx, int mx, int my, int x1, int x2, int y, String label, boolean on, boolean click, Runnable action, float dt, float a) {
        boolean over = Ui.inside(mx, my, x1, y, x2, y + 18);
        Ui.round(ctx, x1, y, x2, y + 18, 5, Ui.alpha(over ? 0x30FFFFFF : 0x18FFFFFF, a));
        Ui.text(ctx, Ui.t(label), x1 + 8, y + 5, Ui.alpha(Ui.TEXT, a));
        toggle(ctx, x2 - 28, y + 4, on, hover("opt" + label, on, dt));
        if (click && over) { Ui.click(); action.run(); }
        return y + 22;
    }

    private int slider(DrawContext ctx, int mx, int my, int x1, int x2, int y, String label, float value, java.util.function.Consumer<Float> set, String shown, float a) {
        Ui.text(ctx, Ui.t(label), x1 + 8, y + 5, Ui.alpha(Ui.TEXT, a));
        Text v = Ui.t(shown);
        Ui.text(ctx, v, x2 - 8 - Ui.width(v), y + 5, Ui.alpha(Ui.ACCENT, a));
        int tx1 = x1 + 110, tx2 = x2 - 50;
        int ty = y + 8;
        String key = "slider" + label;
        if (this.mouse.pressed && Ui.inside(mx, my, tx1 - 4, y, tx2 + 4, y + 18)) this.dragging = key;
        if (this.mouse.down && key.equals(this.dragging)) {
            value = Math.max(0f, Math.min(1f, (mx - tx1) / (float) (tx2 - tx1)));
            set.accept(value);
        }
        Ui.round(ctx, tx1, ty, tx2, ty + 3, 1, Ui.alpha(0x40FFFFFF, a));
        int kx = tx1 + Math.round(value * (tx2 - tx1));
        Ui.round(ctx, tx1, ty, kx, ty + 3, 1, Ui.alpha(Ui.ACCENT, a));
        Ui.round(ctx, kx - 4, ty - 3, kx + 5, ty + 6, 4, Ui.alpha(0xFFFFFFFF, a));
        return y + 22;
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
