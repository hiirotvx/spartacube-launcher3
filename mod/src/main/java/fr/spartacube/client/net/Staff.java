package fr.spartacube.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Panel staff : le serveur indique si le joueur a la permission spartacube.staff. */
public final class Staff {
    public static volatile boolean allowed;

    public record Payload(boolean allowed) implements CustomPayload {
        public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(Identifier.of("spartacube", "staff"));
        public static final PacketCodec<RegistryByteBuf, Payload> CODEC = PacketCodec.tuple(PacketCodecs.BOOLEAN, Payload::allowed, Payload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private Staff() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Payload.ID, (p, ctx) -> allowed = p.allowed());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> allowed = false);
    }
}
