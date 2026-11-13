package fr.spartacube.client;

import fr.spartacube.client.gui.HudEditorScreen;
import fr.spartacube.client.gui.SpartaMenuScreen;
import fr.spartacube.client.gui.SpartaTitleScreen;
import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
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

    @Override
    public void onInitializeClient() {
        HudConfig.get();

        // HUD personnalisé (FPS, ping, CPS, coordonnées…)
        HudElementRegistry.addLast(Identifier.of("spartacube", "hud"), (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;
            HudRenderer.renderAll(ctx, mc);
        });

        // Scoreboard affichable / masquable depuis le menu
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original -> (ctx, tickCounter) -> {
            if (HudConfig.get().isEnabled(HudModule.SCOREBOARD)) original.render(ctx, tickCounter);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Menu principal SpartaCube à la place de l'écran titre
            if (client.currentScreen instanceof TitleScreen && HudConfig.get().customTitleScreen) {
                client.setScreen(new SpartaTitleScreen());
            }

            // Shift droit : ouvre / ferme le menu SpartaCube en jeu
            long window = GLFW.glfwGetCurrentContext();
            boolean down = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            if (down && !rightShiftWasDown && client.world != null) {
                if (client.currentScreen == null) client.setScreen(new SpartaMenuScreen(null));
                else if (client.currentScreen instanceof SpartaMenuScreen) client.currentScreen.close();
            }
            rightShiftWasDown = down;
        });
    }
}
