package fr.spartacube.client.mixin;

import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import fr.spartacube.client.net.LauncherUsers;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
                && (!cfg.healthAimOnly || MinecraftClient.getInstance().targetedEntity == entity)) {
            Text base = out == null ? name : out;
            if (cfg.healthStyle == 0) out = withHearts(base, player);
            else out = Text.empty().append(base).append(Text.literal("  ")).append(health(player, cfg.healthStyle - 1));
        }
        if (out != null) cir.setReturnValue(out);
    }

    private static final Style HEARTS = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("spartacube", "hearts"))).withColor(0xFFFFFF);

    /** Décalage horizontal (positif ou négatif) fait de caractères d'espacement de la police « hearts ». */
    private static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(511, Math.abs(px));
        char first = px < 0 ? '\uF800' : '\uF820';
        for (int i = 0; i < 9; i++) if ((n & (1 << i)) != 0) sb.append((char) (first + i));
        return sb.toString();
    }

    /** Rangée de vrais cœurs centrée au-dessus du pseudo (la police dessine les cœurs une ligne plus haut). */
    private static MutableText withHearts(Text base, PlayerEntity p) {
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth()), abs = p.getAbsorptionAmount();
        int hearts = Math.max(1, Math.min(10, (int) Math.ceil(max / 2f)));
        int units = Math.max(hp > 0 ? 1 : 0, Math.round(hp / max * hearts * 2f));
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < hearts; i++) row.append(units >= (i + 1) * 2 ? '\uE000' : units == i * 2 + 1 ? '\uE001' : '\uE002');
        for (int i = 0; i < Math.min(5, (int) Math.ceil(abs / 2f)); i++) row.append('\uE003');
        Text heartsText = Text.literal(row.toString()).setStyle(HEARTS);
        var tr = MinecraftClient.getInstance().textRenderer;
        int w = tr.getWidth(base), h = tr.getWidth(heartsText);
        MutableText out = Text.empty();
        if (h <= w) {
            int pad = (w - h) / 2;
            out.append(base).append(Text.literal(shift(-(w - pad))).setStyle(HEARTS)).append(heartsText)
                    .append(Text.literal(shift(w - pad - h)).setStyle(HEARTS));
        } else {
            int pad = (h - w) / 2;
            out.append(heartsText).append(Text.literal(shift(-(h - pad))).setStyle(HEARTS)).append(base)
                    .append(Text.literal(shift(h - pad - w)).setStyle(HEARTS));
        }
        return out;
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
