package fr.spartacube.client.hud;

import fr.spartacube.client.gui.Ui;
import fr.spartacube.client.net.LauncherUsers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.UUID;

/** Carte joueur « façon Dofus » : vise un joueur pour afficher son pseudo, son titre et sa pastille launcher. */
public final class PlayerCard {
    private static final double RANGE = 48.0;
    private static UUID target;
    private static long lastSeen;
    private static float alpha;
    private static long lastFrame;
    private static float drawX = -1, drawY = -1;

    private PlayerCard() {}

    /** Tick client : quel joueur est visé (jusqu'à 48 blocs, murs compris). */
    public static void tick(MinecraftClient mc) {
        if (!HudConfig.get().isEnabled(HudModule.PLAYER_CARD) || mc.player == null || mc.world == null) {
            target = null;
            return;
        }
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3d eye = cam.getCameraPosVec(1f);
        Vec3d look = cam.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(RANGE));
        HitResult block = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, cam));
        double maxSq = block.getType() == HitResult.Type.MISS ? RANGE * RANGE : block.getPos().squaredDistanceTo(eye);
        Box box = cam.getBoundingBox().stretch(look.multiply(RANGE)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(cam, eye, end, box,
                e -> e instanceof PlayerEntity && e != mc.player && !e.isSpectator() && !e.isInvisibleTo(mc.player), maxSq);
        if (hit != null && hit.getEntity() instanceof PlayerEntity p) {
            target = p.getUuid();
            lastSeen = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastSeen > 350) {
            target = null;
        }
    }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        PlayerListEntry entry = target != null && net != null ? net.getPlayerListEntry(target) : null;
        alpha = Ui.approach(alpha, entry != null ? 1f : 0f, 12f, dt);
        if (alpha < 0.02f || entry == null) return;

        PlayerEntity player = mc.world != null ? mc.world.getPlayerByUuid(target) : null;
        Text name = Ui.b(entry.getProfile().name());
        Text title = entry.getDisplayName() != null ? entry.getDisplayName()
                : player != null ? player.getDisplayName() : null;
        boolean launcher = LauncherUsers.contains(target);
        Text sub = Ui.t(launcher ? "✦ Launcher SpartaCube" : "Joueur");
        if (player != null && player.getHealth() > 0) sub = Ui.t((launcher ? "✦ Launcher SpartaCube  ·  " : "") + "❤ " + Math.round(player.getHealth()));

        int w = Math.max(Math.max(Ui.width(name), title != null ? mc.textRenderer.getWidth(title) : 0), Ui.width(sub)) + 40;
        int h = title != null ? 44 : 34;
        float tx = mc.getWindow().getScaledWidth() / 2f + 14, ty = mc.getWindow().getScaledHeight() / 2f + 10;
        if (drawX < 0) { drawX = tx; drawY = ty; }
        drawX = Ui.approach(drawX, tx, 18f, dt);
        drawY = Ui.approach(drawY, ty, 18f, dt);
        int x = Math.round(drawX), y = Math.round(drawY + (1 - alpha) * 6);

        Ui.round(ctx, x, y, x + w, y + h, 6, Ui.alpha(0xE6140B12, alpha));
        Ui.round(ctx, x, y + 6, x + 2, y + h - 6, 1, Ui.alpha(Ui.ACCENT, alpha));
        PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), x + 8, y + 8, 18);
        int tx2 = x + 32;
        Ui.text(ctx, name, tx2, y + 7, Ui.alpha(0xFFFFFFFF, alpha));
        if (launcher) Ui.icon(ctx, "join", tx2 + Ui.width(name) + 4, y + 6, 9, Ui.alpha(Ui.ACCENT, alpha));
        int ly = y + 19;
        if (title != null) {
            ctx.drawText(mc.textRenderer, title, tx2, ly, Ui.alpha(0xFFFFFFFF, alpha), false);
            ly += 11;
        }
        Ui.text(ctx, sub, tx2, ly, Ui.alpha(launcher ? 0xFFFFB27A : Ui.MUTED, alpha));
    }
}
