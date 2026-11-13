package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.Freelook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook : la caméra utilise l'angle libre au lieu de celui du joueur. */
@Mixin(Camera.class)
public abstract class MixinCamera {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"), require = 0)
    private float spartacube$yaw(Entity entity, float tickDelta) {
        return Freelook.isActive() ? Freelook.yaw() : entity.getYaw(tickDelta);
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"), require = 0)
    private float spartacube$pitch(Entity entity, float tickDelta) {
        return Freelook.isActive() ? Freelook.pitch() : entity.getPitch(tickDelta);
    }
}
