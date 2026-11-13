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

/** Paquets des cosmétiques. Drapeaux : 1 = ailes, 2 = aura. */
public final class CosmeticsPayloads {
    private CosmeticsPayloads() {}

    /** Client → serveur : cosmétiques choisis. */
    public record Select(int flags) implements CustomPayload {
        public static final Id<Select> ID = new Id<>(Identifier.of("spartacube", "cosmetic_select"));
        public static final PacketCodec<RegistryByteBuf, Select> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Select::flags, Select::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Serveur → client : cosmétiques auxquels ce joueur a droit. */
    public record Allowed(int flags) implements CustomPayload {
        public static final Id<Allowed> ID = new Id<>(Identifier.of("spartacube", "cosmetic_allowed"));
        public static final PacketCodec<RegistryByteBuf, Allowed> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, Allowed::flags, Allowed::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Entry(UUID id, int flags) {
        public static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, Entry::id, PacketCodecs.VAR_INT, Entry::flags, Entry::new);
    }

    /** Serveur → client : cosmétiques actifs de tous les joueurs du launcher. */
    public record Active(List<Entry> entries) implements CustomPayload {
        public static final Id<Active> ID = new Id<>(Identifier.of("spartacube", "cosmetics"));
        public static final PacketCodec<RegistryByteBuf, Active> CODEC = PacketCodec.tuple(
                PacketCodecs.collection(ArrayList::new, Entry.CODEC, 1000), Active::entries, Active::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
