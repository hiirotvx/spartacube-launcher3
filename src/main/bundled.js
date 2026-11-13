'use strict';
// Mods livrés DANS le launcher (ex. SpartaCube Client : menu principal, menu Shift droit, HUD).
// Copiés dans l'instance de la version pour laquelle ils ont été compilés, et verrouillés.
const fsp = require('fs/promises');
const path = require('path');
const { app } = require('electron');
const { sha1File } = require('./http');

function bundledDir() {
  return app.isPackaged ? path.join(process.resourcesPath, 'mods') : path.join(__dirname, '..', '..', 'mod-dist');
}

async function installBundledMods(gameDir, version) {
  let meta;
  try { meta = JSON.parse(await fsp.readFile(path.join(bundledDir(), 'bundled.json'), 'utf8')); } catch { return []; }
  const files = (meta.files || []).filter(f => /^[\w.\-]+\.jar$/.test(f));
  const stateFile = path.join(gameDir, '.spartacube-bundled.json');
  if (meta.minecraft !== version) {
    // version différente : on retire nos mods s'ils avaient été posés
    for (const f of files) await fsp.rm(path.join(gameDir, 'mods', f), { force: true });
    await fsp.rm(stateFile, { force: true });
    return [];
  }
  await fsp.mkdir(path.join(gameDir, 'mods'), { recursive: true });
  const copied = [];
  for (const f of files) {
    const src = path.join(bundledDir(), f);
    const dest = path.join(gameDir, 'mods', f);
    const same = await Promise.all([sha1File(src), sha1File(dest).catch(() => null)]).then(([a, b]) => a === b);
    if (!same) { await fsp.copyFile(src, dest); copied.push(f); }
    await fsp.rm(`${dest}.disabled`, { force: true });
  }
  await fsp.writeFile(stateFile, JSON.stringify({ files }, null, 2));
  return copied;
}

module.exports = { installBundledMods };
