package fr.spartacube.client.hud;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Minimap simple (vue du dessus, nord en haut) calculée à partir des couleurs de carte des blocs. */
public final class Minimap {
    public static final int RADIUS = 32;
    public static final int SIZE = RADIUS * 2;
    private static final int[] COLORS = new int[SIZE * SIZE];
    private static int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
    private static int ticks;

    private Minimap() {}

    /** Recalcule la carte (appelé au tick client, toutes les 10 ticks ou si le joueur a bougé). */
    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        ClientPlayerEntity p = mc.player;
        if (world == null || p == null) return;
        int px = p.getBlockX(), pz = p.getBlockZ();
        if (++ticks < 10 && px == lastX && pz == lastZ) return;
        ticks = 0;
        lastX = px;
        lastZ = pz;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int[] heights = new int[SIZE * SIZE];
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int x = px - RADIUS + dx, z = pz - RADIUS + dz;
                int i = dz * SIZE + dx;
                if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
                    COLORS[i] = 0;
                    heights[i] = Integer.MIN_VALUE;
                    continue;
                }
                int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                pos.set(x, top, z);
                BlockState state = world.getBlockState(pos);
                int rgb = state.getMapColor(world, pos).color;
                heights[i] = top;
                COLORS[i] = rgb == 0 ? 0 : 0xFF000000 | rgb;
            }
        }
        // Relief : plus clair si plus haut que le voisin du nord, plus sombre sinon
        for (int dz = SIZE - 1; dz > 0; dz--) {
            for (int dx = 0; dx < SIZE; dx++) {
                int i = dz * SIZE + dx, n = (dz - 1) * SIZE + dx;
                if (COLORS[i] == 0 || heights[n] == Integer.MIN_VALUE) continue;
                int diff = heights[i] - heights[n];
                float f = diff > 0 ? 1.12f : diff < 0 ? 0.82f : 1.0f;
                COLORS[i] = shade(COLORS[i], f);
            }
        }
    }

    private static int shade(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Dessine la carte à (0,0) dans le repère courant (taille SIZE+4). */
    public static void render(DrawContext ctx, MinecraftClient mc, boolean background) {
        if (background) ctx.fill(0, 0, SIZE + 4, SIZE + 4, 0xA0140B12);
        ctx.fill(0, 0, SIZE + 4, 1, 0xFFFF8C2E);
        for (int dz = 0; dz < SIZE; dz++) {
            int runStart = 0;
            int runColor = COLORS[dz * SIZE];
            for (int dx = 1; dx <= SIZE; dx++) {
                int c = dx < SIZE ? COLORS[dz * SIZE + dx] : 1;
                if (c != runColor) {
                    if (runColor != 0) ctx.fill(2 + runStart, 2 + dz, 2 + dx, 3 + dz, runColor);
                    runStart = dx;
                    runColor = c;
                }
            }
        }
        // Joueur au centre + direction du regard
        int c = 2 + RADIUS;
        ctx.fill(c - 1, c - 1, c + 2, c + 2, 0xFFFFFFFF);
        if (mc.player != null) {
            double yaw = Math.toRadians(mc.player.getYaw());
            for (int i = 2; i <= 5; i++) {
                int x = c + (int) Math.round(-Math.sin(yaw) * i);
                int y = c + (int) Math.round(Math.cos(yaw) * i);
                ctx.fill(x, y, x + 1, y + 1, 0xFFFF5A1F);
            }
        }
        ctx.drawText(mc.textRenderer, "N", c - 2, 3, 0xFFFFFFFF, true);
    }
}
