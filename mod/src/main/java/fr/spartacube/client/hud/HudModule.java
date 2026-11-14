package fr.spartacube.client.hud;

/** Tous les modules du menu SpartaCube (Shift droit). */
public enum HudModule {
    // ---- HUD ----
    FPS("FPS", "Images par seconde", Category.HUD, "fps", Kind.TEXT, true, 4, 4),
    CPS("CPS", "Clics par seconde (gauche | droit)", Category.HUD, "cps", Kind.TEXT, false, 4, 18),
    KEYSTROKES("Touches", "Affiche Z Q S D, espace et clics", Category.HUD, "keystrokes", Kind.SPECIAL, false, 4, 110),
    CLOCK("Horloge", "Heure réelle", Category.HUD, "clock", Kind.TEXT, false, 4, 32),
    PLAYTIME("Temps de jeu", "Durée de ta session en cours", Category.HUD, "clock", Kind.TEXT, false, 4, 172),
    STOPWATCH("Chronomètre", "Chronomètre manuel : une touche pour démarrer / mettre en pause", Category.HUD, "clock", Kind.TEXT, false, 4, 186),
    ITEM_COUNTER("Compteur d'objets", "Nombre total de l'objet tenu en main dans ton inventaire", Category.HUD, "armor", Kind.TEXT, false, 4, 200),
    COMBO("Combo", "Coups consécutifs portés sans te faire toucher", Category.HUD, "cps", Kind.TEXT, false, 4, 214),
    REACH("Portée", "Distance (en blocs) de ton dernier coup", Category.HUD, "direction", Kind.TEXT, false, 4, 228),
    COOLDOWNS("Temps de recharge", "Objets en recharge (perles de l'Ender, boucliers…)", Category.HUD, "clock", Kind.SPECIAL, false, -110, 120),
    MEMORY("Mémoire", "RAM utilisée par le jeu", Category.HUD, "memory", Kind.TEXT, false, 4, 46),
    // ---- Joueur ----
    COORDS("Coordonnées", "Position X / Y / Z", Category.PLAYER, "coords", Kind.TEXT, false, 4, 60),
    DIRECTION("Direction", "Orientation (Nord, Sud…)", Category.PLAYER, "direction", Kind.TEXT, false, 4, 74),
    SPEED("Vitesse", "Vitesse en blocs par seconde", Category.PLAYER, "speed", Kind.TEXT, false, 4, 88),
    BIOME("Biome", "Biome actuel", Category.PLAYER, "biome", Kind.TEXT, false, 4, 102),
    DAY("Jour", "Numéro du jour dans le monde", Category.PLAYER, "day", Kind.TEXT, false, 4, 116),
    ARMOR("Armure", "Armure et objet en main, avec leur état (%, barre…)", Category.PLAYER, "armor", Kind.SPECIAL, false, -40, -90),
    HEALTH("Vie des joueurs", "Affiche les cœurs d'un joueur au-dessus de sa tête quand tu le vises", Category.PLAYER, "armor", Kind.MECHANIC, true, 0, 0),
    SKIN3D("Skins 3D", "Relief sur les skins des joueurs (mod 3D Skin Layers)", Category.PLAYER, "solo", Kind.MECHANIC, false, 0, 0),
    NAMETAG_STYLE("Nametags SpartaCube", "Nametags aux couleurs de SpartaCube + pastille launcher", Category.PLAYER, "multi", Kind.MECHANIC, false, 0, 0),
    NAMETAG("Pseudo en F5", "Affiche ton pseudo au-dessus de ta tête en vue F5", Category.PLAYER, "solo", Kind.MECHANIC, true, 0, 0),
    EFFECTS("Effets de potion", "Effets actifs avec icône, durée et couleurs", Category.PLAYER, "fps", Kind.SPECIAL, false, -110, 40),
    SATURATION("Saturation", "Ta saturation (la réserve cachée derrière la barre de faim)", Category.PLAYER, "day", Kind.TEXT, false, 4, 158),
    // ---- Mécaniques ----
    TOGGLE_SPRINT("Toggle Sprint", "Sprint sans maintenir la touche", Category.MECHANIC, "speed", Kind.MECHANIC, false, 0, 0),
    FLY_BOOST("Fly Boost", "Vole plus vite (créatif ou /fly)", Category.MECHANIC, "join", Kind.MECHANIC, true, 0, 0),
    FULLBRIGHT("Fullbright", "Tout est éclairé, même la nuit et dans les grottes", Category.MECHANIC, "fps", Kind.MECHANIC, false, 0, 0),
    TOGGLE_SNEAK("Toggle Sneak", "S'accroupir reste activé sans maintenir la touche", Category.MECHANIC, "solo", Kind.MECHANIC, false, 0, 0),
    SNAPLOOK("Snaplook", "Maintiens une touche pour regarder derrière toi", Category.MECHANIC, "search", Kind.MECHANIC, false, 0, 0),
    NO_HURT_CAM("Sans secousse", "Supprime le tremblement de la caméra quand tu prends des dégâts", Category.MECHANIC, "move", Kind.MECHANIC, false, 0, 0),
    CROSSHAIR("Viseur", "Viseur personnalisé : forme, taille et couleur", Category.MECHANIC, "search", Kind.MECHANIC, false, 0, 0),
    CHAT("Chat SpartaCube", "Chat modernisé : police, heure des messages, barre de couleur, 2 000 messages gardés", Category.SERVER, "server", Kind.MECHANIC, true, 0, 0),
    EMOTES("Émojis", "Écris :eyes: dans le chat pour 👀, avec suggestions et grille d'émojis", Category.SERVER, "server", Kind.MECHANIC, true, 0, 0),
    TAB_PING("Ping en chiffres", "Liste des joueurs (Tab) : ping en millisecondes à la place des barres", Category.SERVER, "ping", Kind.MECHANIC, true, 0, 0),
    BOSS_BAR("Barre de boss", "Redimensionne ou masque la barre de vie des boss", Category.SERVER, "scoreboard", Kind.MECHANIC, false, 0, 0),
    TITLES("Titres", "Réduit ou agrandit les grands textes au centre de l'écran", Category.SERVER, "scoreboard", Kind.MECHANIC, false, 0, 0),
    ZOOM("Zoom", "Maintiens une touche pour zoomer (C par défaut)", Category.MECHANIC, "search", Kind.MECHANIC, true, 0, 0),
    FREELOOK("Freelook", "Regarde autour de toi sans tourner (Alt par défaut)", Category.MECHANIC, "direction", Kind.MECHANIC, true, 0, 0),
    SCREENSHOTS("Captures d'écran", "F2 : capture copiée dans le presse-papiers, avec une notification", Category.MECHANIC, "hud", Kind.MECHANIC, true, 0, 0),
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
