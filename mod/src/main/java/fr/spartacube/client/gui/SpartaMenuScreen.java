package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Menu SpartaCube ouvert avec Shift droit : activer les modules d'HUD et régler l'affichage. */
public class SpartaMenuScreen extends Screen {
    private static final int COLS = 3;
    private static final int BW = 120;
    private static final int GAP = 6;

    private final Screen parent;
    private int panelTop;
    private int panelBottom;

    public SpartaMenuScreen(Screen parent) {
        super(Text.literal("Menu SpartaCube"));
        this.parent = parent;
    }

    private static Text moduleLabel(HudModule m) {
        boolean on = HudConfig.get().isEnabled(m);
        MutableText state = Text.literal(on ? " ✔" : " ✘").formatted(on ? Formatting.GREEN : Formatting.RED);
        return Text.literal(m.label).append(state);
    }

    private static Text optionLabel(String name, boolean on) {
        return Text.literal(name + " : ").append(Text.literal(on ? "Oui" : "Non").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private static Text colorLabel() {
        HudConfig c = HudConfig.get();
        return Text.literal("Couleur : ").append(Text.literal(HudConfig.COLOR_NAMES[c.color]).withColor(c.textColor() & 0xFFFFFF));
    }

    @Override
    protected void init() {
        HudConfig cfg = HudConfig.get();
        int gridW = COLS * BW + (COLS - 1) * GAP;
        int x0 = (this.width - gridW) / 2;
        int rows = (HudModule.values().length + COLS - 1) / COLS;
        int contentH = 22 + rows * 24 + 14 + 2 * 24 + 10 + 24;
        this.panelTop = Math.max(8, (this.height - contentH) / 2 - 10);
        int y = this.panelTop + 26;

        int i = 0;
        for (HudModule m : HudModule.values()) {
            int x = x0 + (i % COLS) * (BW + GAP);
            int yy = y + (i / COLS) * 24;
            ButtonWidget b = ButtonWidget.builder(moduleLabel(m), btn -> {
                HudConfig.get().toggle(m);
                btn.setMessage(moduleLabel(m));
            }).dimensions(x, yy, BW, 20).build();
            b.setTooltip(Tooltip.of(Text.literal(m.description)));
            this.addDrawableChild(b);
            i++;
        }
        y += rows * 24 + 14;

        this.addDrawableChild(ButtonWidget.builder(colorLabel(), b -> {
            cfg.color = (cfg.color + 1) % HudConfig.COLORS.length;
            cfg.save();
            b.setMessage(colorLabel());
        }).dimensions(x0, y, BW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(optionLabel("Fond", cfg.background), b -> {
            cfg.background = !cfg.background;
            cfg.save();
            b.setMessage(optionLabel("Fond", cfg.background));
        }).dimensions(x0 + BW + GAP, y, BW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(optionLabel("Ombre", cfg.shadow), b -> {
            cfg.shadow = !cfg.shadow;
            cfg.save();
            b.setMessage(optionLabel("Ombre", cfg.shadow));
        }).dimensions(x0 + 2 * (BW + GAP), y, BW, 20).build());
        y += 24;

        ButtonWidget title = ButtonWidget.builder(optionLabel("Menu principal SpartaCube", cfg.customTitleScreen), b -> {
            cfg.customTitleScreen = !cfg.customTitleScreen;
            cfg.save();
            b.setMessage(optionLabel("Menu principal SpartaCube", cfg.customTitleScreen));
        }).dimensions(x0, y, gridW, 20).build();
        title.setTooltip(Tooltip.of(Text.literal("Remplace l'écran titre de Minecraft par celui de SpartaCube")));
        this.addDrawableChild(title);
        y += 34;

        int third = (gridW - 2 * GAP) / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("✥ Déplacer l'HUD").formatted(Formatting.GOLD),
                b -> this.client.setScreen(new HudEditorScreen(this))).dimensions(x0, y, third, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Positions par défaut"),
                b -> cfg.resetPositions()).dimensions(x0 + third + GAP, y, third, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Fermer"),
                b -> this.close()).dimensions(x0 + 2 * (third + GAP), y, third, 20).build());
        this.panelBottom = y + 30;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // fond géré dans render()
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client.world == null) SpartaDraw.background(ctx, this.width, this.height);
        else ctx.fill(0, 0, this.width, this.height, 0x90000000);
        int gridW = COLS * BW + (COLS - 1) * GAP;
        int x0 = (this.width - gridW) / 2;
        SpartaDraw.panel(ctx, x0 - 12, this.panelTop, x0 + gridW + 12, this.panelBottom);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("⚔ MENU SPARTACUBE ⚔").formatted(Formatting.BOLD), this.width / 2, this.panelTop + 9, 0xFFFFB02E);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Shift droit pour ouvrir ce menu en jeu · Échap pour fermer"), this.width / 2, this.panelBottom + 6, 0xFFA88F80);
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
