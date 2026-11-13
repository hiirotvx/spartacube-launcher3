package fr.spartacube.client.net;

import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Qui utilise le launcher SpartaCube (relayé par le plugin serveur SpartaCubeBadge). */
public final class LauncherUsers {
    private static final Set<UUID> USERS = ConcurrentHashMap.newKeySet();
    private static boolean helloSent;

    private static final Style BADGE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("spartacube", "badge")));

    private LauncherUsers() {}

    /** Pastille « S » (police bitmap spartacube:badge). */
    public static Text badge() {
        return Text.literal("\uE000").setStyle(BADGE);
    }

    public static boolean contains(UUID id) {
        if (id == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && id.equals(mc.player.getUuid())) return true; // soi-même
        return USERS.contains(id);
    }

    private static void sendHello() {
        if (helloSent || !ClientPlayNetworking.canSend(HelloPayload.ID)) return;
        String version = FabricLoader.getInstance().getModContainer("spartacube-client")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        ClientPlayNetworking.send(new HelloPayload(version));
        helloSent = true;
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(HelloPayload.ID, HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UsersPayload.ID, UsersPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(UsersPayload.ID, (payload, context) -> {
            USERS.clear();
            USERS.addAll(payload.users());
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendHello());
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(HelloPayload.ID.id())) sendHello();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            USERS.clear();
            helloSent = false;
        });
    }
}
