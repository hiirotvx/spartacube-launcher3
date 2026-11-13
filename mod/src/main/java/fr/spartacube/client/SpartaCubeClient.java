package fr.spartacube.client;

import fr.spartacube.client.gui.HudEditorScreen;
import fr.spartacube.client.gui.SpartaModsScreen;
import fr.spartacube.client.gui.SpartaTitleScreen;
import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudRenderer;
import fr.spartacube.client.hud.Mechanics;
import fr.spartacube.client.hud.PlayerCard;
import fr.spartacube.client.hud.Toasts;
import fr.spartacube.client.hud.Freelook;
import fr.spartacube.client.gui.LoadingOverlay;
import fr.spartacube.client.net.ToastPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import fr.spartacube.client.net.VoteBridge;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import fr.spartacube.client.net.LauncherUsers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Point d'entrée du mod SpartaCube Client. */
public class SpartaCubeClient implements ClientModInitializer {
    public static final String SERVER_ADDRESS = "play.spartacube.fr";
    private static boolean rightShiftWasDown;
    private static int joinTicks = -1;
    private static String pendingCommand = "";
    private static int voteCheckTicks = 0;
    private static boolean voteNotified;

    @Override
    public void onInitializeClient() {
        HudConfig.get();
        LauncherUsers.init();

        // HUD (FPS, ping, CPS, minimap…)
        HudElementRegistry.addLast(Identifier.of("spartacube", "hud"), (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;
            HudRenderer.renderAll(ctx, mc);
        });

        // Carte joueur au survol (façon Dofus)
        HudElementRegistry.addLast(Identifier.of("spartacube", "player_card"), (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || mc.currentScreen != null) return;
            PlayerCard.render(ctx, mc);
        });

        // Notifications animées
        HudElementRegistry.addLast(Identifier.of("spartacube", "toasts"), (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || !HudConfig.get().isEnabled(HudModule.NOTIFICATIONS)) return;
            Toasts.render(ctx, mc);
        });
        PayloadTypeRegistry.playS2C().register(ToastPayload.ID, ToastPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ToastPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (HudConfig.get().isEnabled(HudModule.NOTIFICATIONS)) Toasts.show(payload.type(), payload.title(), payload.text());
                }));

        // Écran de chargement SpartaCube
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!HudConfig.get().isEnabled(HudModule.LOADING_SCREEN)) return;
            String status = screen instanceof ConnectScreen ? "Connexion à SpartaCube…"
                    : screen instanceof LevelLoadingScreen ? "Chargement du monde…" : null;
            if (status == null) return;
            LoadingOverlay.onOpen();
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, d) -> LoadingOverlay.render(s, ctx, mx, my, d, status));
        });

        // Connexion : destination choisie dans le launcher + rappel de vote
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var entry = client.getCurrentServerEntry();
            boolean sparta = entry != null && entry.address.toLowerCase(java.util.Locale.ROOT).contains("spartacube");
            pendingCommand = sparta ? VoteBridge.joinCommand() : "";
            joinTicks = 60;
            voteCheckTicks = 200;
            voteNotified = false;
        });

        // Bouton « Voter » dans le menu Échap
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof GameMenuScreen)) return;
            int maxY = -1;
            for (ClickableWidget b : Screens.getButtons(screen)) maxY = Math.max(maxY, b.getY() + b.getHeight());
            if (maxY < 0) return; // menu réduit (F3 + Échap)
            Screens.getButtons(screen).add(ButtonWidget.builder(
                    Text.literal("★ Voter pour SpartaCube").formatted(Formatting.GOLD),
                    b -> VoteBridge.openVote()).dimensions(w / 2 - 102, maxY + 8, 204, 20).build());
        });

        // Scoreboard affichable / masquable
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (ctx, tickCounter) -> {
            if (HudConfig.get().isEnabled(HudModule.SCOREBOARD)) original.render(ctx, tickCounter);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Menu principal SpartaCube à la place de l'écran titre
            if (client.currentScreen instanceof TitleScreen && HudConfig.get().customTitleScreen) {
                client.setScreen(new SpartaTitleScreen());
            }

            Mechanics.tick(client);
            HudRenderer.tick(client);
            PlayerCard.tick(client);
            Freelook.tick(client);

            if (joinTicks > 0 && client.player != null && --joinTicks == 0 && !pendingCommand.isEmpty()) {
                client.player.networkHandler.sendChatCommand(pendingCommand.substring(1));
                pendingCommand = "";
            }
            if (client.world != null && HudConfig.get().isEnabled(HudModule.NOTIFICATIONS) && --voteCheckTicks <= 0) {
                voteCheckTicks = 20 * 60 * 5;
                VoteBridge.fetchVoteAvailable(available -> client.execute(() -> {
                    if (available && !voteNotified) {
                        Toasts.show("vote", "Vote disponible !", "Échap → ★ Voter pour SpartaCube pour gagner tes récompenses.");
                        voteNotified = true;
                    } else if (!available) {
                        voteNotified = false;
                    }
                }));
            }

            // Shift droit : ouvre / ferme le menu des modules en jeu
            long window = GLFW.glfwGetCurrentContext();
            boolean down = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (down && !rightShiftWasDown && client.world != null) {
                if (client.currentScreen == null) client.setScreen(new SpartaModsScreen(null));
                else if (client.currentScreen instanceof SpartaModsScreen) client.currentScreen.close();
            }
            rightShiftWasDown = down;
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(Mechanics::restore);
    }
}
