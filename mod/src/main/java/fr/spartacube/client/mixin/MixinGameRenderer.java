package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.Mechanics;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom : réduit le champ de vision calculé par le jeu. require = 0 → jamais de crash si la méthode change. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void spartacube$zoom(CallbackInfoReturnable<Float> cir) {
        float f = Mechanics.fovFactor();
        if (f < 0.999f) cir.setReturnValue(cir.getReturnValue() * f);
    }
}
