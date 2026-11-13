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
        String t = title == null ? "" : title.length() > 60 ? title.substring(0, 60) : title;
        String b = text == null ? "" : text.length() > 140 ? text.substring(0, 140) : text;
        TOASTS.add(new Toast(Type.of(type), t, b, 6000));
        while (TOASTS.size() > 4) TOASTS.remove(0);
    }

    public static synchronized void render(DrawContext ctx, MinecraftClient mc) {
        if (TOASTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (System.nanoTime() - lastFrame) / 1_000_000_000f);
        lastFrame = System.nanoTime();
        int sw = mc.getWindow().getScaledWidth();
        int w = 190;
        float target = 8;
        Iterator<Toast> it = TOASTS.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            long age = now - t.start;
            if (age > t.duration + 400) { it.remove(); continue; }
            float in = Ui.easeOut(age / 300f);
            float out = age > t.duration ? Ui.easeOut((age - t.duration) / 400f) : 0f;
            float a = in * (1f - out);
            List<net.minecraft.text.OrderedText> lines = mc.textRenderer.wrapLines(Ui.t(t.text), w - 36);
            int h = 20 + Math.max(1, Math.min(3, lines.size())) * 10;
            if (t.y < 0) t.y = target;
            t.y = Ui.approach(t.y, target, 14f, dt);
            int x = sw - w - 8 + Math.round((1f - in + out) * 40);
            int y = Math.round(t.y);
            Ui.round(ctx, x, y, x + w, y + h, 6, Ui.alpha(0xE8140B12, a));
            Ui.round(ctx, x, y + 5, x + 2, y + h - 5, 1, Ui.alpha(t.type.color, a));
            Ui.icon(ctx, t.type.icon, x + 9, y + 8, 14, Ui.alpha(t.type.color, a));
            Ui.text(ctx, t.title, x + 30, y + 6, Ui.alpha(0xFFFFFFFF, a));
            int ly = y + 17;
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
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
