package fr.spartacube.client.gui;

import fr.spartacube.client.hud.HudConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Panel staff : joueurs en ligne, téléportation, modes de jeu, sanctions et historique.
 * Chaque bouton envoie une commande au nom du joueur : c'est le serveur qui vérifie les permissions.
 */
public class StaffScreen extends Screen {
    /** kind : 0 normal, 1 sanction (double clic de confirmation), 2 lecture (ferme le panel pour lire le chat). */
    private record Action(String label, String command, int kind, boolean needsPlayer) {}

    private static final Object[][] SECTIONS = {
            {"Téléportation", new Action[]{
                    new Action("Aller vers lui", "tp {p}", 0, true),
                    new Action("L'amener ici", "tp {p} {me}", 0, true),
                    new Action("Inventaire", "invsee {p}", 0, true),
                    new Action("Ender chest", "enderchest {p}", 0, true)}},
            {"Moi", new Action[]{
                    new Action("Spectateur", "gamemode spectator", 0, false),
                    new Action("Survie", "gamemode survival", 0, false),
                    new Action("Créatif", "gamemode creative", 0, false),
                    new Action("Vanish", "vanish", 0, false),
                    new Action("Fly", "fly", 0, false)}},
            {"Sanctions", new Action[]{
                    new Action("Avertir", "warn {p} {r}", 1, true),
                    new Action("Kick", "kick {p} {r}", 1, true),
                    new Action("Mute", "mute {p} {d} {r}", 1, true),
                    new Action("Ban", "ban {p} {d} {r}", 1, true),
                    new Action("Unmute", "unmute {p}", 1, true),
                    new Action("Unban", "unban {p}", 1, true)}},
            {"Historique", new Action[]{
                    new Action("Historique", "history {p}", 2, true),
                    new Action("Avertissements", "warns {p}", 2, true),
                    new Action("Comptes liés", "alts {p}", 2, true)}}
    };

    private static final String[] DURATIONS = {"30m", "2h", "1d", "7d", "30d", ""};
    private static final int LIST_W = 150, PAD = 14, BTN_H = 18, GAP = 5;

    private final Screen parent;
    private final Ui.Mouse mouse = new Ui.Mouse();
    private final Map<String, Float> hover = new HashMap<>();
    private final long openedAt = Util.getMeasuringTimeMs();
    private long lastFrame = openedAt;
    private TextFieldWidget search, reason, duration;
    private String selected;
    private String confirm;
    private long confirmAt;
    private String status = "";
    private float scroll, scrollTarget;

    public StaffScreen(Screen parent) {
        super(Text.literal("Panel staff"));
        this.parent = parent;
    }

    private int pw() { return Math.min(560, this.width - 16); }
    private int px1() { return (this.width - pw()) / 2; }
    private int rx() { return px1() + LIST_W + PAD; }
    private int rx2() { return px1() + pw() - PAD; }

    /** Largeur d'un bouton : adaptée à son texte. */
    private int btnW(String label) {
        return Math.max(54, Ui.width(Ui.t(label)) + 18);
    }

    /** Hauteur occupée par une rangée de boutons qui passent à la ligne quand il n'y a plus de place. */
    private int flowHeight(Action[] actions) {
        int x = rx(), lines = 1;
        for (Action a : actions) {
            int w = btnW(a.kind() == 1 ? longest(a.label()) : a.label());
            if (x + w > rx2() && x > rx()) { x = rx(); lines++; }
            x += w + GAP;
        }
        return lines * (BTN_H + GAP);
    }

    /** Un bouton de sanction peut afficher « Confirmer ? » : on réserve la place du texte le plus long. */
    private static String longest(String label) {
        return label.length() >= "Confirmer ?".length() ? label : "Confirmer ?";
    }

    private int contentHeight() {
        int h = 48; // en-tête du joueur
        for (Object[] section : SECTIONS) h += 14 + flowHeight((Action[]) section[1]) + 6;
        return h + 18 + 20 + 8 + 20 + 8 + 14 + PAD; // libellés, champs, durées rapides, statut
    }

