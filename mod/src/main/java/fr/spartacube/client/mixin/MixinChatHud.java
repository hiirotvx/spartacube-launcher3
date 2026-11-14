package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Chat étendu : 2 000 messages gardés au lieu de 100. */
@Mixin(ChatHud.class)
public abstract class MixinChatHud {
    @ModifyConstant(method = {"addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", "addVisibleMessage"},
            constant = @Constant(intValue = 100), require = 0)
    private int spartacube$longHistory(int max) {
        return HudConfig.get().isEnabled(HudModule.CHAT) ? 2000 : max;
    }
}
