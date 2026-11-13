package fr.spartacube.client.cosmetics;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.net.CosmeticsPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmétiques exclusifs au launcher : ailes de feu et aura de flammes (particules côté client).
 * Les droits viennent du plugin serveur ; sans plugin, seul le propriétaire les voit sur lui-même.
 */
public final class Cosmetics {
    public static final int WINGS = 1, AURA = 2;
    /** Propriétaire (Hiirowh) : accès même sans plugin serveur. */
    private static final UUID OWNER = UUID.fromString("6eb277d7-48c3-41c8-a3ba-bf5339f59cd4");
    private static final String OWNER_NAME = "Hiirowh";

    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();
    private static int allowedFromServer = -1;
    private static long tick;

    private Cosmetics() {}

    private static boolean isOwner(MinecraftClient mc) {
        return mc.player != null && (OWNER.equals(mc.player.getUuid()) || OWNER_NAME.equalsIgnoreCase(mc.player.getName().getString()))
                || mc.getSession() != null && OWNER_NAME.equalsIgnoreCase(mc.getSession().getUsername());
    }

    /** Cosmétiques autorisés pour ce joueur. */
    public static int allowed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int fromServer = Math.max(0, allowedFromServer);
        return fromServer | (isOwner(mc) ? WINGS | AURA : 0);
    }

    public static boolean isAllowed(HudModule m) {
        int flag = flagOf(m);
        return flag == 0 || (allowed() & flag) != 0;
    }

    public static int flagOf(HudModule m) {
        return m == HudModule.WINGS ? WINGS : m == HudModule.AURA ? AURA : 0;
    }

    private static int selected() {
        HudConfig cfg = HudConfig.get();
        int f = 0;
        if (cfg.isEnabled(HudModule.WINGS)) f |= WINGS;
        if (cfg.isEnabled(HudModule.AURA)) f |= AURA;
        return f & allowed();
    }

    /** Envoie la sélection au serveur (après un changement dans le menu). */
    public static void sync() {
        if (ClientPlayNetworking.canSend(CosmeticsPayloads.Select.ID)) ClientPlayNetworking.send(new CosmeticsPayloads.Select(selected()));
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(CosmeticsPayloads.Select.ID, CosmeticsPayloads.Select.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmeticsPayloads.Allowed.ID, CosmeticsPayloads.Allowed.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmeticsPayloads.Active.ID, CosmeticsPayloads.Active.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CosmeticsPayloads.Allowed.ID, (p, ctx) -> ctx.client().execute(() -> {
            allowedFromServer = p.flags();
            sync();
        }));
        ClientPlayNetworking.registerGlobalReceiver(CosmeticsPayloads.Active.ID, (p, ctx) -> {
            ACTIVE.clear();
            for (CosmeticsPayloads.Entry e : p.entries()) ACTIVE.put(e.id(), e.flags());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> { ACTIVE.clear(); allowedFromServer = -1; });
    }

    private static int flagsFor(PlayerEntity p, MinecraftClient mc) {
        if (p == mc.player) return selected();
        return ACTIVE.getOrDefault(p.getUuid(), 0);
    }

    /** Tick client : particules des joueurs proches. */
    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.isPaused()) return;
        tick++;
        for (PlayerEntity p : mc.world.getPlayers()) {
            int f = flagsFor(p, mc);
            if (f == 0 || p.isInvisible() || p.isSpectator() || p.squaredDistanceTo(mc.player) > 48 * 48) continue;
            boolean firstPersonSelf = p == mc.player && mc.options.getPerspective() == Perspective.FIRST_PERSON;
            if ((f & AURA) != 0) aura(mc, p);
            if ((f & WINGS) != 0 && !firstPersonSelf) wings(mc, p);
        }
    }

    private static void aura(MinecraftClient mc, PlayerEntity p) {
        double t = tick * 0.12;
        for (int k = 0; k < 3; k++) {
            double a = t + k * (Math.PI * 2 / 3);
            double r = 0.75;
            mc.world.addParticleClient(ParticleTypes.SMALL_FLAME,
                    p.getX() + Math.cos(a) * r, p.getY() + 0.1 + (tick % 20) * 0.01, p.getZ() + Math.sin(a) * r,
                    0, 0.015, 0);
        }
        if (tick % 4 == 0) {
            double a = Math.random() * Math.PI * 2;
            mc.world.addParticleClient(ParticleTypes.FLAME,
                    p.getX() + Math.cos(a) * 0.5, p.getY() + 0.2 + Math.random() * 1.6, p.getZ() + Math.sin(a) * 0.5,
                    0, 0.02, 0);
        }
    }

    private static void wings(MinecraftClient mc, PlayerEntity p) {
        float yaw = p.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        double backX = MathHelper.sin(yaw), backZ = -MathHelper.cos(yaw);
        double rightX = -MathHelper.cos(yaw), rightZ = -MathHelper.sin(yaw);
        double flap = Math.sin(tick * 0.22) * 0.45;
        double baseY = p.getY() + (p.isSneaking() ? 1.05 : 1.3);
        double ox = p.getX() + backX * 0.3, oz = p.getZ() + backZ * 0.3;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 7; i++) {
                double t = Math.random();
                double span = 0.15 + t * 1.25;
                double edge = Math.random() < 0.6 ? 1.0 : Math.random();
                double h = (0.55 * Math.sin(Math.PI * t) - 0.35 * t) * edge + (1 - edge) * -0.15;
                double sideOut = span * Math.cos(flap);
                double backOut = span * Math.sin(flap) * 0.8;
                double x = ox + rightX * side * sideOut + backX * backOut;
                double z = oz + rightZ * side * sideOut + backZ * backOut;
                mc.world.addParticleClient(edge >= 1.0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                        x, baseY + h, z, 0, 0.004, 0);
            }
        }
    }
}
