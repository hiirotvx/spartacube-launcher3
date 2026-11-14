package fr.spartacube.client.gui;

import fr.spartacube.client.SpartaCubeClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/** Menu principal SpartaCube : boutons sombres arrondis, icônes, animations d'entrée et de survol. */
public class SpartaTitleScreen extends Screen {
    private final List<Btn> buttons = new ArrayList<>();
    private final Ui.Mouse mouse = new Ui.Mouse();
    private long openedAt;
    private long lastFrame;
    private float parX, parY;

    /** Bouton dessiné à la main. */
    private static final class Btn {
        final String icon;
        final Text label;
        final Text sub;
        final Runnable action;
        final boolean primary;
        final boolean iconOnly;
        final int order;
        int x, y, w, h;
        float hover;

        Btn(String icon, String label, String sub, boolean primary, boolean iconOnly, int order, Runnable action) {
            this.icon = icon;
            this.label = label == null ? null : (primary ? Ui.b(label) : Ui.t(label));
            this.sub = sub == null ? null : Ui.t(sub);
            this.primary = primary;
            this.iconOnly = iconOnly;
            this.order = order;
            this.action = action;
        }

        void place(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    public SpartaTitleScreen() {
        super(Text.literal("SpartaCube"));
    }

    @Override
    protected void init() {
        this.buttons.clear();
        if (this.openedAt == 0) this.openedAt = Util.getMeasuringTimeMs();
        this.mouse.swallow();

        int bw = 210;
        int cx = this.width / 2;
        int logoW = Math.min(250, this.width - 40);
        int logoH = logoW * SpartaDraw.LOGO_H / SpartaDraw.LOGO_W;
        int y = Math.max(16 + logoH + 6, this.height / 2 - 34);

        Btn join = new Btn("join", "Rejoindre SpartaCube", SpartaCubeClient.SERVER_ADDRESS, true, false, 0, this::joinSpartaCube);
        join.place(cx - bw / 2, y, bw, 32);
        this.buttons.add(join);
        y += 38;
        Btn solo = new Btn("solo", "Solo", null, false, false, 1, () -> this.client.setScreen(new SelectWorldScreen(this)));
        solo.place(cx - bw / 2, y, bw, 22);
        this.buttons.add(solo);
        y += 26;
        Btn multi = new Btn("multi", "Multijoueur", null, false, false, 2, () -> this.client.setScreen(new MultiplayerScreen(this)));
        multi.place(cx - bw / 2, y, bw, 22);
        this.buttons.add(multi);
        y += 26;
        int half = (bw - 4) / 2;
        Btn opt = new Btn("options", "Options", null, false, false, 3, () -> this.client.setScreen(new OptionsScreen(this, this.client.options)));
        opt.place(cx - bw / 2, y, half, 22);
        this.buttons.add(opt);
        Btn mods = new Btn("hud", "Modules", null, false, false, 3, () -> this.client.setScreen(new SpartaModsScreen(this)));
        mods.place(cx - bw / 2 + half + 4, y, bw - half - 4, 22);
        this.buttons.add(mods);

        // Barre d'icônes en bas
        List<Btn> bar = new ArrayList<>();
        bar.add(new Btn("move", "Modifier l'HUD", null, false, true, 5, () -> this.client.setScreen(new HudEditorScreen(this))));
        if (modMenuScreen() != null) bar.add(new Btn("mods", "Mods installés", null, false, true, 5, () -> this.client.setScreen(modMenuScreen())));
        bar.add(new Btn("web", "spartacube.fr", null, false, true, 5, () -> Util.getOperatingSystem().open(java.net.URI.create("https://spartacube.fr"))));
        bar.add(new Btn("quit", "Quitter le jeu", null, false, true, 5, () -> this.client.scheduleStop()));
        int size = 24, gap = 6;
        int total = bar.size() * size + (bar.size() - 1) * gap;
        int bx = cx - total / 2;
        for (Btn b : bar) {
            b.place(bx, this.height - size - 22, size, size);
            this.buttons.add(b);
            bx += size + gap;
        }
    }

    private void joinSpartaCube() {
        String address = SpartaCubeClient.SERVER_ADDRESS;
        ServerInfo info = new ServerInfo("SpartaCube", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, this.client, ServerAddress.parse(address), info, false, null);
    }

    /** Écran de Mod Menu s'il est installé (sans dépendance à la compilation). */
    private Screen modMenuScreen() {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return null;
        try {
            Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            return (Screen) c.getConstructor(Screen.class).newInstance(this);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // fond dessiné dans render() (pas de flou ni de panorama vanilla)
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.applyTheme();
        long now = Util.getMeasuringTimeMs();
        float dt = this.lastFrame == 0 ? 0f : Math.min(0.1f, (now - this.lastFrame) / 1000f);
        this.lastFrame = now;
        float t = (now - this.openedAt) / 1000f;
        this.mouse.update();

        // Parallaxe douce
        float tx = (mouseX / (float) Math.max(1, this.width) - 0.5f) * 2f;
        float ty = (mouseY / (float) Math.max(1, this.height) - 0.5f) * 2f;
        this.parX = Ui.approach(this.parX, tx * 0.35f, 3f, dt);
        this.parY = Ui.approach(this.parY, ty * 0.35f, 3f, dt);
        SpartaDraw.background(ctx, this.width, this.height, this.parX, this.parY);

        // Logo : apparition + léger flottement
        float logoIn = Ui.easeOut(t / 0.7f);
        int logoW = Math.min(250, this.width - 40);
        float bob = (float) Math.sin(t * 1.6) * 2f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(this.width / 2f, 16 + (1 - logoIn) * -18 + bob);
        float s = 0.9f + 0.1f * logoIn;
        ctx.getMatrices().scale(s, s);
        SpartaDraw.logo(ctx, 0, 0, logoW);
        ctx.getMatrices().popMatrix();

        Btn hovered = null;
        for (Btn b : this.buttons) {
            float in = Ui.easeOut((t - 0.15f - b.order * 0.07f) / 0.45f);
            if (in <= 0f) continue;
            boolean over = in > 0.95f && Ui.inside(mouseX, mouseY, b.x, b.y, b.x + b.w, b.y + b.h);
            if (over) hovered = b;
            b.hover = Ui.approach(b.hover, over ? 1f : 0f, 14f, dt);
            drawButton(ctx, b, in);
        }

        // Info-bulle des boutons-icônes
        if (hovered != null && hovered.iconOnly && hovered.label != null) {
            int w = Ui.width(hovered.label) + 12;
            int x = hovered.x + hovered.w / 2 - w / 2;
            int y = hovered.y - 18;
            Ui.round(ctx, x, y, x + w, y + 14, 4, Ui.panel(0xE0));
            Ui.centered(ctx, hovered.label, x + w / 2, y + 3, Ui.TEXT);
        }

        float footIn = Ui.easeOut((t - 0.5f) / 0.5f);
        Ui.text(ctx, Ui.t("SpartaCube · Minecraft " + minecraftVersion()), 6, this.height - 12, Ui.alpha(0xFFD9BFAE, footIn));
        Text right = Ui.t("Non affilié à Mojang ou Microsoft");
        Ui.text(ctx, right, this.width - Ui.width(right) - 6, this.height - 12, Ui.alpha(0xFF9A8578, footIn));
        if (this.client.getSession() != null) {
            Text name = Ui.t(this.client.getSession().getUsername());
            int w = Ui.width(name) + 26;
            Ui.round(ctx, 8, 8, 8 + w, 26, 6, Ui.alpha(Ui.panel(0xB0), footIn));
            Ui.icon(ctx, "solo", 13, 12, 10);
            Ui.text(ctx, name, 27, 13, Ui.alpha(Ui.TEXT, footIn));
        }

        super.render(ctx, mouseX, mouseY, delta);

        if (hovered != null && this.mouse.released) {
            Ui.click();
            hovered.action.run();
        }
    }

    private void drawButton(DrawContext ctx, Btn b, float in) {
        int dy = Math.round((1 - in) * 10);
        int x1 = b.x, y1 = b.y + dy, x2 = b.x + b.w, y2 = b.y + b.h + dy;
        float h = b.hover;
        if (b.primary) {
            Ui.round(ctx, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 8, Ui.alpha(Ui.ACCENT, 0.3f * h * in));
            Ui.round(ctx, x1, y1, x2, y2, 6, Ui.alpha(Ui.mix(Ui.ACCENT_DARK, Ui.ACCENT_LIGHT, h), in));
            ctx.fillGradient(x1 + 3, y1 + (y2 - y1) / 2, x2 - 3, y2 - 1, 0x00000000, Ui.alpha(0x40A0101F, in));
        } else {
            Ui.round(ctx, x1, y1, x2, y2, 6, Ui.alpha(Ui.mix(Ui.panel(0xC8), Ui.mix(Ui.panel(0xE8), 0xE8FFFFFF, 0.08f), h), in));
            Ui.outline(ctx, x1, y1, x2, y2, 6, Ui.alpha(Ui.mix(0x30FFFFFF, Ui.accent(0xC0), h), in));
        }
        int textColor = Ui.alpha(Ui.TEXT, in);
        if (b.iconOnly) {
            Ui.icon(ctx, b.icon, x1 + 6, y1 + 6, b.w - 12);
            return;
        }
        int iconSize = b.primary ? 14 : 10;
        int contentW = iconSize + 6 + Ui.width(b.label);
        if (b.primary && b.sub != null) contentW = Math.max(contentW, iconSize + 6 + Ui.width(b.sub));
        int sx = x1 + (b.w - contentW) / 2 + Math.round(h * 2);
        Ui.icon(ctx, b.icon, sx, y1 + (b.h - iconSize) / 2, iconSize);
        if (b.primary && b.sub != null) {
            Ui.text(ctx, b.label, sx + iconSize + 6, y1 + 6, textColor);
            Ui.text(ctx, b.sub, sx + iconSize + 6, y1 + 18, Ui.alpha(0xFFFFE2C4, in));
        } else {
            Ui.text(ctx, b.label, sx + iconSize + 6, y1 + (b.h - 8) / 2, textColor);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
