package fr.spartacube.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

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

    private final Screen parent;
    private final Ui.Mouse mouse = new Ui.Mouse();
    private final Map<String, Float> hover = new HashMap<>();
    private TextFieldWidget search, reason, duration;
    private String selected;
    private String confirm;
    private long confirmAt;
    private String status = "";
    private float scroll;

    public StaffScreen(Screen parent) {
        super(Text.literal("Panel staff"));
        this.parent = parent;
    }

    private int pw() { return Math.min(460, this.width - 16); }
    private int ph() { return Math.min(270, this.height - 12); }
    private int px1() { return (this.width - pw()) / 2; }
    private int py1() { return (this.height - ph()) / 2; }

    @Override
    protected void init() {
        this.mouse.swallow();
        int x1 = px1(), y1 = py1();
        int rx = x1 + 150;
        String s = this.search == null ? "" : this.search.getText();
        String r = this.reason == null ? "" : this.reason.getText();
        String d = this.duration == null ? "" : this.duration.getText();
        this.search = new TextFieldWidget(this.textRenderer, x1 + 12, y1 + 34, 126, 16, Text.literal("Rechercher"));
        this.search.setMaxLength(32);
        this.search.setText(s);
        this.reason = new TextFieldWidget(this.textRenderer, rx + 46, y1 + ph() - 42, pw() - 150 - 46 - 12 - 70, 16, Text.literal("Raison"));
        this.reason.setMaxLength(100);
        this.reason.setText(r);
        this.duration = new TextFieldWidget(this.textRenderer, x1 + pw() - 12 - 44, y1 + ph() - 42, 44, 16, Text.literal("Durée"));
        this.duration.setMaxLength(12);
        this.duration.setText(d);
        this.addDrawableChild(this.search);
        this.addDrawableChild(this.reason);
        this.addDrawableChild(this.duration);
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
            this.status = "Clique encore sur « " + a.label() + " » pour confirmer (" + this.selected + ").";
            return;
        }
        this.confirm = null;
        String reasonText = this.reason.getText().replaceAll("[\\r\\n§]", " ").trim();
        String durationText = this.duration.getText().trim();
        if (!durationText.matches("[0-9a-zA-Z]{0,12}")) { this.status = "Durée invalide (exemples : 30m, 2h, 7d ; vide = définitif)."; return; }
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

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world != null) this.applyBlur(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.applyTheme();
        this.mouse.update();
        boolean click = this.mouse.pressed;
        int x1 = px1(), y1 = py1(), x2 = x1 + pw(), y2 = y1 + ph();

        ctx.fill(0, 0, this.width, this.height, 0x60000000);
        Ui.round(ctx, x1, y1, x2, y2, 10, Ui.panel(0xD8));
        Ui.round(ctx, x1, y1, x1 + 146, y2, 10, 0x30000000);
        Ui.outline(ctx, x1, y1, x2, y2, 10, 0x30FFFFFF);
        ctx.fill(x1 + 12, y1, x2 - 12, y1 + 1, Ui.accent(0x90));
        List<PlayerListEntry> list = players();
        Ui.text(ctx, Ui.b("Panel staff"), x1 + 12, y1 + 10, 0xFFFFFFFF);
        Ui.text(ctx, Ui.t(list.size() + " en ligne"), x1 + 12, y1 + 22, Ui.MUTED);

        // Liste des joueurs
        int ly1 = y1 + 56, ly2 = y2 - 10;
        int visible = Math.max(1, (ly2 - ly1) / 14);
        int maxScroll = Math.max(0, list.size() - visible);
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll));
        int first = Math.round(this.scroll);
        for (int i = 0; i < visible && first + i < list.size(); i++) {
            PlayerListEntry e = list.get(first + i);
            String n = e.getProfile().name();
            int ry = ly1 + i * 14;
            boolean over = Ui.inside(mouseX, mouseY, x1 + 8, ry, x1 + 140, ry + 13);
            boolean sel = n.equals(this.selected);
            if (sel) Ui.round(ctx, x1 + 8, ry, x1 + 140, ry + 13, 3, Ui.accent(0x50));
            else if (over) Ui.round(ctx, x1 + 8, ry, x1 + 140, ry + 13, 3, 0x30FFFFFF);
            Ui.text(ctx, Ui.t(n), x1 + 12, ry + 3, sel ? 0xFFFFFFFF : Ui.TEXT);
            Text ping = Ui.t(e.getLatency() + " ms");
            Ui.text(ctx, ping, x1 + 138 - Ui.width(ping), ry + 3, Ui.MUTED);
            if (click && over) { Ui.click(); this.selected = n; this.confirm = null; this.status = ""; click = false; }
        }

        // En-tête du joueur choisi
        int rx = x1 + 158;
        if (this.selected != null) {
            Ui.text(ctx, Ui.b(this.selected), rx, y1 + 10, Ui.ACCENT);
            String info = "";
            for (PlayerListEntry e : list) if (e.getProfile().name().equals(this.selected)) {
                info = e.getLatency() + " ms" + (e.getGameMode() != null ? "  ·  " + e.getGameMode().getId() : "");
            }
            Ui.text(ctx, Ui.t(info), rx, y1 + 22, Ui.MUTED);
        } else {
            Ui.text(ctx, Ui.t("Choisis un joueur à gauche"), rx, y1 + 14, Ui.MUTED);
        }

        // Actions
        int y = y1 + 38;
        int cols = 4, gap = 4;
        int bw = (x2 - 12 - rx - (cols - 1) * gap) / cols;
        for (Object[] section : SECTIONS) {
            Ui.text(ctx, Ui.b(((String) section[0]).toUpperCase(Locale.ROOT)), rx, y, Ui.ACCENT);
            y += 11;
            Action[] actions = (Action[]) section[1];
            for (int i = 0; i < actions.length; i++) {
                Action a = actions[i];
                int bx = rx + (i % cols) * (bw + gap), byy = y + (i / cols) * 20;
                boolean usable = !a.needsPlayer() || this.selected != null;
                boolean over = usable && Ui.inside(mouseX, mouseY, bx, byy, bx + bw, byy + 17);
                String id = a.label();
                float h = Ui.approach(this.hover.getOrDefault(id, 0f), over ? 1f : 0f, 14f, 0.016f);
                this.hover.put(id, h);
                boolean confirming = id.equals(this.confirm) && System.currentTimeMillis() - this.confirmAt < 4000;
                int base = a.kind() == 1 ? Ui.mix(0x38E0424F, 0x90E0424F, confirming ? 1f : h) : Ui.mix(0x24FFFFFF, 0x48FFFFFF, h);
                Ui.round(ctx, bx, byy, bx + bw, byy + 17, 4, Ui.alpha(base, usable ? 1f : 0.4f));
                Text t = Ui.t(confirming ? "Confirmer ?" : a.label());
                Ui.text(ctx, t, bx + (bw - Ui.width(t)) / 2, byy + 5, usable ? 0xFFFFFFFF : Ui.MUTED);
                if (click && over) { Ui.click(); run(a); click = false; if (this.client.currentScreen != this) return; }
            }
            y += ((actions.length + cols - 1) / cols) * 20 + 5;
        }

        // Raison et durée
        Ui.text(ctx, Ui.t("Raison"), rx, y2 - 38, Ui.MUTED);
        Ui.text(ctx, Ui.t("Durée"), x2 - 12 - 44 - 30, y2 - 38, Ui.MUTED);
        Ui.text(ctx, Ui.t(this.status.isEmpty() ? "Durée : 30m, 2h, 7d… (vide = définitif). Mute et Ban utilisent la raison et la durée." : this.status), rx, y2 - 18, this.status.isEmpty() ? Ui.MUTED : 0xFFFFD27F);

        super.render(ctx, mouseX, mouseY, delta); // champs de texte
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scroll -= (float) verticalAmount * 2f;
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
