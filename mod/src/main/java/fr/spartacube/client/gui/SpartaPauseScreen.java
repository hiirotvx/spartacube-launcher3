package fr.spartacube.client.gui;

import fr.spartacube.client.net.Staff;
import fr.spartacube.client.net.VoteBridge;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu Échap aux couleurs de SpartaCube. Les actions restent celles du menu du jeu :
 * chaque bouton « appuie » sur le vrai bouton correspondant (reprendre, options, déconnexion…).
 */
public class SpartaPauseScreen extends Screen {
    /** Menu du jeu à laisser tel quel (menu réduit F3+Échap, ou retour au menu classique). */
    public static Screen keepVanilla;

    private final GameMenuScreen vanilla;
    private final List<ButtonWidget> buttons;
    private final Ui.Mouse mouse = new Ui.Mouse();
    private final Map<String, Float> hover = new HashMap<>();
    private final long openedAt = Util.getMeasuringTimeMs();
    private long lastFrame = openedAt;

    private record Entry(String id, Text label, Runnable action, boolean active, int style) {}

    private SpartaPauseScreen(GameMenuScreen vanilla, List<ButtonWidget> buttons) {
        super(Text.literal("Menu"));
        this.vanilla = vanilla;
        this.buttons = buttons;
    }

    public static void tryOpen(MinecraftClient client, GameMenuScreen gm) {
        List<ButtonWidget> list = new ArrayList<>();
        try {
            for (ClickableWidget w : Screens.getButtons(gm)) if (w instanceof ButtonWidget b && b.visible) list.add(b);
        } catch (Throwable ignored) {
            list.clear();
        }
        if (list.size() < 3) { keepVanilla = gm; return; } // menu réduit : on n'y touche pas
        client.setScreen(new SpartaPauseScreen(gm, list));
    }

    @Override
    protected void init() {
        this.mouse.swallow();
    }

    private void press(ButtonWidget b) {
        try {
            b.onPress(null);
        } catch (Throwable t) {
            classic(); // en cas de souci : menu classique du jeu
        }
    }

    private void classic() {
        keepVanilla = this.vanilla;
        this.client.setScreen(this.vanilla);
    }