    private int ph() { return Math.min(this.height - 12, Math.max(250, contentHeight() + 4)); }
    private int py1() { return (this.height - ph()) / 2; }
    private int fieldsY() { return py1() + ph() - PAD - 14 - 8 - 20 - 8 - 20; }

    private TextFieldWidget field(int x, int y, int w, String placeholder, int max, String value) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x + 7, y + 6, w - 14, 12, Text.literal(placeholder));
        f.setDrawsBackground(false);
        f.setMaxLength(max);
        f.setPlaceholder(Ui.t(placeholder).styled(st -> st.withColor(0x80808A)));
        f.setText(value);
        this.addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        this.mouse.swallow();
        String s = this.search == null ? "" : this.search.getText();
        String r = this.reason == null ? "" : this.reason.getText();
        String d = this.duration == null ? "" : this.duration.getText();
        int x1 = px1(), y1 = py1(), fy = fieldsY();
        this.search = field(x1 + 10, y1 + 40, LIST_W - 20, "Rechercher…", 32, s);
        this.reason = field(rx(), fy, rx2() - rx() - 80 - GAP, "Raison de la sanction", 100, r);
        this.duration = field(rx2() - 80, fy, 80, "Durée", 12, d);
    }

    private List<PlayerListEntry> players() {
        List<PlayerListEntry> out = new ArrayList<>();
        if (this.client == null || this.client.getNetworkHandler() == null) return out;
        String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
        for (PlayerListEntry e : this.client.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (n == null || (!q.isEmpty() && !n.toLowerCase(Locale.ROOT).contains(q))) continue;
            out.add(e);
        }
        out.sort((a, b) -> a.getProfile().name().compareToIgnoreCase(b.getProfile().name()));
        return out;
    }

    private void run(Action a) {
        if (this.client == null || this.client.player == null) return;
        if (a.needsPlayer() && (this.selected == null || !this.selected.matches("[A-Za-z0-9_.*]{1,32}"))) {
            this.status = "Choisis d'abord un joueur dans la liste.";
            return;
        }
        long now = System.currentTimeMillis();
        if (a.kind() == 1 && !(a.label().equals(this.confirm) && now - this.confirmAt < 4000)) {
            this.confirm = a.label();
            this.confirmAt = now;
            this.status = "Clique encore pour confirmer : " + a.label() + " → " + this.selected;
            return;
        }
        this.confirm = null;
        String reasonText = this.reason.getText().replaceAll("[\\r\\n§]", " ").trim();
        String durationText = this.duration.getText().trim();
        if (!durationText.matches("[0-9a-zA-Z]{0,12}")) { this.status = "Durée invalide. Exemples : 30m, 2h, 7d."; return; }
        String cmd = a.command()
                .replace("{p}", this.selected == null ? "" : this.selected)
                .replace("{me}", this.client.player.getName().getString())
                .replace("{d}", durationText)
                .replace("{r}", reasonText)
                .replaceAll(" {2,}", " ").trim();
        this.client.player.networkHandler.sendChatCommand(cmd);
        this.status = "Envoyé : /" + cmd;
        if (a.kind() == 2) this.client.setScreen(null); // lecture : le résultat s'affiche dans le chat
    }

    private static int pingColor(int ms) {
        return ms <= 0 ? 0xFF8A8A94 : ms < 80 ? 0xFF4ADE80 : ms < 150 ? 0xFFFACC15 : ms < 300 ? 0xFFFB923C : 0xFFF87171;
    }

    private float hov(String id, boolean over, float dt) {
        float h = Ui.approach(this.hover.getOrDefault(id, 0f), over ? 1f : 0f, 14f, dt);
        this.hover.put(id, h);
        return h;
    }

    /** Texte coupé avec « … » s'il dépasse la largeur donnée. */
    private Text fit(String s, int maxW) {
        if (Ui.width(Ui.t(s)) <= maxW) return Ui.t(s);
        String cut = s;
        while (cut.length() > 1 && Ui.width(Ui.t(cut + "…")) > maxW) cut = cut.substring(0, cut.length() - 1);
        return Ui.t(cut + "…");
    }

    private void inputBox(DrawContext ctx, TextFieldWidget f, int x, int y, int w) {
        boolean focus = f.isFocused();
        Ui.round(ctx, x, y, x + w, y + 20, 6, 0x66000000);
        Ui.outline(ctx, x, y, x + w, y + 20, 6, focus ? Ui.accent(0xD0) : 0x26FFFFFF);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world != null) this.applyBlur(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.applyTheme();
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min(0.1f, (now - this.lastFrame) / 1000f);
        this.lastFrame = now;
        float open = Ui.easeOut(Math.min(1f, (now - this.openedAt) / 220f));
        boolean holo = HudConfig.get().themeHolo;
        this.mouse.update();
        boolean click = this.mouse.pressed;
        int x1 = px1(), y1 = py1(), x2 = x1 + pw(), y2 = y1 + ph();

        // Fond : voile, ombre, verre, colonne de gauche, liseré
        ctx.fill(0, 0, this.width, this.height, Ui.alpha(0x66000000, open));
        for (int i = 5; i >= 1; i--) Ui.round(ctx, x1 - i * 2, y1 - i * 2 + 3, x2 + i * 2, y2 + i * 2 + 3, 12 + i * 2, Ui.alpha(0x12000000, open));
        Ui.round(ctx, x1, y1, x2, y2, 12, Ui.alpha(Ui.panel(0xD0), open));
        Ui.round(ctx, x1, y1, x1 + LIST_W, y2, 12, Ui.alpha(0x38000000, open));
        for (int i = 0; i < 16; i++) ctx.fill(x1 + 8, y1 + 1 + i, x2 - 8, y1 + 2 + i, Ui.alpha(((int) (12 * (1f - i / 16f)) << 24) | 0xFFFFFF, open));
        Ui.outline(ctx, x1, y1, x2, y2, 12, Ui.alpha(0x30FFFFFF, open));
        for (int i = 0; i < pw() - 28; i += 2) {
            ctx.fill(x1 + 14 + i, y1, x1 + 16 + i, y1 + 1, Ui.alpha(holo ? Ui.holo(i / (float) pw(), 150) : Ui.accent(0x90), open));
        }

        // ----- Colonne de gauche : joueurs -----
        List<PlayerListEntry> list = players();
        Ui.icon(ctx, "solo", x1 + 12, y1 + 11, 14, Ui.alpha(Ui.ACCENT, open));
        Ui.text(ctx, Ui.b("Panel staff"), x1 + 31, y1 + 10, Ui.alpha(0xFFFFFFFF, open));
        Ui.text(ctx, Ui.t(list.size() + " joueur" + (list.size() > 1 ? "s" : "") + " en ligne"), x1 + 31, y1 + 22, Ui.alpha(Ui.MUTED, open));
        inputBox(ctx, this.search, x1 + 10, y1 + 40, LIST_W - 20);

        int ly1 = y1 + 68, ly2 = y2 - 10, rowH = 18;
        float maxScroll = Math.max(0, list.size() * rowH - (ly2 - ly1));
        this.scrollTarget = Math.max(0, Math.min(maxScroll, this.scrollTarget));
        this.scroll = Ui.approach(this.scroll, this.scrollTarget, 18f, dt);
        ctx.enableScissor(x1, ly1, x1 + LIST_W, ly2);
        for (int i = 0; i < list.size(); i++) {
            PlayerListEntry e = list.get(i);
            String n = e.getProfile().name();
            int ry = ly1 + i * rowH - Math.round(this.scroll);
            if (ry + rowH < ly1 || ry > ly2) continue;
            boolean over = Ui.inside(mouseX, mouseY, x1 + 6, Math.max(ry, ly1), x1 + LIST_W - 8, Math.min(ry + rowH - 1, ly2));
            boolean sel = n.equals(this.selected);
            float h = hov("p" + n, over, dt);
            if (sel) {
                Ui.round(ctx, x1 + 6, ry, x1 + LIST_W - 8, ry + rowH - 1, 5, Ui.accent(0x48));
                Ui.round(ctx, x1 + 6, ry + 4, x1 + 8, ry + rowH - 5, 1, Ui.ACCENT);
            } else if (h > 0.02f) {
                Ui.round(ctx, x1 + 6, ry, x1 + LIST_W - 8, ry + rowH - 1, 5, Ui.alpha(0x26FFFFFF, h));
            }
            try { PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), x1 + 12, ry + 3, 11); } catch (Throwable ignored) { /* tête indisponible */ }
            Ui.text(ctx, fit(n, LIST_W - 60), x1 + 28, ry + 5, sel ? 0xFFFFFFFF : Ui.TEXT);
            Ui.round(ctx, x1 + LIST_W - 20, ry + 6, x1 + LIST_W - 15, ry + 11, 2, pingColor(e.getLatency()));
            if (click && over) { Ui.click(); this.selected = n; this.confirm = null; this.status = ""; click = false; }
        }
        ctx.disableScissor();
        if (list.isEmpty()) Ui.text(ctx, Ui.t("Aucun joueur"), x1 + 14, ly1 + 6, Ui.MUTED);
        if (maxScroll > 0) {
            int track = ly2 - ly1, bar = Math.max(16, track * track / (list.size() * rowH));
            Ui.round(ctx, x1 + LIST_W - 5, ly1 + Math.round((track - bar) * (this.scroll / maxScroll)), x1 + LIST_W - 3, ly1 + Math.round((track - bar) * (this.scroll / maxScroll)) + bar, 1, Ui.accent(0x90));
        }

        // ----- Colonne de droite : joueur choisi -----
        int rx = rx(), rx2 = rx2();
        int y = y1 + PAD;
        PlayerListEntry sel = null;
        for (PlayerListEntry e : list) if (e.getProfile().name().equals(this.selected)) sel = e;
        Ui.round(ctx, rx, y, rx2, y + 34, 8, 0x22FFFFFF);
        if (this.selected != null) {
            if (sel != null) { try { PlayerSkinDrawer.draw(ctx, sel.getSkinTextures(), rx + 7, y + 7, 20); } catch (Throwable ignored) { /* */ } }
            Ui.text(ctx, Ui.b(this.selected), rx + 34, y + 7, 0xFFFFFFFF);
            if (sel != null) {
                int ms = sel.getLatency();
                Ui.round(ctx, rx + 34, y + 22, rx + 39, y + 27, 2, pingColor(ms));
                Text pt = Ui.t(ms + " ms");
                Ui.text(ctx, pt, rx + 43, y + 20, Ui.MUTED);
                if (sel.getGameMode() != null) {
                    Text gm = Ui.t(sel.getGameMode().getTranslatableName().getString());
                    int gx = rx + 43 + Ui.width(pt) + 8;
                    Ui.round(ctx, gx, y + 18, gx + Ui.width(gm) + 10, y + 30, 5, Ui.accent(0x38));
                    Ui.text(ctx, gm, gx + 5, y + 21, Ui.TEXT);
                }
            } else {
                Ui.text(ctx, Ui.t("Hors ligne ou masqué"), rx + 34, y + 20, Ui.MUTED);
            }
        } else {
            Ui.text(ctx, Ui.b("Aucun joueur choisi"), rx + 10, y + 7, Ui.TEXT);
            Ui.text(ctx, Ui.t("Clique sur un joueur dans la liste"), rx + 10, y + 20, Ui.MUTED);
        }
        y += 34 + 14;

        // ----- Actions : boutons à la largeur de leur texte, retour à la ligne automatique -----
        for (Object[] section : SECTIONS) {
            Ui.round(ctx, rx, y + 1, rx + 2, y + 9, 1, Ui.ACCENT);
            Ui.text(ctx, Ui.b((String) section[0]), rx + 7, y, Ui.TEXT);
            y += 14;
            Action[] actions = (Action[]) section[1];
            int bx = rx;
            for (Action a : actions) {
                boolean confirming = a.label().equals(this.confirm) && System.currentTimeMillis() - this.confirmAt < 4000;
                int w = btnW(a.kind() == 1 ? longest(a.label()) : a.label());
                if (bx + w > rx2 && bx > rx) { bx = rx; y += BTN_H + GAP; }
                boolean usable = !a.needsPlayer() || this.selected != null;
                boolean over = usable && Ui.inside(mouseX, mouseY, bx, y, bx + w, y + BTN_H);
                float h = hov(a.label(), over, dt);
                boolean danger = a.kind() == 1;
                int base = danger ? Ui.mix(0x30E0424F, 0xA0E0424F, confirming ? 1f : h) : Ui.mix(0x20FFFFFF, 0x42FFFFFF, h);
                if (h > 0.02f) Ui.round(ctx, bx + 1, y + 2, bx + w - 1, y + BTN_H + 2, 6, Ui.alpha(0x30000000, h));
                Ui.round(ctx, bx, y, bx + w, y + BTN_H, 6, Ui.alpha(base, usable ? 1f : 0.4f));
                Ui.outline(ctx, bx, y, bx + w, y + BTN_H, 6, Ui.alpha(danger ? Ui.mix(0x50E0424F, 0xFFFF7A84, h) : Ui.mix(0x16FFFFFF, Ui.accent(0xB0), h), usable ? 1f : 0.4f));
                if (over && holo && !danger) Ui.holoSheen(ctx, bx + 2, y + 2, bx + w - 2, y + BTN_H - 2, h * 0.6f);
                Text t = Ui.t(confirming ? "Confirmer ?" : a.label());
                Ui.text(ctx, t, bx + (w - Ui.width(t)) / 2, y + 5, usable ? (danger ? 0xFFFFD9DC : 0xFFFFFFFF) : Ui.MUTED);
                if (click && over) { Ui.click(); run(a); click = false; if (this.client.currentScreen != this) return; }
                bx += w + GAP;
            }
            y += BTN_H + GAP + 6;
        }

        // ----- Raison, durée, durées rapides, statut -----
        int fy = fieldsY();
        Ui.text(ctx, Ui.t("Raison"), rx + 2, fy - 11, Ui.MUTED);
        Ui.text(ctx, Ui.t("Durée"), rx2 - 78, fy - 11, Ui.MUTED);
        inputBox(ctx, this.reason, rx, fy, rx2 - rx - 80 - GAP);
        inputBox(ctx, this.duration, rx2 - 80, fy, 80);
        int cy = fy + 20 + 8, cx = rx;
        for (String d : DURATIONS) {
            String label = d.isEmpty() ? "Définitif" : d;
            int w = Ui.width(Ui.t(label)) + 14;
            if (cx + w > rx2) break;
            boolean active = this.duration.getText().trim().equals(d);
            boolean over = Ui.inside(mouseX, mouseY, cx, cy, cx + w, cy + 16);
            float h = hov("d" + label, over, dt);
            Ui.round(ctx, cx, cy, cx + w, cy + 16, 8, active ? Ui.accent(0x70) : Ui.mix(0x1CFFFFFF, 0x3CFFFFFF, h));
            Ui.text(ctx, Ui.t(label), cx + 7, cy + 4, active ? 0xFFFFFFFF : Ui.TEXT);
            if (click && over) { Ui.click(); this.duration.setText(d); click = false; }
            cx += w + 4;
        }
        String hint = this.status.isEmpty() ? "Mute et Ban utilisent la raison et la durée." : this.status;
        Ui.text(ctx, fit(hint, rx2 - rx), rx + 2, y2 - PAD - 9, this.status.isEmpty() ? Ui.MUTED : 0xFFFFD27F);

        super.render(ctx, mouseX, mouseY, delta); // champs de texte (fond dessiné ci-dessus)
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollTarget -= (float) verticalAmount * 36f;
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
