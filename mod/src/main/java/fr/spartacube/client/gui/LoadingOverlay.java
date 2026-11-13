package fr.spartacube.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Écran de chargement SpartaCube (connexion au serveur, chargement du monde) avec astuces. */
public final class LoadingOverlay {
    private static final String[] TIPS = {
            "Shift droit ouvre le menu des modules : FPS, CPS, touches, coordonnées…",
            "Maintiens C pour zoomer, et Alt gauche pour regarder autour de toi (Freelook).",
            "Vise un joueur pour voir son pseudo et son titre, façon Dofus.",
            "Échap → ★ Voter pour SpartaCube : vote sans quitter le jeu.",
            "F2 prend une capture : retrouve-la dans l'onglet Captures du launcher.",
            "La pastille S dans la tab montre qui utilise le launcher SpartaCube.",
            "Les profils de mods (Performances, PvP, Shaders) se changent dans le launcher."
    };
    private static long shownAt;
    private static int tip;

    private LoadingOverlay() {}

    public static void onOpen() {
        shownAt = Util.getMeasuringTimeMs();
        tip = (int) (Math.random() * TIPS.length);
    }

    public static void render(Screen screen, DrawContext ctx, int mouseX, int mouseY, float delta, String status) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = screen.width, h = screen.height;
        float t = (Util.getMeasuringTimeMs() - shownAt) / 1000f;
        SpartaDraw.background(ctx, w, h);
        ctx.fill(0, 0, w, h, 0x50000000);
        int logoW = Math.min(220, w - 40);
        SpartaDraw.logo(ctx, w / 2, h / 2 - logoW * SpartaDraw.LOGO_H / SpartaDraw.LOGO_W - 10, logoW);

        // Barre de progression animée (indéterminée)
        int bw = 200, bx = w / 2 - bw / 2, by = h / 2 + 14;
        Ui.round(ctx, bx, by, bx + bw, by + 4, 2, 0x40FFFFFF);
        float p = (t * 0.8f) % 1f;
        int sx = bx + Math.round((bw + 60) * p) - 60;
        ctx.enableScissor(bx, by, bx + bw, by + 4);
        Ui.round(ctx, sx, by, sx + 60, by + 4, 2, Ui.ACCENT);
        ctx.disableScissor();

        Ui.centered(ctx, Ui.b(status), w / 2, by + 12, 0xFFFFFFFF);
        int cur = (tip + (int) (t / 6f)) % TIPS.length;
        float tipA = Math.min(1f, (t % 6f) * 2f) * Math.min(1f, (6f - t % 6f) * 2f);
        Text tipText = Ui.t("Astuce : " + TIPS[cur]);
        Ui.centered(ctx, tipText, w / 2, h - 30, Ui.alpha(0xFFD9C8BE, tipA));

        for (ClickableWidget b : Screens.getButtons(screen)) b.render(ctx, mouseX, mouseY, delta);
    }
}
