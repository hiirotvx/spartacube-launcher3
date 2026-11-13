package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook : la souris fait tourner la caméra, pas le personnage. */
@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void spartacube$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Freelook.isActive() && (Object) this == MinecraftClient.getInstance().player) {
            Freelook.turn(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
