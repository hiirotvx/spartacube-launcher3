package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Barre de boss : masquée ou redimensionnée (centrée en haut de l'écran). */
@Mixin(BossBarHud.class)
public abstract class MixinBossBarHud {
    @Unique
    private boolean spartacube$pushed;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void spartacube$bossStart(DrawContext ctx, CallbackInfo ci) {
        spartacube$pushed = false;
        HudConfig cfg = HudConfig.get();
        if (!cfg.isEnabled(HudModule.BOSS_BAR)) return;
        var st = cfg.settings(HudModule.BOSS_BAR);
        if (st.mode == 1) { ci.cancel(); return; }
        float s = st.scale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(ctx.getScaledWindowWidth() / 2f * (1 - s), 0f);
        ctx.getMatrices().scale(s, s);
        spartacube$pushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void spartacube$bossEnd(DrawContext ctx, CallbackInfo ci) {
        if (spartacube$pushed) { ctx.getMatrices().popMatrix(); spartacube$pushed = false; }
    }
}
