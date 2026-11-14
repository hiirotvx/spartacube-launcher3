package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.net.LauncherUsers;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
        if (name == null || !(entity instanceof PlayerEntity player)) return;
        HudConfig cfg = HudConfig.get();
        MutableText out = null;
        if (cfg.isEnabled(HudModule.NAMETAG_STYLE) && LauncherUsers.contains(entity.getUuid())) {
            out = Text.empty().append(name).append(Text.literal(" ")).append(LauncherUsers.badge());
        }
        if (cfg.isEnabled(HudModule.HEALTH) && entity != MinecraftClient.getInstance().player
                && (!cfg.healthTargetOnly || MinecraftClient.getInstance().targetedEntity == entity)) {
            if (out == null) out = Text.empty().append(name);
            out.append(Text.literal("  ")).append(health(player, cfg.healthMode));
        }
        if (out != null) cir.setReturnValue(out);
    }

    private static Text health(PlayerEntity p, int mode) {
        float hp = p.getHealth(), max = p.getMaxHealth(), abs = p.getAbsorptionAmount();
        if (mode == 1) {
            MutableText t = Text.literal(Math.round(hp) + " / " + Math.round(max) + " PV").formatted(Formatting.RED);
            if (abs > 0) t.append(Text.literal(" +" + Math.round(abs)).formatted(Formatting.GOLD));
            return t;
        }
        if (mode == 2) {
            int hearts = Math.max(1, Math.min(10, Math.round(max / 2f)));
            int full = Math.round(hp / 2f);
            MutableText t = Text.empty();
            for (int i = 0; i < hearts; i++) t.append(Text.literal("❤").formatted(i < full ? Formatting.RED : Formatting.DARK_GRAY));
            if (abs > 0) t.append(Text.literal(" +" + Math.round(abs / 2f) + "❤").formatted(Formatting.GOLD));
            return t;
        }
        float h = Math.round(hp / 2f * 2f) / 2f; // demi-cœurs
        String v = h == Math.floor(h) ? String.valueOf((int) h) : String.valueOf(h);
        MutableText t = Text.literal("❤ " + v).formatted(Formatting.RED);
        if (abs > 0) t.append(Text.literal(" +" + Math.round(abs / 2f)).formatted(Formatting.GOLD));
        return t;
    }
}
