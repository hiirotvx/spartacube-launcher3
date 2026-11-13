'use strict';
// Profils de mods : un clic active/désactive un ensemble de mods (les mods verrouillés ne bougent pas).
const fsp = require('fs/promises');
const path = require('path');
const { listMods, toggleMod } = require('./mods');

const FILE = '.spartacube-profiles.json';

// Profils intégrés : règles par identifiant Fabric
const BUILTIN = {
  'Performances': { off: ['iris', 'oculus', 'entity_model_features', 'entity_texture_features', 'euphoria_patcher', 'continuity'], on: ['sodium', 'lithium', 'sodium-extra'] },
  'PvP': { off: ['iris', 'euphoria_patcher'], on: ['sodium', 'lithium'] },
  'Shaders': { on: ['iris', 'sodium'] },
  'Tout activé': { all: true }
};

async function readCustom(gameDir) {
  try { return JSON.parse(await fsp.readFile(path.join(gameDir, FILE), 'utf8')); } catch { return {}; }
}

async function list(gameDir) {
  const custom = await readCustom(gameDir);
  return { builtin: Object.keys(BUILTIN), custom: Object.keys(custom) };
}

async function saveCurrent(gameDir, name) {
  const n = String(name || '').trim().slice(0, 30);
  if (!n || BUILTIN[n]) throw new Error('Nom de profil invalide');
  const custom = await readCustom(gameDir);
  custom[n] = (await listMods(gameDir)).filter(m => m.enabled).map(m => m.file);
  await fsp.writeFile(path.join(gameDir, FILE), JSON.stringify(custom, null, 2));
  return list(gameDir);
}

async function remove(gameDir, name) {
  const custom = await readCustom(gameDir);
  delete custom[name];
  await fsp.writeFile(path.join(gameDir, FILE), JSON.stringify(custom, null, 2));
  return list(gameDir);
}

async function apply(gameDir, name) {
  const mods = await listMods(gameDir);
  const custom = await readCustom(gameDir);
  let changed = 0;
  for (const m of mods) {
    if (m.required) continue;
    let want = m.enabled;
    if (BUILTIN[name]) {
      const r = BUILTIN[name];
      if (r.all) want = true;
      if (r.off && r.off.includes(m.id)) want = false;
      if (r.on && r.on.includes(m.id)) want = true;
    } else if (custom[name]) {
      const base = m.file.replace(/\.disabled$/i, '');
      want = custom[name].some(f => f.replace(/\.disabled$/i, '') === base);
    } else {
      throw new Error('Profil introuvable');
    }
    if (want !== m.enabled) { await toggleMod(gameDir, m.file, want); changed++; }
  }
  return changed;
}

module.exports = { list, saveCurrent, remove, apply };
