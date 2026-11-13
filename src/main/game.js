'use strict';
// Orchestration du lancement : Java → Fabric → modpack (version officielle) → Minecraft.
// Fichiers partagés (Java, librairies, assets) dans la racine ; chaque version a son
// propre dossier de jeu (mods, options, mondes) dans instances/<version>.
const path = require('path');
const fsp = require('fs/promises');
const { app } = require('electron');
const { Client } = require('minecraft-launcher-core');
const log = require('electron-log/main');
const { ensureJava } = require('./java');
const { getJavaComponent, ensureFabric } = require('./fabric');
const { syncModpack } = require('./modpack');
const { installDefaultMods } = require('./mods');
const { installBundledMods } = require('./bundled');
const { instanceDir, isSafeVersion } = require('./versions');
const auth = require('./auth');
const bridge = require('./bridge');

let running = null;

function gameRoot(cfg) {
  const name = String(cfg.instanceFolder || 'spartacube').replace(/[^\w.\-]/g, '');
  return path.join(app.getPath('appData'), `.${name}`);
}

function selectedVersion(cfg, settings) {
  const v = settings.selectedVersion;
  if (v && isSafeVersion(v) && (cfg.allowOtherVersions !== false || v === cfg.minecraftVersion)) return v;
  return cfg.minecraftVersion;
}

function gameDirFor(cfg, version) {
  return instanceDir(gameRoot(cfg), version);
}

/** Premier lancement d'une instance : GUI scale 2 (le joueur peut le changer ensuite en jeu). */
async function ensureDefaultOptions(gameDir) {
  const file = path.join(gameDir, 'options.txt');
  try {
    await fsp.writeFile(file, 'guiScale:2\n', { flag: 'wx' }); // échoue si le fichier existe déjà
  } catch { /* options déjà présentes : on ne touche pas aux réglages du joueur */ }
}

/**
 * @param cfg       config effective (embarquée + distante)
 * @param settings  réglages du joueur
 * @param emit      (event, data) → renvoyé à l'interface
 */
async function launch(cfg, settings, emit) {
  if (running) throw new Error('Le jeu est déjà lancé');
  const root = gameRoot(cfg);
  const version = selectedVersion(cfg, settings);
  const official = version === cfg.minecraftVersion;
  const gameDir = gameDirFor(cfg, version);
  await fsp.mkdir(gameDir, { recursive: true });
  const progress = p => emit('progress', p);

  progress({ step: 'prepare', label: `Préparation de Minecraft ${version}…`, done: 0, total: 1 });
  const component = await getJavaComponent(version);
  const javaPath = await ensureJava(root, component, progress);

  progress({ step: 'fabric', label: 'Installation de Fabric…', done: 0, total: 1 });
  const fabric = await ensureFabric(root, version, official ? cfg.fabricLoaderVersion : 'latest');
  log.info(`Fabric ${fabric.loader} pour ${version}`);

  if (official && cfg.modpackManifestUrl) {
    const res = await syncModpack(gameDir, cfg.modpackManifestUrl, {
      managedFolders: cfg.managedFolders,
      allowUserMods: cfg.allowUserMods !== false
    }, progress);
    log.info('Modpack synchronisé', res);
  }
  const bundled = await installBundledMods(gameDir, version);
  if (bundled.length) log.info('Mods intégrés mis à jour', bundled);
  const extras = await installDefaultMods(gameDir, version, cfg.defaultMods, progress);
  if (extras.length) log.info('Mods par défaut', extras);
  await ensureDefaultOptions(gameDir);
  await bridge.writeBridgeFile(gameDir).catch(() => {});

  progress({ step: 'auth', label: 'Vérification du compte…', done: 0, total: 1 });
  const authorization = await auth.getLaunchAuth();

  const minMb = Math.max(512, Number(settings.ramMinMb) || 2048);
  const maxMb = Math.max(minMb, Number(settings.ramMaxMb) || 4096);
  const extraJvm = String(settings.jvmArgs || '').split(/\s+/).filter(a => /^-(X|D|XX:)/.test(a));

  const opts = {
    root,
    authorization,
    javaPath,
    version: { number: version, type: 'release', custom: fabric.id },
    memory: { min: `${minMb}M`, max: `${maxMb}M` },
    customArgs: extraJvm,
    overrides: { maxSockets: 8, gameDirectory: gameDir, cwd: gameDir }
  };
  if (settings.autoConnect && official) {
    opts.quickPlay = { type: 'multiplayer', identifier: `${cfg.serverAddress}:${cfg.serverPort || 25565}` };
  }

  const client = new Client();
  client.on('debug', m => log.debug(m));
  client.on('data', m => log.info(`[MC] ${String(m).trimEnd()}`));
  client.on('progress', p => progress({ step: 'game', label: `Téléchargement de Minecraft (${p.type})…`, done: p.task, total: p.total }));

  progress({ step: 'game', label: 'Téléchargement de Minecraft…', done: 0, total: 1 });
  const proc = await client.launch(opts);
  if (!proc) throw new Error('Impossible de démarrer Minecraft (voir les logs)');

  running = proc;
  emit('game-started', { version });
  client.on('close', code => {
    running = null;
    emit('game-closed', { code });
  });
}

function isRunning() { return !!running; }

module.exports = { launch, isRunning, gameRoot, gameDirFor, selectedVersion };
