package fr.spartacube.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Changement de compte en jeu : remplace la session du client. */
@Mixin(MinecraftClient.class)
public interface AccessorMinecraftClient {
    @Mutable
    @Accessor("session")
    void spartacube$setSession(Session session);

    @Mutable
    @Accessor("userApiService")
    void spartacube$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("profileKeys")
    void spartacube$setProfileKeys(ProfileKeys keys);
}
