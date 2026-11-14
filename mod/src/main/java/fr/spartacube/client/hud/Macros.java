package fr.spartacube.client.hud;

import net.minecraft.client.MinecraftClient;

import java.util.HashSet;
import java.util.Set;

/** Macros : une touche envoie une commande ou un message. */
public final class Macros {
    public static final int MAX = 16;
    private static final Set<Integer> DOWN = new HashSet<>();
    private static long lastSent;

    private Macros() {}

    public static void tick(MinecraftClient mc) {
        HudConfig cfg = HudConfig.get();
        if (cfg.macros.isEmpty()) return;
        boolean canRun = mc.player != null && mc.currentScreen == null;
        for (HudConfig.Macro m : cfg.macros) {
            if (m.key <= 0) continue;
            boolean down = Keys.isDown(m.key);
            boolean was = DOWN.contains(m.key);
            if (down && !was) {
                DOWN.add(m.key);
                if (canRun) run(mc, m.command);
            } else if (!down && was) {
                DOWN.remove(m.key);
            }
        }
    }

    /** Envoie une commande (« /spawn ») ou un message. Une macro au plus toutes les 300 ms. */
    public static void run(MinecraftClient mc, String command) {
        if (command == null || mc.player == null) return;
        String c = command.trim();
        if (c.isEmpty() || c.length() > 200) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < 300) return;
        lastSent = now;
        if (c.startsWith("/")) mc.player.networkHandler.sendChatCommand(c.substring(1));
        else mc.player.networkHandler.sendChatMessage(c);
    }
}
