'use strict';
const path = require('path');
const os = require('os');
const { app, BrowserWindow, ipcMain, shell, dialog } = require('electron');
const log = require('electron-log/main');
const { autoUpdater } = require('electron-updater');

const embedded = require('../../launcher.config.json');
const { getJson } = require('./http');
const store = require('./store');
const auth = require('./auth');
const game = require('./game');
const mods = require('./mods');
const skin = require('./skin');
const { listVersions, isSafeVersion } = require('./versions');
const { ping } = require('./status');
const bridge = require('./bridge');

log.initialize();
log.transports.file.level = 'info';

// Une seule instance du launcher à la fois
if (!app.requestSingleInstanceLock()) {
  app.quit();
}

let win = null;
let cfg = { ...embedded };
let settings = null;

// Clés que la config distante (spartacube.fr/launcher/launcher.json) a le droit de modifier.
const REMOTE_KEYS = [
  'minecraftVersion', 'fabricLoaderVersion', 'modpackManifestUrl', 'managedFolders',
  'news', 'message', 'serverAddress', 'serverPort', 'versions', 'allowOtherVersions', 'allowUserMods', 'defaultMods', 'voteUrl'
];

async function loadRemoteConfig() {
  if (!embedded.remoteConfigUrl) return;
  try {
    const remote = await getJson(embedded.remoteConfigUrl);
    for (const k of REMOTE_KEYS) if (remote[k] !== undefined) cfg[k] = remote[k];
    log.info('Config distante chargée');
    if (cfg.voteUrl) bridge.setVoteUrl(cfg.voteUrl);
  } catch (e) {
    log.warn(`Config distante indisponible, config embarquée utilisée : ${e.message}`);
  }
}

/** Nettoie les news (texte brut uniquement, liens/images en HTTPS). */
function cleanNews(list) {
  const httpsOrNull = u => { try { return new URL(u).protocol === 'https:' ? String(u) : null; } catch { return null; } };
  return (Array.isArray(list) ? list : []).slice(0, 30).map((n, i) => ({
    id: i,
    title: String(n.title || '').slice(0, 120),
    text: String(n.text || '').slice(0, 4000),
    date: String(n.date || '').slice(0, 40),
    tag: String(n.tag || '').slice(0, 24),
    image: httpsOrNull(n.image),
    url: httpsOrNull(n.url)
  }));
}

function send(channel, data) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, data);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 720,
    minWidth: 1040,
    minHeight: 640,
    frame: false,
    backgroundColor: '#140b12',
    show: false,
    icon: path.join(__dirname, '../renderer/assets/icon.png'),
    webPreferences: {
      preload: path.join(__dirname, '../preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  win.loadFile(path.join(__dirname, '../renderer/index.html'));
  win.once('ready-to-show', () => win.show());

  // Interdit toute navigation / ouverture de fenêtre depuis l'interface
  win.webContents.on('will-navigate', e => e.preventDefault());
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
}

function setupUpdater() {
  if (!app.isPackaged) return;
  autoUpdater.logger = log;
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on('update-available', i => send('update', { state: 'downloading', version: i.version }));
  autoUpdater.on('download-progress', p => send('update', { state: 'downloading', percent: Math.round(p.percent) }));
  autoUpdater.on('update-downloaded', i => send('update', { state: 'ready', version: i.version }));
  autoUpdater.on('error', e => log.warn(`Mise à jour : ${e.message}`));
  autoUpdater.checkForUpdates().catch(() => {});
  setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 60 * 60 * 1000);
}

/** Enveloppe un handler : renvoie { ok, data } ou { ok:false, error } au lieu de lever. */
function handle(channel, fn) {
  ipcMain.handle(channel, async (_e, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (e) {
      log.warn(`${channel} : ${e && e.message}`);
      return { ok: false, error: (e && e.message) || String(e) };
    }
  });
}

function versionArg(v) {
  if (!isSafeVersion(v)) throw new Error('Version invalide');
  if (cfg.allowOtherVersions === false && v !== cfg.minecraftVersion) throw new Error('Version non autorisée');
  return v;
}

function ensureNotRunning() {
  if (game.isRunning()) throw new Error('Ferme Minecraft avant de modifier les mods');
}

