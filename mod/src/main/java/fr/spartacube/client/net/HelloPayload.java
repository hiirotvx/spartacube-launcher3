package fr.spartacube.client.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → serveur : « j'utilise le launcher SpartaCube ». */
public record HelloPayload(String version) implements CustomPayload {
    public static final CustomPayload.Id<HelloPayload> ID = new CustomPayload.Id<>(Identifier.of("spartacube", "hello"));
    public static final PacketCodec<RegistryByteBuf, HelloPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, HelloPayload::version, HelloPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
