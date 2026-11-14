package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudConfig.ModuleSettings;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudModule.Category;
import fr.spartacube.client.cosmetics.Cosmetics;
import fr.spartacube.client.hud.Toasts;
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
    private static final int CARD_H = 64;
    private static final int GAP = 6;
    private static final int SIDEBAR = 112;

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
    private HudModule hoveredModule;

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
        if (this.optionsFor != null) this.optScrollTarget -= (float) verticalAmount * 30f;
        else this.scrollTarget -= (float) verticalAmount * 30f;
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
        pollCapture();
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
        Ui.outline(ctx, this.px1, this.py1, this.px2, this.py2, 10, Ui.alpha(0x22FFFFFF, open));

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
        Ui.text(ctx, Ui.t("Écran titre"), bx1 + 8, by + 7, Ui.TEXT);
        toggle(ctx, bx2 - 27, by + 6, cfg.customTitleScreen, hover("titleSw", cfg.customTitleScreen, dt));
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
        String foot = this.hoveredModule != null && this.optionsFor == null
                ? this.hoveredModule.description + "  ·  clic : activer  ·  ⚙ : options"
                : "Shift droit : ouvrir / fermer  ·  Molette : défiler";
        Ui.centered(ctx, Ui.t(foot), this.width / 2, this.py2 + 5, Ui.alpha(this.hoveredModule != null ? 0xFFE8DED8 : 0xFF9A8578, open));
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void toggle(DrawContext ctx, int x, int y, boolean on, float anim) {
        Ui.round(ctx, x, y, x + 20, y + 10, 5, Ui.mix(0x40FFFFFF, Ui.GREEN, anim));
        int kx = x + 1 + Math.round(anim * 10);
        Ui.round(ctx, kx, y + 1, kx + 8, y + 9, 4, 0xFFFFFFFF);
    }

    private void drawGrid(DrawContext ctx, int mouseX, int mouseY, int gx1, int gy1, int gx2, int gy2, float dt, boolean click) {
        HudConfig cfg = HudConfig.get();
        List<HudModule> list = visibleModules();
        this.hoveredModule = null;
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

            Ui.round(ctx, x1, y1, x2, y2, 7, Ui.alpha(Ui.mix(0x22FFFFFF, 0x36FFFFFF, hv), fade));
            if (overCard) this.hoveredModule = m;
            int iy = y1 + 9 - Math.round(hv * 1.5f);
            Ui.icon(ctx, m.icon, (x1 + x2) / 2 - 9, iy, 18, Ui.alpha(on ? Ui.ACCENT : 0xFF8F8079, fade));
            Ui.centered(ctx, Ui.t(m.label), (x1 + x2) / 2, y1 + 32, Ui.alpha(on ? 0xFFFFFFFF : 0xFFB3A39A, fade));

            // Bas de carte : engrenage (options) à gauche, interrupteur à droite
            int by = y2 - 17;
            boolean overGear = overCard && Ui.inside(mouseX, mouseY, x1 + 4, by - 2, x1 + 20, by + 12);
            Ui.icon(ctx, "gear", x1 + 7, by + 1, 9, Ui.alpha(overGear ? 0xFFFFFFFF : 0x90FFFFFF, fade));
            float sw = Ui.approach(this.hoverMisc.getOrDefault("sw" + m.name(), on ? 1f : 0f), on ? 1f : 0f, 16f, dt);
            this.hoverMisc.put("sw" + m.name(), sw);
            toggle(ctx, x2 - 27, by + 1, on, sw);

            boolean locked = !Cosmetics.isAllowed(m);
            if (locked) {
                Ui.round(ctx, x1, y1, x2, y2, 7, Ui.alpha(0x90140B12, fade));
                Ui.icon(ctx, "armor", (x1 + x2) / 2 - 7, y1 + 44, 14, Ui.alpha(0xFFB3A39A, fade));
            }
            if (click && overCard && locked) {
                Ui.click();
                Toasts.show("info", "Cosmétique verrouillé", "Ce cosmétique n'est pas encore disponible pour ton compte.");
            } else if (click && overGear) { Ui.click(); this.optionsFor = m; this.optScroll = 0; this.optScrollTarget = 0; }
            else if (click && overCard) {
                Ui.click();
                cfg.toggle(m);
                if (Cosmetics.flagOf(m) != 0) Cosmetics.sync();
            }
        }
        // Barre de défilement
        if (maxScroll > 0) {
            int trackH = gy2 - gy1;
            int barH = Math.max(20, trackH * trackH / contentH);
            int barY = gy1 + Math.round((trackH - barH) * (this.scroll / maxScroll));
            Ui.round(ctx, gx2 - 3, barY, gx2, barY + barH, 1, 0x80FF8C2E);
        }
    }

    private float optScroll, optScrollTarget;

    private void drawOptions(DrawContext ctx, int mouseX, int mouseY, int gx1, int gy1, int gx2, int gy2, float dt, boolean click) {
        HudConfig cfg = HudConfig.get();
        HudModule m = this.optionsFor;
        ModuleSettings st = cfg.settings(m);
        int off = Math.round((1 - this.optionsAnim) * 40);
        int x = gx1 + off;
        int x2 = gx2 - 6;
        float a = this.optionsAnim;
        // Les clics hors de la zone visible sont ignorés
        boolean inArea = Ui.inside(mouseX, mouseY, gx1, gy1, gx2, gy2);
        click = click && inArea;
        this.optScroll = Ui.approach(this.optScroll, this.optScrollTarget, 16f, dt);
        int top = gy1 - Math.round(this.optScroll);

        // Retour
        int y = top;
        boolean overBack = inArea && Ui.inside(mouseX, mouseY, x, y, x + 60, y + 16);
        Ui.round(ctx, x, y, x + 60, y + 16, 4, Ui.alpha(overBack ? 0x50FFFFFF : 0x30FFFFFF, a));
        Ui.text(ctx, Ui.t("‹ Retour"), x + 9, y + 4, Ui.alpha(Ui.TEXT, a));
        if (click && overBack) { Ui.click(); this.capturing = null; this.optionsFor = null; this.optScrollTarget = 0; this.optScroll = 0; return; }

        y += 24;
        Ui.icon(ctx, m.icon, x, y, 20, Ui.alpha(Ui.ACCENT, a));
        Ui.text(ctx, Ui.b(m.label), x + 28, y + 1, Ui.alpha(0xFFFFFFFF, a));
        Ui.text(ctx, Ui.t(m.description), x + 28, y + 12, Ui.alpha(Ui.MUTED, a));
        y += 32;

        y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Activé", st.enabled, click, () -> {
            if (!Cosmetics.isAllowed(m)) { Toasts.show("info", "Cosmétique verrouillé", "Ce cosmétique n'est pas encore disponible pour ton compte."); return; }
            cfg.toggle(m);
            if (Cosmetics.flagOf(m) != 0) Cosmetics.sync();
        }, dt, a);

        switch (m) {
            case ZOOM -> {
                y = section(ctx, x, y, "Zoom", a);
                y = keyRow(ctx, mouseX, mouseY, x, x2, y, "Touche du zoom (maintenir)", "zoom", cfg.zoomKey, k -> cfg.zoomKey = k, click, a);
                y = slider(ctx, mouseX, mouseY, x, x2, y, "Puissance", (60 - cfg.zoomFov) / 55f,
                        v -> cfg.zoomFov = Math.round(60 - v * 55), "x" + String.format(Locale.ROOT, "%.1f", 70f / cfg.zoomFov), a);
                y = note(ctx, x, y, "Maintiens la touche pour zoomer.", a);
            }
            case FLY_BOOST -> {
                y = section(ctx, x, y, "Vol", a);
                y = slider(ctx, mouseX, mouseY, x, x2, y, "Vitesse de vol", (cfg.flyBoostMult - 1f) / 4f,
                        v -> cfg.flyBoostMult = Math.round((1f + v * 4f) * 10f) / 10f, "x" + String.format(Locale.ROOT, "%.1f", cfg.flyBoostMult), a);
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Seulement en sprintant", cfg.flyBoostSprintOnly, click,
                        () -> { cfg.flyBoostSprintOnly = !cfg.flyBoostSprintOnly; cfg.save(); }, dt, a);
                y = note(ctx, x, y, "Fonctionne sans le Toggle Sprint, dès que tu voles (créatif ou /fly).", a);
            }
            case FREELOOK -> {
                y = section(ctx, x, y, "Touche", a);
                y = keyRow(ctx, mouseX, mouseY, x, x2, y, "Touche du freelook", "freelook", cfg.freelookKey, k -> cfg.freelookKey = k, click, a);
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Maintenir Alt (sinon : appuyer pour basculer)", cfg.freelookHold, click,
                        () -> { cfg.freelookHold = !cfg.freelookHold; cfg.save(); }, dt, a);
            }
            case FULLBRIGHT -> {
                y = section(ctx, x, y, "Touche", a);
                y = keyRow(ctx, mouseX, mouseY, x, x2, y, "Touche pour activer / désactiver", "fullbright", cfg.fullbrightKey, k -> cfg.fullbrightKey = k, click, a);
                y = note(ctx, x, y, "Clic droit sur la touche pour l'enlever.", a);
            }
            case HEALTH -> {
                y = section(ctx, x, y, "Affichage", a);
                y = choiceRow(ctx, mouseX, mouseY, x, x2, y, "Format", new String[]{"❤ 9.5", "19 / 20 PV", "❤❤❤❤❤"}, cfg.healthMode,
                        i -> { cfg.healthMode = i; cfg.save(); }, click, a);
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Seulement le joueur visé", cfg.healthTargetOnly, click,
                        () -> { cfg.healthTargetOnly = !cfg.healthTargetOnly; cfg.save(); }, dt, a);
                y = note(ctx, x, y, "Les cœurs s'affichent à côté du pseudo, au-dessus de la tête.", a);
            }
            case ARMOR -> {
                y = section(ctx, x, y, "Armure", a);
                y = choiceRow(ctx, mouseX, mouseY, x, x2, y, "État", new String[]{"Icônes", "Pourcentage", "Barre d'usure", "Durabilité restante"}, st.mode,
                        i -> { st.mode = i; cfg.save(); }, click, a);
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Horizontal", st.horizontal, click, () -> { st.horizontal = !st.horizontal; cfg.save(); }, dt, a);
                y = note(ctx, x, y, "Position : bouton « Éditer l'HUD » puis glisse le module.", a);
            }
            case NOTIFICATIONS -> {
                y = section(ctx, x, y, "Son", a);
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Son de notification", cfg.toastSound, click,
                        () -> { cfg.toastSound = !cfg.toastSound; cfg.save(); }, dt, a);
                y = slider(ctx, mouseX, mouseY, x, x2, y, "Volume", cfg.toastVolume,
                        v -> cfg.toastVolume = Math.round(v * 20f) / 20f, Math.round(cfg.toastVolume * 100) + " %", a);
                boolean overTest = inArea && Ui.inside(mouseX, mouseY, x, y, x + 120, y + 16);
                Ui.round(ctx, x, y, x + 120, y + 16, 4, Ui.alpha(overTest ? 0x50FFFFFF : 0x30FFFFFF, a));
                Ui.text(ctx, Ui.t("Tester une notification"), x + 7, y + 4, Ui.alpha(Ui.TEXT, a));
                if (click && overTest) { Ui.click(); cfg.save(); Toasts.show("event", "Test", "Voici à quoi ressemble une notification SpartaCube."); }
                y += 22;
            }
            default -> { }
        }

        if (m.movable()) {
            y = section(ctx, x, y, "Apparence", a);
            y = slider(ctx, mouseX, mouseY, x, x2, y, "Taille", (st.scale - 0.5f) / 1.5f,
                    v -> st.scale = Math.round((0.5f + v * 1.5f) * 20f) / 20f, Math.round(st.scale * 100) + " %", a);
            y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Fond", st.background, click, () -> { st.background = !st.background; cfg.save(); }, dt, a);
            if (st.background) {
                y = slider(ctx, mouseX, mouseY, x, x2, y, "Opacité du fond", st.bgOpacity / 100f,
                        v -> st.bgOpacity = Math.round(v * 100f), st.bgOpacity + " %", a);
            }
            y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Ombre du texte", st.shadow, click, () -> { st.shadow = !st.shadow; cfg.save(); }, dt, a);
            if (m.kind == HudModule.Kind.TEXT) {
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Crochets  [ ]", st.brackets, click, () -> { st.brackets = !st.brackets; cfg.save(); }, dt, a);
            }

            y = section(ctx, x, y, "Couleur", a);
            y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Arc-en-ciel", st.chroma, click, () -> { st.chroma = !st.chroma; cfg.save(); }, dt, a);
            if (st.chroma) {
                y = slider(ctx, mouseX, mouseY, x, x2, y, "Vitesse", (st.chromaSpeed - 0.2f) / 2.8f,
                        v -> st.chromaSpeed = Math.round((0.2f + v * 2.8f) * 10f) / 10f, "x" + String.format(Locale.ROOT, "%.1f", st.chromaSpeed), a);
            } else {
                // Palette rapide
                Ui.text(ctx, Ui.t("Palette"), x + 8, y + 4, Ui.alpha(Ui.TEXT, a));
                int cx = x2 - HudConfig.COLORS.length * 16 - 4;
                for (int i = 0; i < HudConfig.COLORS.length; i++) {
                    int sx = cx + i * 16;
                    boolean over = inArea && Ui.inside(mouseX, mouseY, sx, y + 1, sx + 12, y + 13);
                    if (!st.customColor && st.color == i) Ui.round(ctx, sx - 2, y - 1, sx + 14, y + 15, 4, Ui.alpha(0xFFFFFFFF, a));
                    Ui.round(ctx, sx, y + 1, sx + 12, y + 13, 3, Ui.alpha(HudConfig.COLORS[i], a));
                    if (click && over) { Ui.click(); st.color = i; st.customColor = false; cfg.save(); }
                }
                y += 20;
                y = optionRow(ctx, mouseX, mouseY, x, x2, y, "Couleur personnalisée", st.customColor, click, () -> { st.customColor = !st.customColor; cfg.save(); }, dt, a);
                if (st.customColor) {
                    y = colorSlider(ctx, mouseX, mouseY, x, x2, y, "Teinte", st.hue, v -> st.hue = v, 0, st, a);
                    y = colorSlider(ctx, mouseX, mouseY, x, x2, y, "Saturation", st.sat, v -> st.sat = v, 1, st, a);
                    y = colorSlider(ctx, mouseX, mouseY, x, x2, y, "Luminosité", st.bri, v -> st.bri = Math.max(0.15f, v), 2, st, a);
                }
            }
            // Aperçu
            Ui.text(ctx, Ui.t("Aperçu"), x + 8, y + 5, Ui.alpha(Ui.TEXT, a));
            String sample = m.label;
            int pw = this.textRenderer.getWidth(sample) + 8;
            int pxs = x2 - pw - 4;
            if (st.background) Ui.round(ctx, pxs, y + 1, pxs + pw, y + 16, 3, st.bgColor());
            int ccx = pxs + 4;
            for (int i = 0; i < sample.length(); i++) {
                String ch = String.valueOf(sample.charAt(i));
                ctx.drawText(this.textRenderer, ch, ccx, y + 5, Ui.alpha(st.textColor(ccx), a), st.shadow);
                ccx += this.textRenderer.getWidth(ch);
            }
            y += 22;

            boolean overReset = inArea && Ui.inside(mouseX, mouseY, x, y, x + 110, y + 16);
            Ui.round(ctx, x, y, x + 110, y + 16, 4, Ui.alpha(overReset ? 0x50FFFFFF : 0x30FFFFFF, a));
            Ui.text(ctx, Ui.t("Réinitialiser le module"), x + 7, y + 4, Ui.alpha(Ui.TEXT, a));
            if (click && overReset) { Ui.click(); cfg.resetModule(m); }
            y += 22;
        } else if (m != HudModule.ZOOM && m != HudModule.FLY_BOOST && m != HudModule.FREELOOK && m != HudModule.NOTIFICATIONS
                && m != HudModule.FULLBRIGHT && m != HudModule.HEALTH) {
            y = note(ctx, x, y, "Ce module n'a pas d'autre option.", a);
        }

        // Défilement des options
        int contentH = y - top;
        float max = Math.max(0, contentH - (gy2 - gy1) + 6);
        this.optScrollTarget = Math.max(0, Math.min(max, this.optScrollTarget));
        if (max > 0) {
            int trackH = gy2 - gy1;
            int barH = Math.max(20, trackH * trackH / Math.max(1, contentH));
            int barY = gy1 + Math.round((trackH - barH) * (this.optScroll / max));
            Ui.round(ctx, gx2 - 3, barY, gx2, barY + barH, 1, 0x80FF8C2E);
        }
        if (this.mouse.released && this.dragging != null) { this.dragging = null; cfg.save(); }
    }

    /** Touche en cours de capture (null : aucune). */
    private String capturing;
    private java.util.function.IntConsumer captureSet;

    private int keyRow(DrawContext ctx, int mx, int my, int x1, int x2, int y, String label, String id, int key,
                       java.util.function.IntConsumer set, boolean click, float a) {
        Ui.round(ctx, x1, y, x2, y + 18, 5, Ui.alpha(0x18FFFFFF, a));
        Ui.text(ctx, Ui.t(label), x1 + 8, y + 5, Ui.alpha(Ui.TEXT, a));
        boolean cap = id.equals(this.capturing);
        String shown = cap ? "Appuie sur une touche…" : key > 0 ? fr.spartacube.client.hud.Keys.name(key) : "Aucune";
        Text t = Ui.t(shown);
        int bw = Math.max(54, Ui.width(t) + 14);
        int bx = x2 - bw - 4;
        boolean over = Ui.inside(mx, my, bx, y + 2, x2 - 4, y + 16);
        Ui.round(ctx, bx, y + 2, x2 - 4, y + 16, 4, Ui.alpha(cap ? 0xFFE8731C : over ? 0x50FFFFFF : 0x30FFFFFF, a));
        Ui.text(ctx, t, bx + (bw - Ui.width(t)) / 2, y + 5, Ui.alpha(0xFFFFFFFF, a));
        if (click && over) {
            Ui.click();
            this.capturing = id;
            this.captureSet = set;
            this.captureArmed = false;
            if (this.search != null) this.search.setFocused(false);
        }
        long w = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (over && w != 0L && org.lwjgl.glfw.GLFW.glfwGetMouseButton(w, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS && !cap) {
            set.accept(0);
            HudConfig.get().save();
        }
        return y + 22;
    }

    private boolean captureArmed;

    public boolean isCapturing() {
        return this.capturing != null;
    }

    /** À chaque image : enregistre la première touche pressée pendant une capture. */
    private void pollCapture() {
        if (this.capturing == null) return;
        int k = fr.spartacube.client.hud.Keys.firstPressed();
        if (!this.captureArmed) { if (k < 0) this.captureArmed = true; return; } // attendre que tout soit relâché
        long w = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (w != 0L && org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            this.capturing = null;
            return;
        }
        if (k > 0) {
            this.captureSet.accept(k);
            this.capturing = null;
            HudConfig.get().save();
            Ui.click();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.capturing == null;
    }

    private int choiceRow(DrawContext ctx, int mx, int my, int x1, int x2, int y, String label, String[] choices, int index,
                          java.util.function.IntConsumer set, boolean click, float a) {
        Ui.round(ctx, x1, y, x2, y + 18, 5, Ui.alpha(0x18FFFFFF, a));
        Ui.text(ctx, Ui.t(label), x1 + 8, y + 5, Ui.alpha(Ui.TEXT, a));
        int i = Math.floorMod(index, choices.length);
        Text t = Ui.t("‹  " + choices[i] + "  ›");
        int bw = Ui.width(t) + 14;
        int bx = x2 - bw - 4;
        boolean over = Ui.inside(mx, my, bx, y + 2, x2 - 4, y + 16);
        Ui.round(ctx, bx, y + 2, x2 - 4, y + 16, 4, Ui.alpha(over ? 0x50FFFFFF : 0x30FFFFFF, a));
        Ui.text(ctx, t, bx + 7, y + 5, Ui.alpha(Ui.ACCENT, a));
        if (click && over) {
            Ui.click();
            // clic sur la moitié gauche : précédent, sinon suivant
            set.accept(Math.floorMod(i + (mx < bx + bw / 2 ? -1 : 1), choices.length));
        }
        return y + 22;
    }

    private int section(DrawContext ctx, int x, int y, String title, float a) {
        y += 4;
        Ui.text(ctx, Ui.b(title.toUpperCase(Locale.ROOT)), x + 2, y, Ui.alpha(Ui.ACCENT, a));
        return y + 13;
    }

    private int note(DrawContext ctx, int x, int y, String text, float a) {
        Ui.text(ctx, Ui.t(text), x + 2, y + 2, Ui.alpha(Ui.MUTED, a));
        return y + 16;
    }

    /** Curseur de couleur avec piste en dégradé (0 : teinte, 1 : saturation, 2 : luminosité). */
    private int colorSlider(DrawContext ctx, int mx, int my, int x1, int x2, int y, String label, float value,
                            java.util.function.Consumer<Float> set, int kind, ModuleSettings st, float a) {
        Ui.text(ctx, Ui.t(label), x1 + 8, y + 5, Ui.alpha(Ui.TEXT, a));
        int tx1 = x1 + 110, tx2 = x2 - 50;
        int ty = y + 6;
        String key = "color" + label;
        if (this.mouse.pressed && Ui.inside(mx, my, tx1 - 4, y, tx2 + 4, y + 18)) this.dragging = key;
        if (this.mouse.down && key.equals(this.dragging)) {
            value = Math.max(0f, Math.min(1f, (mx - tx1) / (float) (tx2 - tx1)));
            set.accept(value);
        }
        int w = tx2 - tx1;
        for (int i = 0; i < w; i++) {
            float t = i / (float) Math.max(1, w - 1);
            int rgb = switch (kind) {
                case 0 -> java.awt.Color.HSBtoRGB(t, 0.9f, 1f);
                case 1 -> java.awt.Color.HSBtoRGB(st.hue, t, st.bri);
                default -> java.awt.Color.HSBtoRGB(st.hue, st.sat, t);
            };
            ctx.fill(tx1 + i, ty, tx1 + i + 1, ty + 6, Ui.alpha(0xFF000000 | (rgb & 0xFFFFFF), a));
        }
        Ui.outline(ctx, tx1 - 1, ty - 1, tx2 + 1, ty + 7, 3, Ui.alpha(0x40FFFFFF, a));
        int kx = tx1 + Math.round(value * w);
        Ui.round(ctx, kx - 3, ty - 3, kx + 4, ty + 9, 3, Ui.alpha(0xFFFFFFFF, a));
        int sw = Ui.alpha(st.textColor(), a);
        Ui.round(ctx, x2 - 22, y + 3, x2 - 8, y + 15, 3, sw);
        return y + 22;
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
