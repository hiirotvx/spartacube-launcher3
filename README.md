# SpartaCube Launcher

Launcher officiel de **spartacube.fr** : Minecraft Java + Fabric, connexion **Microsoft uniquement** (aucun mode crack).
Windows, macOS et Linux.

## Onglets

- **Accueil** : actualités à gauche, sélecteur de version, bouton JOUER, statut du serveur.
- **Actualités** : toutes les news du serveur (texte, image, lien), gérées depuis `launcher.json`.
- **Mods** : mods installés par version (activer/désactiver, supprimer, ajouter un .jar), recherche et installation depuis Modrinth avec dépendances. Les mods du modpack serveur sont verrouillés 🔒.
- **Skin** : aperçu 3D, éditeur pixel (pinceau, gomme, remplissage, pipette, annuler), import PNG, copie du skin d'un joueur, bibliothèque locale, application directe sur le compte Minecraft et choix de la cape.
- **Voter** : tous les sites de vote (`voteSites`), minuteur par site, compteur du mois, pastille quand un vote est disponible.
- **Captures** : galerie des captures F2, copier (pour Discord), afficher, mettre à la corbeille.
- **Réglages** : RAM, qualité graphique, thème (auto : feu / hiver / Halloween), connexion auto, arguments JVM, compte.
- **Premier lancement guidé** : analyse du PC, RAM conseillée, préréglage graphique, choix minimap/shaders, raccourcis.
- **Profils de mods** : Performances, PvP, Shaders, Tout activé + profils perso.
- **Discord Rich Presence** : « Joue sur SpartaCube · 87/300 joueurs » + bouton (renseigner `discordClientId`).
- **Rejoindre directement** : destination (`destinations`) exécutée à la connexion.

Chaque version a son propre dossier (`instances/<version>`) : mods, options et mondes ne se mélangent pas. Au premier lancement d'une instance, le GUI scale est réglé sur 2.

## Mod SpartaCube Client (en jeu)

Le dossier `mod/` contient un mod Fabric 1.21.11 compilé par GitHub Actions et **intégré au launcher** (installé et verrouillé automatiquement sur la version officielle). Interface en police Montserrat (OFL), icônes originales, animations.

- **Menu principal SpartaCube** : fond animé (parallaxe), logo, « ⚔ Rejoindre SpartaCube » (play.spartacube.fr), Solo, Multijoueur, Options, Modules, barre d'icônes (éditer l'HUD, Mod Menu, site, quitter).
- **Shift droit en jeu** : menu des modules en cartes (ACTIVÉ / DÉSACTIVÉ, OPTIONS), catégories, recherche, options par module (taille, couleur, fond, ombre).
- **Modules** : FPS, CPS, Touches, Horloge, Mémoire, Coordonnées, Direction, Vitesse, Biome, Jour, Armure, Effets, Ping, Serveur, Scoreboard, Toggle Sprint (+ fly boost), Toggle Sneak, Zoom (touche C), Carte joueur au survol, Nametags SpartaCube.
- **Minimap** : Xaero's Minimap est installée automatiquement par le launcher.
- **Bouton « Voter »** dans le menu Échap : le launcher ouvre la page `voteUrl` (launcher.json) dans une fenêtre au-dessus du jeu.
- **Éditer l'HUD** : glisser-déposer, molette pour la taille, aimantation aux bords et au centre.
- **Pseudo en F5** : ton pseudo s'affiche au-dessus de ta tête en vue à la 3e personne.
- **Pastille SpartaCube dans la tab** : un petit « S » à droite du pseudo des joueurs qui utilisent le launcher (nécessite le plugin serveur ci-dessous).
- Réglages enregistrés dans `config/spartacube-client.json`.

### Plugin serveur SpartaCubeBadge

Le dossier `plugin/` contient un plugin Paper/Spigot 1.21 qui relaie la liste des joueurs équipés du launcher. Le fichier `SpartaCubeBadge.jar` est joint à chaque Release : place-le dans `plugins/` puis redémarre le serveur. Commande admin : `/launcherusers`.

## Ce que fait le launcher, tout seul

| Étape | Automatique |
|---|---|
| Connexion | Compte Microsoft officiel, session mémorisée (jeton chiffré par le système). Les comptes sans Minecraft Java sont refusés. |
| Java | Le runtime Java officiel de Mojang adapté à la version est téléchargé et vérifié (SHA-1). |
| Fabric | Le loader Fabric (dernier stable, ou version imposée) est installé. |
| Modpack | Les mods/configs sont synchronisés depuis `spartacube.fr` à chaque lancement ; les mods non autorisés dans `mods/` sont retirés. |
| Connexion serveur | Le jeu rejoint directement `spartacube.fr` (désactivable dans les paramètres). |
| Mises à jour du launcher | Téléchargées depuis GitHub Releases et installées à la fermeture. |
| Statut serveur | Joueurs en ligne et ping affichés en direct. |

Adresse du serveur : `play.spartacube.fr` (ping + connexion auto).

Le jeu est installé dans `%APPDATA%\.spartacube` (Windows), `~/Library/Application Support/.spartacube` (macOS) ou `~/.config/.spartacube` (Linux). Il reste séparé du `.minecraft` du joueur.

---

## 1. Tester en local

