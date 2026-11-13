#!/usr/bin/env node
'use strict';
// Charge réellement tous les modules du processus principal (Electron simulé)
// pour détecter AVANT la compilation les dépendances cassées (ex. "Package subpath not exported").
const Module = require('module');
const os = require('os');
const path = require('path');

const fakeElectron = {
  app: { getPath: () => os.tmpdir(), getVersion: () => '0.0.0', isPackaged: false, requestSingleInstanceLock: () => true, on() {}, whenReady: () => new Promise(() => {}), quit() {} },
  safeStorage: { isEncryptionAvailable: () => false, encryptString: s => Buffer.from(s), decryptString: b => b.toString() },
  BrowserWindow: function () {}, ipcMain: { handle() {}, on() {} }, shell: {}, contextBridge: { exposeInMainWorld() {} }, ipcRenderer: { invoke() {}, on() {}, send() {} }
};
const origLoad = Module._load;
Module._load = function (req, ...rest) {
  if (req === 'electron') return fakeElectron;
  return origLoad.call(this, req, ...rest);
};

// Dépendances tierces chargées paresseusement par minecraft-launcher-core
for (const dep of ['minecraft-launcher-core', 'msmc', 'request', 'adm-zip', 'electron-log/main', 'electron-updater']) {
  try { require(dep); } catch (e) {
    if (dep.startsWith('electron-') && /electron/i.test(e.message)) continue; // ces libs exigent le vrai runtime Electron
    console.error(`✘ ${dep} : ${e.message}`); process.exit(1);
  }
}
const root = path.join(__dirname, '..', 'src', 'main');
for (const f of ['http', 'java', 'fabric', 'modpack', 'store', 'auth', 'status', 'game']) {
  require(path.join(root, f));
}
console.log('✔ Tous les modules se chargent');
