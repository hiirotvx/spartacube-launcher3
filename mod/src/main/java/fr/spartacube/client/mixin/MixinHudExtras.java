package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Titres : taille réglable des grands textes au centre de l'écran. */
@Mixin(InGameHud.class)
public abstract class MixinHudExtras {
    @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), require = 0)
    private void spartacube$titleScaleStart(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        float s = HudConfig.get().isEnabled(HudModule.TITLES) ? HudConfig.get().settings(HudModule.TITLES).scale : 1f;
        float cx = ctx.getScaledWindowWidth() / 2f, cy = ctx.getScaledWindowHeight() / 2f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx * (1 - s), cy * (1 - s));
        ctx.getMatrices().scale(s, s);
    }

    @Inject(method = "renderTitleAndSubtitle", at = @At("RETURN"), require = 0)
    private void spartacube$titleScaleEnd(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        ctx.getMatrices().popMatrix();
    }
}
