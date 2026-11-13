#!/usr/bin/env node
'use strict';
// Charge réellement tous les modules du launcher (avec un faux "electron")
// pour détecter les dépendances cassées AVANT de publier une version.
const Module = require('module');
const path = require('path');

const fake = new Proxy(function () {}, {
  get: (_t, k) => (k === 'then' ? undefined : k === 'getPath' ? () => require('os').tmpdir() : fake),
  apply: () => fake,
  construct: () => fake
});
const origLoad = Module._load;
Module._load = function (req, ...rest) {
  if (req === 'electron' || req === 'electron-log/main') return fake;
  return origLoad.call(this, req, ...rest);
};

const mods = ['http', 'java', 'fabric', 'modpack', 'versions', 'mods', 'skin', 'store', 'auth', 'status', 'game'];
for (const m of mods) require(path.join(__dirname, '..', 'src', 'main', `${m}.js`));
for (const dep of ['minecraft-launcher-core', 'msmc', 'request', 'request/lib/auth', 'request/lib/multipart', 'adm-zip', 'electron-updater']) {
  require(dep);
}
console.log(`✔ ${mods.length} modules du launcher + dépendances chargés sans erreur`);
