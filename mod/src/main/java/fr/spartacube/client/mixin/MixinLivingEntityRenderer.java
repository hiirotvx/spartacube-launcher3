package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pseudo en F5 : affiche son propre pseudo en vue à la 3e personne. */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true, require = 0)
    private void spartacube$ownNametag(LivingEntity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == mc.player && mc.options.getPerspective() != Perspective.FIRST_PERSON
                && !mc.options.hudHidden && !entity.isInvisible()
                && HudConfig.get().isEnabled(HudModule.NAMETAG)) {
            cir.setReturnValue(true);
        }
    }
}
