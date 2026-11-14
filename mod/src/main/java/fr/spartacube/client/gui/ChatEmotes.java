package fr.spartacube.client.gui;

import fr.spartacube.client.hud.Emotes;
import fr.spartacube.client.hud.HudConfig;
import fr.spartacube.client.hud.HudModule;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Émojis dans le chat : remplacement de « :nom: », suggestions pendant la frappe, bouton et grille de choix. */
public final class ChatEmotes {
    private static final Pattern TYPING = Pattern.compile(":([a-z0-9_]{1,24})$");
    private static final int COLS = 10, CELL = 13;
    private static final Ui.Mouse MOUSE = new Ui.Mouse();
    private static boolean pickerOpen;

    private ChatEmotes() {}

    private static boolean enabled() {
        return HudConfig.get().isEnabled(HudModule.EMOTES);
    }

    public static void register() {
        // Filet de sécurité : remplacement à l'envoi (au cas où le champ n'a pas été converti)
        ClientSendMessageEvents.MODIFY_CHAT.register(msg -> enabled() ? Emotes.replace(msg) : msg);
        ClientSendMessageEvents.MODIFY_COMMAND.register(cmd -> enabled() ? Emotes.replace(cmd) : cmd);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            pickerOpen = false;
            MOUSE.swallow();
            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
                try {
                    if (enabled()) render(s, ctx, mouseX, mouseY);
                } catch (Throwable ignored) {
                    // jamais de crash à cause des émojis
                }
            });
        });
    }

    private static TextFieldWidget field(Screen screen) {
        for (Element e : screen.children()) if (e instanceof TextFieldWidget f) return f;
        return null;
    }

    private static void render(Screen screen, DrawContext ctx, int mx, int my) {
        TextFieldWidget field = field(screen);
        if (field == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        MOUSE.update();
        boolean click = MOUSE.pressed;
        int w = screen.width, h = screen.height;

        // « :eyes: » terminé → converti tout de suite dans le champ
        String text = field.getText();
        String replaced = Emotes.replace(text);
        if (!replaced.equals(text)) {
            field.setText(replaced);
            text = replaced;
        }

        // Bouton tout à droite de la barre de chat
        int bx1 = w - 15, by1 = h - 14, bx2 = w - 2, by2 = h - 2;
        boolean overBtn = Ui.inside(mx, my, bx1, by1, bx2, by2);
        ctx.fill(bx1, by1, bx2, by2, overBtn || pickerOpen ? 0xC0303038 : 0xA0101014);
        ctx.drawText(mc.textRenderer, Emotes.ALL.get("smile"), bx1 + 2, by1 + 2, 0xFFFFFFFF, false);
        if (click && overBtn) { pickerOpen = !pickerOpen; Ui.click(); click = false; }

        // Grille de tous les émojis
        if (pickerOpen) {
            List<String> names = new ArrayList<>(Emotes.ALL.keySet());
            int rows = (names.size() + COLS - 1) / COLS;
            int pw = COLS * CELL + 8, ph = rows * CELL + 8 + 11;
            int px2 = w - 2, px1 = px2 - pw, py2 = h - 18, py1 = py2 - ph;
            Ui.round(ctx, px1, py1, px2, py2, 5, Ui.panel(0xF0));
            Ui.outline(ctx, px1, py1, px2, py2, 5, 0x30FFFFFF);
            String hovered = null;
            for (int i = 0; i < names.size(); i++) {
                int cx = px1 + 4 + (i % COLS) * CELL, cy = py1 + 4 + (i / COLS) * CELL;
                boolean over = Ui.inside(mx, my, cx, cy, cx + CELL, cy + CELL);
                if (over) { ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0x40FFFFFF); hovered = names.get(i); }
                ctx.drawText(mc.textRenderer, Emotes.ALL.get(names.get(i)), cx + 2, cy + 2, 0xFFFFFFFF, false);
                if (click && over) { field.write(Emotes.ALL.get(names.get(i))); Ui.click(); click = false; }
            }
            ctx.drawText(mc.textRenderer, hovered != null ? ":" + hovered + ":" : "Clique sur un émoji", px1 + 5, py2 - 11, 0xFFB0B0B8, false);
            if (click && !Ui.inside(mx, my, px1, py1, px2, py2)) pickerOpen = false;
            return;
        }

        // Suggestions pendant la frappe : « :ey » → :eyes:
        int cursor = Math.max(0, Math.min(text.length(), field.getCursor()));
        Matcher mt = TYPING.matcher(text.substring(0, cursor));
        if (!mt.find()) return;
        List<String> sug = Emotes.suggest(mt.group(1), 6);
        if (sug.isEmpty()) return;
        int sw = 0;
        for (String n : sug) sw = Math.max(sw, mc.textRenderer.getWidth(":" + n + ":"));
        int sx1 = 2, sx2 = sx1 + sw + 24, sy2 = h - 16, sy1 = sy2 - sug.size() * 12 - 4;
        Ui.round(ctx, sx1, sy1, sx2, sy2, 4, Ui.panel(0xF0));
        Ui.outline(ctx, sx1, sy1, sx2, sy2, 4, 0x30FFFFFF);
        for (int i = 0; i < sug.size(); i++) {
            String n = sug.get(i);
            int ry = sy1 + 2 + i * 12;
            boolean over = Ui.inside(mx, my, sx1, ry, sx2, ry + 12);
            if (over) ctx.fill(sx1 + 2, ry, sx2 - 2, ry + 12, 0x40FFFFFF);
            ctx.drawText(mc.textRenderer, Emotes.ALL.get(n), sx1 + 5, ry + 2, 0xFFFFFFFF, false);
            ctx.drawText(mc.textRenderer, ":" + n + ":", sx1 + 18, ry + 2, over ? 0xFFFFFFFF : 0xFFB0B0B8, false);
            if (click && over) {
                String emoji = Emotes.ALL.get(n);
                String before = text.substring(0, mt.start());
                field.setText(before + emoji + text.substring(cursor));
                field.setCursor(before.length() + emoji.length(), false);
                Ui.click();
                click = false;
            }
        }
    }
}
