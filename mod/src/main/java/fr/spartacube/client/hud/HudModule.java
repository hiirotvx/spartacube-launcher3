package fr.spartacube.client.hud;

/** Les modules d'HUD disponibles dans le menu Shift droit. */
public enum HudModule {
    FPS("FPS", "Images par seconde", true, 4, 4),
    PING("Ping", "Latence avec le serveur", true, 4, 18),
    CPS("CPS", "Clics par seconde (gauche | droit)", false, 4, 32),
    COORDS("Coordonnées", "Position X / Y / Z", false, 4, 46),
    DIRECTION("Direction", "Orientation (Nord, Sud…)", false, 4, 60),
    CLOCK("Horloge", "Heure réelle", false, 4, 74),
    SERVER("Serveur", "Adresse du serveur", false, 4, 88),
    KEYSTROKES("Touches", "Affiche Z Q S D, espace et clics", false, 4, 110),
    SCOREBOARD("Scoreboard", "Affiche le scoreboard du serveur", true, -1, -1);

    public final String label;
    public final String description;
    public final boolean defaultEnabled;
    public final int defaultX;
    public final int defaultY;

    HudModule(String label, String description, boolean defaultEnabled, int defaultX, int defaultY) {
        this.label = label;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
    }

    /** Les modules qui s'affichent sous forme de texte déplaçable. */
    public boolean movable() {
        return this != SCOREBOARD;
    }
}
