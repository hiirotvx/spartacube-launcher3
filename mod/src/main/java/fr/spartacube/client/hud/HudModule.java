package fr.spartacube.client.hud;

/** Tous les modules du menu SpartaCube (Shift droit). */
public enum HudModule {
    // ---- HUD ----
    FPS("FPS", "Images par seconde", Category.HUD, "fps", Kind.TEXT, true, 4, 4),
    CPS("CPS", "Clics par seconde (gauche | droit)", Category.HUD, "cps", Kind.TEXT, false, 4, 18),
    KEYSTROKES("Touches", "Affiche Z Q S D, espace et clics", Category.HUD, "keystrokes", Kind.SPECIAL, false, 4, 110),
    CLOCK("Horloge", "Heure réelle", Category.HUD, "clock", Kind.TEXT, false, 4, 32),
    MEMORY("Mémoire", "RAM utilisée par le jeu", Category.HUD, "memory", Kind.TEXT, false, 4, 46),
    // ---- Joueur ----
    COORDS("Coordonnées", "Position X / Y / Z", Category.PLAYER, "coords", Kind.TEXT, false, 4, 60),
    DIRECTION("Direction", "Orientation (Nord, Sud…)", Category.PLAYER, "direction", Kind.TEXT, false, 4, 74),
    SPEED("Vitesse", "Vitesse en blocs par seconde", Category.PLAYER, "speed", Kind.TEXT, false, 4, 88),
    BIOME("Biome", "Biome actuel", Category.PLAYER, "biome", Kind.TEXT, false, 4, 102),
    DAY("Jour", "Numéro du jour dans le monde", Category.PLAYER, "day", Kind.TEXT, false, 4, 116),
    ARMOR("Armure", "Pièces d'armure et objet en main", Category.PLAYER, "armor", Kind.SPECIAL, false, -40, -90),
    PLAYER_CARD("Carte joueur", "Vise un joueur pour voir son pseudo et son titre", Category.PLAYER, "solo", Kind.MECHANIC, true, 0, 0),
    NAMETAG_STYLE("Nametags SpartaCube", "Nametags aux couleurs de SpartaCube + pastille launcher", Category.PLAYER, "multi", Kind.MECHANIC, false, 0, 0),
    NAMETAG("Pseudo en F5", "Affiche ton pseudo au-dessus de ta tête en vue F5", Category.PLAYER, "solo", Kind.MECHANIC, true, 0, 0),
    EFFECTS("Effets", "Effets de potion actifs et durée", Category.PLAYER, "fps", Kind.SPECIAL, false, -110, 40),
    // ---- Mécaniques ----
    TOGGLE_SPRINT("Toggle Sprint", "Sprint sans maintenir la touche", Category.MECHANIC, "speed", Kind.MECHANIC, false, 0, 0),
    FLY_BOOST("Fly Boost", "Vole plus vite (créatif ou /fly)", Category.MECHANIC, "join", Kind.MECHANIC, true, 0, 0),
    TOGGLE_SNEAK("Toggle Sneak", "S'accroupir reste activé sans maintenir la touche", Category.MECHANIC, "solo", Kind.MECHANIC, false, 0, 0),
    ZOOM("Zoom", "Maintiens C pour zoomer", Category.MECHANIC, "search", Kind.MECHANIC, true, 0, 0),
    FREELOOK("Freelook", "Maintiens Alt gauche pour regarder autour de toi", Category.MECHANIC, "direction", Kind.MECHANIC, true, 0, 0),
    LOADING_SCREEN("Écran de chargement", "Écran SpartaCube avec astuces pendant la connexion", Category.MECHANIC, "server", Kind.MECHANIC, true, 0, 0),
    NOTIFICATIONS("Notifications", "Notifications animées du serveur et rappels de vote", Category.SERVER, "join", Kind.MECHANIC, true, 0, 0),
    // ---- Cosmétiques (exclusifs au launcher) ----
    WINGS("Ailes de feu", "Ailes de flammes dans ton dos, visibles par les joueurs du launcher", Category.COSMETIC, "join", Kind.MECHANIC, false, 0, 0),
    AURA("Aura de flammes", "Flammes qui tournent autour de toi", Category.COSMETIC, "fps", Kind.MECHANIC, false, 0, 0),
    // ---- Serveur ----
    PING("Ping", "Latence avec le serveur", Category.SERVER, "ping", Kind.TEXT, true, 4, 130),
    SERVER("Serveur", "Adresse du serveur", Category.SERVER, "server", Kind.TEXT, false, 4, 144),
    SCOREBOARD("Scoreboard", "Affiche le scoreboard du serveur", Category.SERVER, "scoreboard", Kind.MECHANIC, true, 0, 0);

    public enum Category {
        ALL("Tous"), HUD("HUD"), PLAYER("Joueur"), MECHANIC("Mécaniques"), COSMETIC("Cosmétiques"), SERVER("Serveur");
        public final String label;
        Category(String label) { this.label = label; }
    }

    /** TEXT/SPECIAL : affichés et déplaçables ; MECHANIC : simple activation. */
    public enum Kind { TEXT, SPECIAL, MECHANIC }

    public final String label;
    public final String description;
    public final Category category;
    public final String icon;
    public final Kind kind;
    public final boolean defaultEnabled;
    /** Position par défaut ; une valeur négative est mesurée depuis le bord droit / bas. */
    public final int defaultX;
    public final int defaultY;

    HudModule(String label, String description, Category category, String icon, Kind kind, boolean defaultEnabled, int defaultX, int defaultY) {
        this.label = label;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.kind = kind;
        this.defaultEnabled = defaultEnabled;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
    }

    public boolean movable() {
        return kind != Kind.MECHANIC;
    }
}
