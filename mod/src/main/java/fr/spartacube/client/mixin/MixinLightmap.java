package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fullbright : la luminosité lue par la lightmap passe à 15 (sans toucher au réglage enregistré). */
@Mixin(LightmapTextureManager.class)
public abstract class MixinLightmap {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"), require = 0)
    private Object spartacube$fullbright(SimpleOption<?> option) {
        Object value = option.getValue();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && option == mc.options.getGamma() && HudConfig.get().isEnabled(HudModule.FULLBRIGHT)) {
            return 15.0;
        }
        return value;
    }
}
