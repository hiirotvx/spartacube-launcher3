package fr.spartacube.client.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Serveur → client : notification (type, titre, message). */
public record ToastPayload(String type, String title, String text) implements CustomPayload {
    public static final CustomPayload.Id<ToastPayload> ID = new CustomPayload.Id<>(Identifier.of("spartacube", "toast"));
    public static final PacketCodec<RegistryByteBuf, ToastPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(16), ToastPayload::type,
            PacketCodecs.string(64), ToastPayload::title,
            PacketCodecs.string(256), ToastPayload::text,
            ToastPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
