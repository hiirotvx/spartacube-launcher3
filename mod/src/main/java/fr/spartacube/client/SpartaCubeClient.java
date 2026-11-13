package fr.spartacube.client;

import fr.spartacube.client.gui.HudEditorScreen;
import fr.spartacube.client.gui.SpartaModsScreen;
import fr.spartacube.client.gui.SpartaTitleScreen;
import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.hud.HudRenderer;
import fr.spartacube.client.hud.Mechanics;
import fr.spartacube.client.hud.Minimap;
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
            if (HudConfig.get().isEnabled(HudModule.MINIMAP)) Minimap.tick(client);

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
