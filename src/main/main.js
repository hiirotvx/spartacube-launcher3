'use strict';
const path = require('path');
const os = require('os');
const { app, BrowserWindow, ipcMain, shell } = require('electron');
const log = require('electron-log/main');
const { autoUpdater } = require('electron-updater');

const embedded = require('../../launcher.config.json');
const { getJson } = require('./http');
const store = require('./store');
const auth = require('./auth');
const game = require('./game');
const { ping } = require('./status');

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
const REMOTE_KEYS = ['minecraftVersion', 'fabricLoaderVersion', 'modpackManifestUrl', 'managedFolders', 'news', 'message', 'serverPort'];

async function loadRemoteConfig() {
  if (!embedded.remoteConfigUrl) return;
  try {
    const remote = await getJson(embedded.remoteConfigUrl);
    for (const k of REMOTE_KEYS) if (remote[k] !== undefined) cfg[k] = remote[k];
    log.info('Config distante chargée');
  } catch (e) {
    log.warn(`Config distante indisponible, config embarquée utilisée : ${e.message}`);
  }
}

function send(channel, data) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, data);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 660,
    minWidth: 960,
    minHeight: 600,
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

function registerIpc() {
  ipcMain.handle('init', async () => {
    await loadRemoteConfig();
    const profile = await auth.restore(cfg);
    return {
      appVersion: app.getVersion(),
      serverName: cfg.serverName,
      serverAddress: cfg.serverAddress,
      minecraftVersion: cfg.minecraftVersion,
      website: cfg.website,
      message: typeof cfg.message === 'string' ? cfg.message : '',
      news: Array.isArray(cfg.news) ? cfg.news.slice(0, 5) : [],
      totalRamMb: Math.floor(os.totalmem() / 1024 / 1024),
      settings,
      profile
    };
  });

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
      jvmArgs: String(s.jvmArgs || '').slice(0, 500)
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

  ipcMain.handle('open-website', async () => {
    const u = new URL(cfg.website);
    if (u.protocol === 'https:') await shell.openExternal(u.toString());
  });
  ipcMain.handle('open-game-folder', async () => shell.openPath(game.gameRoot(cfg)));
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
  registerIpc();
  createWindow();
  setupUpdater();
});

app.on('window-all-closed', () => {
  if (!game.isRunning()) app.quit();
});
