package fr.spartacube.client.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Serveur → client : liste des joueurs connectés qui utilisent le launcher (VarInt + UUID en 2 longs). */
public record UsersPayload(List<UUID> users) implements CustomPayload {
    public static final CustomPayload.Id<UsersPayload> ID = new CustomPayload.Id<>(Identifier.of("spartacube", "users"));
    public static final PacketCodec<RegistryByteBuf, UsersPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, Uuids.PACKET_CODEC, 1000), UsersPayload::users, UsersPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