Prérequis : [Node.js 22+](https://nodejs.org).

```bash
npm install
npm start
```

## 2. Préparer le serveur web (spartacube.fr)

Crée un dossier `https://spartacube.fr/launcher/` qui contient :

```
launcher/
├── launcher.json      ← config distante (version, modpack, message, news)
├── manifest.json      ← liste des fichiers du modpack (générée)
└── files/             ← les fichiers du modpack
    ├── mods/…
    └── config/…
```

1. Copie `server-example/launcher/launcher.json` et adapte-le (version Minecraft, message, news).
2. Prépare un dossier local `modpack/` qui reproduit le dossier du jeu côté client (`mods/`, `config/`, `options.txt`, etc.).
3. Génère le manifest :
   ```bash
   npm run manifest -- ./modpack https://spartacube.fr/launcher/files --keep options.txt
   ```
   `--keep` signifie que le fichier est posé une fois, puis le joueur peut le modifier.
4. Envoie le contenu de `modpack/` dans `launcher/files/` et `manifest.json` dans `launcher/`.

**Pour mettre à jour les mods**, remplace les fichiers, relance l'étape 3 et ré-envoie le tout. Les joueurs sont mis à jour au prochain clic sur JOUER, sans nouvelle version du launcher.
**Pour changer de version Minecraft**, modifie `minecraftVersion` dans `launcher.json`.

> Le serveur web doit être en **HTTPS** : le launcher refuse tout le reste.

## 3. Publier le launcher (Windows / macOS / Linux)

1. Crée un dépôt GitHub `spartacube-launcher3` et pousse ce projet dedans.
2. Dans `package.json`, remplace `hiirotvx` par ton pseudo ou ton organisation GitHub.
3. Pour sortir une version, augmente `"version"` dans `package.json` (ex. 1.0.1 → 1.0.2) et pousse sur `main` :
   ```bash
   npm version patch --no-git-tag-version   # 1.3.1 → 1.3.2 → 1.3.3…
   git commit -am "v1.3.2" && git push
   ```
   GitHub Actions voit la nouvelle version, compile les 3 plateformes et crée la Release `v1.0.2`. Les launchers installés se mettent à jour tout seuls.
   (Bouton manuel : onglet **Actions → Build & Release → Run workflow**.)
4. Lien de téléchargement à mettre sur le site : `https://github.com/hiirotvx/spartacube-launcher3/releases/latest`.

Compiler à la main : `npm run dist:win`, `dist:mac` ou `dist:linux`. Les fichiers sont créés dans `dist/`.

## 4. Configuration (`launcher.config.json`)

| Clé | Rôle |
|---|---|
| `serverAddress` / `serverPort` | Adresse rejointe automatiquement et pingée |
| `remoteConfigUrl` | URL du `launcher.json` distant. Peut modifier toutes les clés ci-dessous + `message`, `serverPort`, `versions` |
| `minecraftVersion` / `fabricLoaderVersion` | Version officielle du serveur (1.21.11) et loader Fabric |
| `allowOtherVersions` | `true` : le sélecteur propose toutes les versions Fabric (sans modpack serveur) |
| `allowUserMods` | `false` : les joueurs ne peuvent pas ajouter leurs mods sur la version officielle |
| `defaultMods` | Mods Modrinth installés une fois par instance (Sodium, Lithium, Iris, Sodium Extra par défaut) |
| `voteUrl` | Page de vote ouverte par le bouton « Voter » du menu Échap |
| `voteSites` | `[{ "name", "url", "cooldownMin" }]` : sites du centre de vote |
| `destinations` | `[{ "name", "command" }]` : choix « Rejoindre directement » (ex. `/warp survie`) |
| `discordClientId` | Application ID Discord (Rich Presence), image `logo` dans Art Assets |
| `theme` | `auto`, `fire`, `snow`, `halloween` |
| `news` | Actualités : `title`, `text`, `date`, `tag`, `image` (HTTPS), `url` (HTTPS) |
| `managedFolders` | Dossiers dont les fichiers non listés sont supprimés (par défaut `mods`) |
| `microsoft.clientId` | Ton propre ID d'application Azure (voir ci-dessous) |
| `defaults` | RAM, connexion auto… par défaut |

## 5. Connexion Microsoft : à lire

Par défaut, la bibliothèque `msmc` utilise l'identifiant d'application du **launcher Minecraft officiel**. C'est ce que font la plupart des launchers communautaires, mais ce n'est pas l'usage prévu par Microsoft.
La méthode « propre » pour un launcher distribué au public :

1. Crée une application dans le [portail Azure](https://portal.azure.com) → *App registrations* (comptes personnels Microsoft, plateforme « Mobile and desktop », redirect `http://localhost`).
2. Demande l'accès à l'API Minecraft via le formulaire de Mojang : [aka.ms/mce-reviewappid](https://aka.ms/mce-reviewappid). L'approbation peut prendre plusieurs semaines.
3. Une fois l'application approuvée, renseigne `microsoft.clientId` dans `launcher.config.json`. **Ne mets jamais de *client secret*** dans le launcher.

## Crédits

- [minecraft-launcher-core](https://github.com/Pierce01/MinecraftLauncher-core) (MIT) : téléchargement et lancement du jeu
- [msmc](https://github.com/Hanro50/MSMC) (MIT) : authentification Microsoft
- [Fabric](https://fabricmc.net) : mod loader
- [skinview3d](https://github.com/bs-community/skinview3d) 3.4.2 (MIT), aperçu 3D des skins, copié dans `src/renderer/vendor`
- [Modrinth](https://modrinth.com) : recherche et téléchargement des mods
- Electron, electron-builder, electron-updater (MIT)

Minecraft est une marque de Mojang/Microsoft. Ce launcher n'est pas affilié à Mojang.
