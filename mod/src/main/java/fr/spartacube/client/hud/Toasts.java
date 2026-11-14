package fr.spartacube.client.hud;

import fr.spartacube.client.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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
            this.title = Ui.b(title);
            this.text = text;
            this.duration = duration;
        }
    }

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
            int natural = Math.max(Ui.width(t.title), t.text.isEmpty() ? 0 : Ui.width(Ui.t(t.text)));
            int w = Math.max(120, Math.min(maxW, natural + 40));
            int textW = w - 38;
            List<net.minecraft.text.OrderedText> titleLines = mc.textRenderer.wrapLines(t.title, textW);
            List<net.minecraft.text.OrderedText> lines = t.text.isEmpty() ? List.of() : mc.textRenderer.wrapLines(Ui.t(t.text), textW);
            int tl = Math.max(1, Math.min(2, titleLines.size()));
            int bl = Math.min(4, lines.size());
            int h = Math.max(28, 10 + tl * 10 + bl * 10 + (bl > 0 ? 2 : 0));

            if (t.y < 0) t.y = target;
            t.y = Ui.approach(t.y, target, 14f, dt);
            int x = sw - w - 8 + Math.round((1f - in + out) * 40);
            int y = Math.round(t.y);
            Ui.round(ctx, x, y, x + w, y + h, 6, Ui.alpha(0xE8140B12, a));
            Ui.round(ctx, x, y + 5, x + 2, y + h - 5, 1, Ui.alpha(t.type.color, a));
            Ui.icon(ctx, t.type.icon, x + 9, y + (h - 14) / 2, 14, Ui.alpha(t.type.color, a));
            int ly = y + 6;
            for (int i = 0; i < tl; i++) {
                ctx.drawText(mc.textRenderer, titleLines.get(i), x + 30, ly, Ui.alpha(0xFFFFFFFF, a), false);
                ly += 10;
            }
            if (bl > 0) ly += 2;
            for (int i = 0; i < bl; i++) {
                ctx.drawText(mc.textRenderer, lines.get(i), x + 30, ly, Ui.alpha(0xFFD9C8BE, a), false);
                ly += 10;
            }
            // barre de temps restant
            float left = 1f - Math.min(1f, age / (float) t.duration);
            ctx.fill(x + 6, y + h - 2, x + 6 + Math.round((w - 12) * left), y + h - 1, Ui.alpha(t.type.color, a * 0.7f));
            target += h + 6;
        }
    }
}
