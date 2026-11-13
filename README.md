# SpartaCube Launcher

Launcher officiel de **spartacube.fr** : Minecraft Java + Fabric, connexion **Microsoft uniquement** (aucun mode crack).
Windows, macOS et Linux.

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
3. Pour sortir une version :
   ```bash
   npm version patch        # 1.0.0 → 1.0.1
   git push --follow-tags
   ```
   GitHub Actions compile les 3 plateformes et crée la Release. Les launchers installés se mettent à jour tout seuls.
4. Lien de téléchargement à mettre sur le site : `https://github.com/hiirotvx/spartacube-launcher3/releases/latest`.

Compiler à la main : `npm run dist:win`, `dist:mac` ou `dist:linux`. Les fichiers sont créés dans `dist/`.

## 4. Configuration (`launcher.config.json`)

| Clé | Rôle |
|---|---|
| `serverAddress` / `serverPort` | Adresse rejointe automatiquement et pingée |
| `remoteConfigUrl` | URL du `launcher.json` distant. Peut modifier : `minecraftVersion`, `fabricLoaderVersion`, `modpackManifestUrl`, `managedFolders`, `message`, `news`, `serverPort` |
| `minecraftVersion` / `fabricLoaderVersion` | Valeurs par défaut si le site est injoignable |
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
- Electron, electron-builder, electron-updater (MIT)

Minecraft est une marque de Mojang/Microsoft. Ce launcher n'est pas affilié à Mojang.