    private static boolean is(ButtonWidget b, String key) {
        return b.getMessage().getString().equals(Text.translatable(key).getString());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world != null) this.applyBlur(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.applyTheme();
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - this.lastFrame) / 1000f);
        this.lastFrame = now;
        float open = Ui.easeOut(Math.min(1f, (now - this.openedAt) / 220f));
        this.mouse.update();
        boolean click = this.mouse.pressed;

        // Répartition : reprendre en haut, déconnexion en bas, vote à part, le reste sur deux colonnes
        ButtonWidget resume = null, quit = null, vote = null;
        List<ButtonWidget> middle = new ArrayList<>();
        for (ButtonWidget b : this.buttons) {
            String s = b.getMessage().getString();
            if (resume == null && is(b, "menu.returnToGame")) resume = b;
            else if (quit == null && (is(b, "menu.disconnect") || is(b, "menu.returnToMenu"))) quit = b;
            else if (vote == null && s.contains("Voter")) vote = b;
            else middle.add(b);
        }

        int pw = 270, rowH = 22, gap = 5;
        int rows = (middle.size() + 1) / 2;
        int ph = 46 + (resume != null ? rowH + gap : 0) + rows * (rowH + gap) + 8 + rowH + gap + (quit != null ? rowH + gap + 6 : 0) + 22;
        int px1 = (this.width - pw) / 2, px2 = px1 + pw;
        int py1 = Math.max(6, (this.height - ph) / 2), py2 = py1 + ph;

        ctx.fill(0, 0, this.width, this.height, Ui.alpha(0x60000000, open));
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(0f, (1f - open) * 12f);
        for (int i = 4; i >= 1; i--) Ui.round(ctx, px1 - i * 2, py1 - i * 2 + 3, px2 + i * 2, py2 + i * 2 + 3, 10 + i * 2, Ui.alpha(0x14000000, open));
        Ui.round(ctx, px1, py1, px2, py2, 10, Ui.alpha(Ui.panel(0xCC), open));
        Ui.outline(ctx, px1, py1, px2, py2, 10, Ui.alpha(0x30FFFFFF, open));
        if (fr.spartacube.client.hud.HudConfig.get().themeHolo) {
            for (int i = 0; i < pw - 24; i += 2) ctx.fill(px1 + 12 + i, py1, px1 + 14 + i, py1 + 1, Ui.alpha(Ui.holo(i / (float) pw, 120), open));
        } else {
            ctx.fill(px1 + 12, py1, px2 - 12, py1 + 1, Ui.alpha(Ui.accent(0x90), open));
        }

        Text title = Ui.b("SPARTACUBE");
        Ui.text(ctx, title, (this.width - Ui.width(title)) / 2, py1 + 12, Ui.alpha(Ui.ACCENT, open));
        Text sub = Ui.t("Jeu en pause");
        Ui.text(ctx, sub, (this.width - Ui.width(sub)) / 2, py1 + 25, Ui.alpha(Ui.MUTED, open));

        int x1 = px1 + 14, x2 = px2 - 14, y = py1 + 46;
        int half = (x2 - x1 - gap) / 2;
        if (resume != null) {
            ButtonWidget b = resume;
            if (button(ctx, "resume", b.getMessage(), x1, y, x2, y + rowH, mouseX, mouseY, dt, open, b.active, 1) && click) { press(b); ctx.getMatrices().popMatrix(); return; }
            y += rowH + gap;
        }
        for (int i = 0; i < middle.size(); i++) {
            ButtonWidget b = middle.get(i);
            boolean last = i == middle.size() - 1 && middle.size() % 2 == 1;
            int bx1 = last ? x1 : x1 + (i % 2) * (half + gap);
            int bx2 = last ? x2 : bx1 + half;
            if (button(ctx, "m" + i, b.getMessage(), bx1, y, bx2, y + rowH, mouseX, mouseY, dt, open, b.active, 0) && click) { press(b); ctx.getMatrices().popMatrix(); return; }
            if (i % 2 == 1 || last) y += rowH + gap;
        }

        // Raccourcis SpartaCube
        y += 8;
        List<Entry> extras = new ArrayList<>();
        extras.add(new Entry("mods", Ui.t("Modules"), () -> this.client.setScreen(new SpartaModsScreen(this)), true, 0));
        extras.add(new Entry("macros", Ui.t("Macros"), () -> this.client.setScreen(new MacrosScreen(this)), true, 0));
        if (Staff.allowed) extras.add(new Entry("staff", Ui.t("Staff"), () -> this.client.setScreen(new StaffScreen(this)), true, 0));
        extras.add(new Entry("vote", Ui.t("★ Voter"), VoteBridge::openVote, true, 2));
        int n = extras.size();
        int ew = (x2 - x1 - (n - 1) * gap) / n;
        for (int i = 0; i < n; i++) {
            Entry e = extras.get(i);
            int bx1 = x1 + i * (ew + gap);
            int bx2 = i == n - 1 ? x2 : bx1 + ew;
            if (button(ctx, e.id(), e.label(), bx1, y, bx2, y + rowH, mouseX, mouseY, dt, open, true, e.style()) && click) { Ui.click(); e.action().run(); ctx.getMatrices().popMatrix(); return; }
        }
        y += rowH + gap + 6;

        if (quit != null) {
            ButtonWidget b = quit;
            if (button(ctx, "quit", b.getMessage(), x1, y, x2, y + rowH, mouseX, mouseY, dt, open, b.active, 3) && click) { press(b); ctx.getMatrices().popMatrix(); return; }
            y += rowH + gap;
        }

        Text classic = Ui.t("Menu classique");
        int cw = Ui.width(classic);
        boolean overClassic = Ui.inside(mouseX, mouseY, (this.width - cw) / 2 - 4, py2 - 16, (this.width + cw) / 2 + 4, py2 - 4);
        Ui.text(ctx, classic, (this.width - cw) / 2, py2 - 14, Ui.alpha(overClassic ? Ui.TEXT : Ui.MUTED, open));
        ctx.getMatrices().popMatrix();
        if (click && overClassic) { Ui.click(); classic(); }
    }

    /** style : 0 normal, 1 principal (accent), 2 or (vote), 3 danger. Renvoie vrai si la souris est dessus et le bouton actif. */
    private boolean button(DrawContext ctx, String id, Text label, int x1, int y1, int x2, int y2, int mx, int my, float dt, float open, boolean active, int style) {
        boolean over = active && Ui.inside(mx, my, x1, y1, x2, y2);
        float h = Ui.approach(this.hover.getOrDefault(id, 0f), over ? 1f : 0f, 14f, dt);
        this.hover.put(id, h);
        int base = switch (style) {
            case 1 -> Ui.mix(Ui.ACCENT_DARK, Ui.ACCENT_LIGHT, h);
            case 2 -> Ui.mix(0x40FFC83C, 0x70FFC83C, h);
            case 3 -> Ui.mix(0x40E0424F, 0x90E0424F, h);
            default -> Ui.mix(0x22FFFFFF, 0x40FFFFFF, h);
        };
        if (h > 0.02f) Ui.round(ctx, x1 + 1, y1 + 2, x2 - 1, y2 + 2, 6, Ui.alpha(0x30000000, open * h));
        Ui.round(ctx, x1, y1, x2, y2, 6, Ui.alpha(base, open * (active ? 1f : 0.45f)));
        if (style == 0) Ui.outline(ctx, x1, y1, x2, y2, 6, Ui.alpha(Ui.mix(0x14FFFFFF, Ui.accent(0xA0), h), open));
        if (over && fr.spartacube.client.hud.HudConfig.get().themeHolo) Ui.holoSheen(ctx, x1 + 2, y1 + 2, x2 - 2, y2 - 2, h * 0.6f * open);
        Text t = Ui.t(label.getString());
        int tw = Ui.width(t);
        int color = style == 2 ? 0xFFFFD27F : style == 3 ? 0xFFFFC9CD : 0xFFFFFFFF;
        Ui.text(ctx, t, (x1 + x2 - tw) / 2, (y1 + y2) / 2 - 4, Ui.alpha(active ? color : Ui.MUTED, open));
        return over;
    }
}
