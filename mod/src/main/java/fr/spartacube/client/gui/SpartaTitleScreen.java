package fr.spartacube.client.gui;

import fr.spartacube.client.SpartaCubeClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Menu principal SpartaCube (remplace l'écran titre de Minecraft). */
public class SpartaTitleScreen extends Screen {
    private int logoBottom;

    public SpartaTitleScreen() {
        super(Text.literal("SpartaCube"));
    }

    @Override
    protected void init() {
        int bw = 220;
        int half = (bw - 4) / 2;
        int x = this.width / 2 - bw / 2;
        int logoW = Math.min(300, this.width - 40);
        this.logoBottom = 12 + logoW * 306 / 512;
        int y = Math.max(this.logoBottom + 8, this.height / 2 - 20);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("⚔ Rejoindre SpartaCube").formatted(Formatting.GOLD, Formatting.BOLD),
                b -> joinSpartaCube()).dimensions(x, y, bw, 24).build());
        y += 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Solo"),
                b -> this.client.setScreen(new SelectWorldScreen(this))).dimensions(x, y, half, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Multijoueur"),
                b -> this.client.setScreen(new MultiplayerScreen(this))).dimensions(x + half + 4, y, half, 20).build());
        y += 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Options"),
                b -> this.client.setScreen(new OptionsScreen(this, this.client.options))).dimensions(x, y, half, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Menu HUD"),
                b -> this.client.setScreen(new SpartaMenuScreen(this))).dimensions(x + half + 4, y, half, 20).build());
        y += 24;
        Screen modMenu = modMenuScreen();
        if (modMenu != null) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Mods"),
                    b -> this.client.setScreen(modMenuScreen())).dimensions(x, y, half, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Quitter"),
                    b -> this.client.scheduleStop()).dimensions(x + half + 4, y, half, 20).build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Quitter"),
                    b -> this.client.scheduleStop()).dimensions(x, y, bw, 20).build());
        }
    }

    private void joinSpartaCube() {
        String address = SpartaCubeClient.SERVER_ADDRESS;
        ServerInfo info = new ServerInfo("SpartaCube", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, this.client, ServerAddress.parse(address), info, false, null);
    }

    /** Ouvre l'écran de Mod Menu s'il est installé (sans dépendance à la compilation). */
    private Screen modMenuScreen() {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return null;
        try {
            Class<?> c = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
            return (Screen) c.getConstructor(Screen.class).newInstance(this);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Le fond est dessiné dans render() : pas de flou ni de panorama vanilla.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        SpartaDraw.background(ctx, this.width, this.height);
        SpartaDraw.logo(ctx, this.width, 12, Math.min(300, this.width - 40));
        super.render(ctx, mouseX, mouseY, delta);
        int bottom = this.height - 12;
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("SpartaCube · Minecraft " + minecraftVersion()), 4, bottom, 0xFFD9BFAE);
        Text right = Text.literal("spartacube.fr").formatted(Formatting.GOLD);
        ctx.drawTextWithShadow(this.textRenderer, right, this.width - this.textRenderer.getWidth(right) - 4, bottom, 0xFFFFB02E);
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
