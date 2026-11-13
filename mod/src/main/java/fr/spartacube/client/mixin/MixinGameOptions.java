package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags SpartaCube : fond des pseudos teinté rouge sombre au lieu du noir. */
@Mixin(GameOptions.class)
public abstract class MixinGameOptions {
    @Inject(method = "getTextBackgroundColor(F)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void spartacube$tint(float fallbackOpacity, CallbackInfoReturnable<Integer> cir) {
        if (!HudConfig.get().isEnabled(HudModule.NAMETAG_STYLE)) return;
        int c = cir.getReturnValueI();
        int a = (c >>> 24) & 0xFF;
        if (a == 0) return;
        cir.setReturnValue((Math.min(255, a + 24) << 24) | 0x3A0D16);
    }
}
