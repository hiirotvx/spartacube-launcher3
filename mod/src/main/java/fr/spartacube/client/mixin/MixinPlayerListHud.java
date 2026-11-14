package fr.spartacube.client.mixin;

import fr.spartacube.client.net.LauncherUsers;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab : pastille « S » à droite du pseudo des joueurs qui utilisent le launcher SpartaCube. */
@Mixin(PlayerListHud.class)
public abstract class MixinPlayerListHud {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void spartacube$badge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!LauncherUsers.contains(entry.getProfile().id())) return;
        cir.setReturnValue(Text.empty().append(cir.getReturnValue()).append(LauncherUsers.badge()));
    }

    /** Ping en chiffres à la place des barres de connexion. */
    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void spartacube$pingNumber(net.minecraft.client.gui.DrawContext ctx, int width, int x, int y, PlayerListEntry entry,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!fr.spartacube.client.hud.HudConfig.get().isEnabled(fr.spartacube.client.hud.HudModule.TAB_PING)) return;
        int ms = entry.getLatency();
        String t = ms <= 0 ? "?" : String.valueOf(Math.min(ms, 9999));
        int col = ms <= 0 ? 0xFFAAAAAA : ms < 80 ? 0xFF55FF55 : ms < 150 ? 0xFFFFFF55 : ms < 300 ? 0xFFFFAA00 : 0xFFFF5555;
        var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        float s = 0.6f;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x + width - 1 - tr.getWidth(t) * s, y + 2f);
        ctx.getMatrices().scale(s, s);
        ctx.drawText(tr, t, 0, 0, col, true);
        ctx.getMatrices().popMatrix();
        ci.cancel();
    }
}
