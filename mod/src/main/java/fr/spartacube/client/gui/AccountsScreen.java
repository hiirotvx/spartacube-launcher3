package fr.spartacube.client.gui;

import fr.spartacube.client.hud.Toasts;
import fr.spartacube.client.net.Accounts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Écran « Comptes » : passer d'un compte Microsoft à l'autre sans relancer le jeu. */
public class AccountsScreen extends Screen {
    private final Screen parent;
    private List<Accounts.Account> accounts;
    private String status = "Chargement des comptes…";
    private boolean busy;

    public AccountsScreen(Screen parent) {
        super(Text.literal("Comptes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
        if (this.accounts == null) {
            Accounts.list(list -> {
                this.accounts = list == null ? List.of() : list;
                this.status = list == null ? "Le launcher SpartaCube doit rester ouvert pour changer de compte."
                        : list.isEmpty() ? "Aucun compte enregistré dans le launcher." : "";
                rebuild();
            });
        }
    }

    private void rebuild() {
        this.clearChildren();
        int y = this.height / 2 - 70;
        if (this.accounts != null) {
            for (Accounts.Account a : this.accounts) {
                ButtonWidget b = ButtonWidget.builder(Text.literal((a.active() ? "✔ " : "") + a.name()), w -> choose(a))
                        .dimensions(this.width / 2 - 100, y, 200, 20).build();
                b.active = !a.active() && !this.busy;
                this.addDrawableChild(b);
                y += 24;
            }
        }
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Retour"), w -> close())
                .dimensions(this.width / 2 - 100, Math.max(y + 12, this.height / 2 + 60), 200, 20).build());
    }

    private void choose(Accounts.Account a) {
        if (this.busy) return;
        this.busy = true;
        this.status = "Connexion à " + a.name() + "…";
        rebuild();
        Accounts.switchTo(a, error -> {
            this.busy = false;
            if (error == null) {
                Toasts.show("info", "Compte changé", "Tu joues maintenant avec " + a.name() + ".");
                close();
            } else {
                this.status = error;
                rebuild();
            }
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Les comptes s'ajoutent dans le launcher (clic sur ton pseudo)."), this.width / 2, this.height / 2 - 88, 0xFFA0A0A0);
        if (!this.status.isEmpty()) ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.status), this.width / 2, this.height / 2 + 92, 0xFFFFD27F);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
