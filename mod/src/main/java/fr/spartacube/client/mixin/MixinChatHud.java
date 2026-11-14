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

    private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private static final net.minecraft.text.Style FONT = net.minecraft.text.Style.EMPTY.withFont(
            new net.minecraft.text.StyleSpriteSource.Font(net.minecraft.util.Identifier.of("spartacube", "chat")));

    /** Chat SpartaCube : police moderne, heure du message et barre de couleur devant chaque ligne. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true, require = 0)
    private net.minecraft.text.Text spartacube$style(net.minecraft.text.Text message) {
        HudConfig cfg = HudConfig.get();
        if (message == null || !cfg.isEnabled(HudModule.CHAT)) return message;
        int off = cfg.settings(HudModule.CHAT).mode; // bits : 1 = police d'origine, 2 = sans heure, 4 = sans barre
        if ((off & 7) == 7) return message;
        net.minecraft.text.MutableText out = net.minecraft.text.Text.empty();
        if ((off & 1) == 0) out.setStyle(FONT);
        if ((off & 4) == 0) out.append(net.minecraft.text.Text.literal("▎").styled(s -> s.withColor(fr.spartacube.client.gui.Ui.ACCENT & 0xFFFFFF)));
        if ((off & 2) == 0) out.append(net.minecraft.text.Text.literal(java.time.LocalTime.now().format(TIME) + " ").styled(s -> s.withColor(0x8F8F9A)));
        return out.append(message);
    }
}
