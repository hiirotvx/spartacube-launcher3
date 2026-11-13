'use strict';
// Orchestration du lancement : Java → Fabric → modpack → Minecraft.
const path = require('path');
const { app } = require('electron');
const { Client } = require('minecraft-launcher-core');
const log = require('electron-log/main');
const { ensureJava } = require('./java');
const { getJavaComponent, ensureFabric } = require('./fabric');
const { syncModpack } = require('./modpack');
const auth = require('./auth');

let running = null;

function gameRoot(cfg) {
  const name = String(cfg.instanceFolder || 'spartacube').replace(/[^\w.\-]/g, '');
  return path.join(app.getPath('appData'), `.${name}`);
}

/**
 * @param cfg       config effective (embarquée + distante)
 * @param settings  réglages du joueur
 * @param emit      (event, data) → renvoyé à l'interface
 */
async function launch(cfg, settings, emit) {
  if (running) throw new Error('Le jeu est déjà lancé');
  const root = gameRoot(cfg);
  const progress = p => emit('progress', p);

  progress({ step: 'prepare', label: 'Préparation…', done: 0, total: 1 });
  const component = await getJavaComponent(cfg.minecraftVersion);
  const javaPath = await ensureJava(root, component, progress);

  progress({ step: 'fabric', label: 'Installation de Fabric…', done: 0, total: 1 });
  const fabric = await ensureFabric(root, cfg.minecraftVersion, cfg.fabricLoaderVersion);
  log.info(`Fabric ${fabric.loader} pour ${cfg.minecraftVersion}`);

  if (cfg.modpackManifestUrl) {
    const res = await syncModpack(root, cfg.modpackManifestUrl, cfg.managedFolders, progress);
    log.info('Modpack synchronisé', res);
  }

  progress({ step: 'auth', label: 'Vérification du compte…', done: 0, total: 1 });
  const authorization = await auth.getLaunchAuth();

  const minMb = Math.max(512, Number(settings.ramMinMb) || 2048);
  const maxMb = Math.max(minMb, Number(settings.ramMaxMb) || 4096);
  const extraJvm = String(settings.jvmArgs || '').split(/\s+/).filter(a => /^-(X|D|XX:)/.test(a));

  const opts = {
    root,
    authorization,
    javaPath,
    version: { number: cfg.minecraftVersion, type: 'release', custom: fabric.id },
    memory: { min: `${minMb}M`, max: `${maxMb}M` },
    customArgs: extraJvm,
    overrides: { maxSockets: 8 }
  };
  if (settings.autoConnect) {
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
  emit('game-started', {});
  client.on('close', code => {
    running = null;
    emit('game-closed', { code });
  });
}

function isRunning() { return !!running; }

module.exports = { launch, isRunning, gameRoot };
