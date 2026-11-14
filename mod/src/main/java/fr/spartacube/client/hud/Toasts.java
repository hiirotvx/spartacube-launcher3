package fr.spartacube.client.hud;

import fr.spartacube.client.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Notifications animées SpartaCube (en haut à droite) : events, votes, messages du serveur. */
public final class Toasts {
    public enum Type {
        INFO("fps", 0xFFFF8C2E), EVENT("join", 0xFFFFB02E), VOTE("join", 0xFF4ADE80), BOSS("armor", 0xFFE0324F), STAFF("server", 0xFF60A5FA);
        final String icon;
        final int color;
        Type(String icon, int color) { this.icon = icon; this.color = color; }

        static Type of(String s) {
            try { return valueOf(s.toUpperCase(java.util.Locale.ROOT)); } catch (Exception e) { return INFO; }
        }
    }

    private static final class Toast {
        final Type type;
        final Text title;
        final String text;
        final long start = System.currentTimeMillis();
        final long duration;
        float y = -1;

        Toast(Type type, String title, String text, long duration) {
            this.type = type;
            this.title = title(title);
            this.text = text;
            this.duration = duration;
        }
    }

    private static final Style TITLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("spartacube", "toast_title")));
    private static final Style BODY = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of("spartacube", "toast_text")));

    private static MutableText title(String s) { return Text.literal(s).setStyle(TITLE); }
    private static MutableText body(String s) { return Text.literal(s).setStyle(BODY); }

    private static final List<Toast> TOASTS = new ArrayList<>();
    private static long lastFrame;

    private Toasts() {}

    public static synchronized void show(String type, String title, String text) {
        String t = title == null ? "" : title.trim();
        String b = text == null ? "" : text.trim();
        if (b.isEmpty() && t.length() > 40) {
            // Pas de « | » : le premier mot sert de titre, le reste devient le message
            int sp = t.indexOf(' ');
            if (sp > 0 && sp <= 30) { b = t.substring(sp + 1); t = t.substring(0, sp); }
        }
        if (t.length() > 60) t = t.substring(0, 60);
        if (b.length() > 200) b = b.substring(0, 200);
        TOASTS.add(new Toast(Type.of(type), t, b, 6000));
        while (TOASTS.size() > 4) TOASTS.remove(0);
    }

    public static synchronized void render(DrawContext ctx, MinecraftClient mc) {
        if (TOASTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (System.nanoTime() - lastFrame) / 1_000_000_000f);
        lastFrame = System.nanoTime();
        int sw = mc.getWindow().getScaledWidth();
        // Largeur adaptée à l'écran : 45 % de la largeur, entre 150 et 280 px (GUI scale)
        int maxW = Math.max(150, Math.min(280, Math.round(sw * 0.45f)));
        float target = 8;
        Iterator<Toast> it = TOASTS.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            long age = now - t.start;
            if (age > t.duration + 400) { it.remove(); continue; }
            float in = Ui.easeOut(age / 300f);
            float out = age > t.duration ? Ui.easeOut((age - t.duration) / 400f) : 0f;
            float a = in * (1f - out);

            // Largeur au plus juste du contenu, puis retour à la ligne si ça dépasse
            Text bodyText = body(t.text);
            int natural = Math.max(Ui.width(t.title), t.text.isEmpty() ? 0 : Ui.width(bodyText));
            int pad = 10, iconBox = 18, gap = 8;
            int w = Math.max(130, Math.min(maxW, natural + pad * 2 + iconBox + gap));
            int textX0 = pad + iconBox + gap;
            int textW = w - textX0 - pad;
            List<net.minecraft.text.OrderedText> titleLines = mc.textRenderer.wrapLines(t.title, textW);
            List<net.minecraft.text.OrderedText> lines = t.text.isEmpty() ? List.of() : mc.textRenderer.wrapLines(bodyText, textW);
            int tl = Math.max(1, Math.min(2, titleLines.size()));
            int bl = Math.min(4, lines.size());
            int contentH = tl * 10 + (bl > 0 ? 3 + bl * 9 : 0);
            int h = Math.max(iconBox + pad * 2 - 2, contentH + pad * 2 - 1);

            if (t.y < 0) t.y = target;
            t.y = Ui.approach(t.y, target, 14f, dt);
            int x = sw - w - 8 + Math.round((1f - in + out) * 40);
            int y = Math.round(t.y);
            int c = t.type.color;

            // Ombre douce (plusieurs couches très transparentes)
            for (int i = 3; i >= 1; i--) {
                Ui.round(ctx, x - i, y - i + 1, x + w + i, y + h + i + 1, 8 + i, Ui.alpha(0x22000000, a));
            }
            // Fond sombre légèrement teinté de la couleur du type
            Ui.round(ctx, x, y, x + w, y + h, 8, Ui.alpha(Ui.mix(0xF2120A10, c | 0xF2000000, 0.06f), a));
            Ui.outline(ctx, x, y, x + w, y + h, 8, Ui.alpha(0x26FFFFFF, a));

            // Pastille d'icône teintée
            int ix = x + pad, iy = y + (h - iconBox) / 2;
            Ui.round(ctx, ix, iy, ix + iconBox, iy + iconBox, 6, Ui.alpha((c & 0x00FFFFFF) | 0x2E000000, a));
            Ui.icon(ctx, t.type.icon, ix + 3, iy + 3, iconBox - 6, Ui.alpha(c, a));

            // Textes
            int tx = x + textX0;
            int ly = y + (h - contentH) / 2 + 1;
            for (int i = 0; i < tl; i++) {
                ctx.drawText(mc.textRenderer, titleLines.get(i), tx, ly, Ui.alpha(0xFFFFFFFF, a), false);
                ly += 10;
            }
            if (bl > 0) ly += 3;
            for (int i = 0; i < bl; i++) {
                ctx.drawText(mc.textRenderer, lines.get(i), tx, ly, Ui.alpha(0xFFBFAFB6, a), false);
                ly += 9;
            }

            // Barre de temps restant : fine, arrondie, à l'intérieur de la carte
            float left = 1f - Math.min(1f, age / (float) t.duration);
            int bx1 = x + 8, bx2 = x + w - 8, by = y + h - 3;
            Ui.round(ctx, bx1, by, bx2, by + 1, 0, Ui.alpha(0x14FFFFFF, a));
            int fillTo = bx1 + Math.round((bx2 - bx1) * left);
            if (fillTo > bx1) ctx.fill(bx1, by, fillTo, by + 1, Ui.alpha(c, a * 0.85f));
            target += h + 6;
        }
    }
}
