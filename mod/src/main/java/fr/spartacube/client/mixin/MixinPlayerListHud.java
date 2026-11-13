package fr.spartacube.client.mixin;

import fr.spartacube.client.net.LauncherUsers;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tab : pastille « S » à droite du pseudo des joueurs qui utilisent le launcher SpartaCube. */
@Mixin(PlayerListHud.class)
public abstract class MixinPlayerListHud {
    private static final Style BADGE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("spartacube", "badge")));

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void spartacube$badge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!LauncherUsers.contains(entry.getProfile().id())) return;
        cir.setReturnValue(Text.empty().append(cir.getReturnValue()).append(Text.literal(" ")).append(Text.literal("").setStyle(BADGE)));
    }
}