function registerIpc() {
  ipcMain.handle('init', async () => {
    await loadRemoteConfig();
    const profile = await auth.restore(cfg);
    return {
      appVersion: app.getVersion(),
      serverName: cfg.serverName,
      serverAddress: cfg.serverAddress,
      officialVersion: cfg.minecraftVersion,
      selectedVersion: game.selectedVersion(cfg, settings),
      allowUserMods: cfg.allowUserMods !== false,
      website: cfg.website,
      message: typeof cfg.message === 'string' ? cfg.message.slice(0, 500) : '',
      news: cleanNews(cfg.news),
      totalRamMb: Math.floor(os.totalmem() / 1024 / 1024),
      settings,
      profile
    };
  });

  handle('versions', () => listVersions(cfg));

  ipcMain.handle('login', async () => {
    try {
      return { ok: true, profile: await auth.login(cfg) };
    } catch (e) {
      const msg = typeof e === 'string' ? e : (e && (e.message || e.name)) || 'Connexion annulée';
      log.warn(`Connexion : ${msg}`);
      return { ok: false, error: msg === 'error.gui.closed' ? 'Connexion annulée' : msg };
    }
  });

  ipcMain.handle('logout', async () => { auth.logout(); return true; });

  ipcMain.handle('save-settings', async (_e, s) => {
    const clean = {
      ramMinMb: Math.max(512, Math.min(65536, parseInt(s.ramMinMb, 10) || 2048)),
      ramMaxMb: Math.max(1024, Math.min(65536, parseInt(s.ramMaxMb, 10) || 4096)),
      autoConnect: !!s.autoConnect,
      closeLauncherOnStart: !!s.closeLauncherOnStart,
      jvmArgs: String(s.jvmArgs || '').slice(0, 500),
      selectedVersion: isSafeVersion(s.selectedVersion) ? s.selectedVersion : ''
    };
    if (clean.ramMinMb > clean.ramMaxMb) clean.ramMinMb = clean.ramMaxMb;
    settings = clean;
    store.writeSettings(settings);
    return settings;
  });

  ipcMain.handle('play', async () => {
    try {
      await game.launch(cfg, settings, (ev, data) => {
        send(ev, data);
        if (ev === 'game-started' && settings.closeLauncherOnStart && win) win.hide();
        if (ev === 'game-closed' && win) win.show();
      });
      return { ok: true };
    } catch (e) {
      log.error(e);
      return { ok: false, error: e.message || String(e) };
    }
  });

  ipcMain.handle('status', async () => ping(cfg.serverAddress, cfg.serverPort || 25565));

  /* ----- Mods ----- */
  handle('mods:list', v => mods.listMods(game.gameDirFor(cfg, versionArg(v))));
  handle('mods:toggle', (v, file, enabled) => { ensureNotRunning(); return mods.toggleMod(game.gameDirFor(cfg, versionArg(v)), file, !!enabled); });
  handle('mods:delete', (v, file) => { ensureNotRunning(); return mods.deleteMod(game.gameDirFor(cfg, versionArg(v)), file); });
  handle('mods:add', async v => {
    ensureNotRunning();
    if (cfg.allowUserMods === false && v === cfg.minecraftVersion) throw new Error('Le serveur n\'autorise pas les mods personnels');
    const r = await dialog.showOpenDialog(win, { title: 'Ajouter des mods Fabric', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Mods Fabric', extensions: ['jar'] }] });
    if (r.canceled) return [];
    return mods.addModFiles(game.gameDirFor(cfg, versionArg(v)), r.filePaths);
  });
  handle('mods:open-folder', async v => {
    const dir = mods.modsDir(game.gameDirFor(cfg, versionArg(v)));
    await require('fs/promises').mkdir(dir, { recursive: true });
    return shell.openPath(dir);
  });
  handle('modrinth:search', (v, q, offset) => mods.searchModrinth(game.gameDirFor(cfg, versionArg(v)), v, q, offset));
  handle('modrinth:install', (v, id) => {
    ensureNotRunning();
    if (cfg.allowUserMods === false && v === cfg.minecraftVersion) throw new Error('Le serveur n\'autorise pas les mods personnels');
    return mods.installFromModrinth(game.gameDirFor(cfg, versionArg(v)), v, id);
  });

  /* ----- Skins ----- */
  handle('skin:profile', async () => skin.getProfile(await auth.getAccessToken()));
  handle('skin:upload', async (dataUrl, variant) => skin.uploadSkin(await auth.getAccessToken(), dataUrl, variant));
  handle('skin:reset', async () => skin.resetSkin(await auth.getAccessToken()));
  handle('skin:cape', async id => skin.setCape(await auth.getAccessToken(), id || null));
  handle('skin:from-player', name => skin.skinFromPlayer(name));
  handle('skin:library', () => skin.listLibrary());
  handle('skin:library-save', (name, dataUrl, variant) => skin.saveToLibrary(name, dataUrl, variant));
  handle('skin:library-delete', id => skin.deleteFromLibrary(id));

  /* ----- Divers ----- */
  ipcMain.handle('open-url', async (_e, url) => {
    // Seulement des liens HTTPS venant de la config (news, site)
    const allowed = new Set([cfg.website, ...cleanNews(cfg.news).map(n => n.url).filter(Boolean)]);
    if (allowed.has(url) && new URL(url).protocol === 'https:') await shell.openExternal(url);
  });
  ipcMain.handle('open-website', async () => {
    const u = new URL(cfg.website);
    if (u.protocol === 'https:') await shell.openExternal(u.toString());
  });
  ipcMain.handle('open-game-folder', async () => shell.openPath(game.gameDirFor(cfg, game.selectedVersion(cfg, settings))));
  ipcMain.handle('open-logs', async () => shell.showItemInFolder(log.transports.file.getFile().path));

  ipcMain.handle('install-update', async () => autoUpdater.quitAndInstall());

  ipcMain.on('win', (_e, action) => {
    if (!win) return;
    if (action === 'min') win.minimize();
    if (action === 'close') win.close();
  });
}

app.on('second-instance', () => {
  if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); }
});

app.whenReady().then(() => {
  settings = store.readSettings(embedded.defaults);
  if (embedded.voteUrl) bridge.setVoteUrl(embedded.voteUrl);
  bridge.start(path.join(__dirname, '../renderer/assets/icon.png')).then(p => log.info(`Pont de vote local sur le port ${p}`));
  registerIpc();
  createWindow();
  setupUpdater();
});

app.on('window-all-closed', () => {
  // La fenêtre de vote peut se fermer pendant que le jeu tourne : on ne quitte que si le jeu est fermé
  if (!game.isRunning() && !(win && !win.isDestroyed())) app.quit();
});
