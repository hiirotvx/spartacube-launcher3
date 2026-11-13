package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.net.LauncherUsers;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags SpartaCube : pastille « S » après le pseudo des joueurs équipés du launcher. */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void spartacube$badge(Entity entity, CallbackInfoReturnable<Text> cir) {
        Text name = cir.getReturnValue();
        if (name == null || !(entity instanceof PlayerEntity)) return;
        if (!HudConfig.get().isEnabled(HudModule.NAMETAG_STYLE) || !LauncherUsers.contains(entity.getUuid())) return;
        cir.setReturnValue(Text.empty().append(name).append(Text.literal(" ")).append(LauncherUsers.badge()));
    }
}
