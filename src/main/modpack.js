'use strict';
// Synchronisation du modpack depuis un manifest.json hébergé sur spartacube.fr.
//
// Format attendu (généré par tools/generate-manifest.js) :
// {
//   "version": "2026-11-13T17:00:00Z",
//   "files": [
//     { "path": "mods/sodium.jar", "url": "https://…", "sha1": "…", "size": 123, "overwrite": true },
//     { "path": "options.txt",     "url": "https://…", "sha1": "…", "size": 456, "overwrite": false }
//   ]
// }
// - overwrite:true  → le fichier est forcé à l'identique du serveur (mods, configs imposées)
// - overwrite:false → posé seulement s'il n'existe pas (réglages que le joueur peut modifier)
// - Les fichiers installés par un ancien manifest puis retirés sont supprimés.
// - Si allowUserMods = false, TOUT fichier des dossiers gérés absent du manifest est supprimé.
const fsp = require('fs/promises');
const path = require('path');
const { getJson, download, pool, safeJoin, assertHttps } = require('./http');

const STATE = '.spartacube-modpack.json';

async function listFiles(dir) {
  const out = [];
  async function walk(d) {
    let entries;
    try { entries = await fsp.readdir(d, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const full = path.join(d, e.name);
      if (e.isDirectory()) await walk(full);
      else out.push(full);
    }
  }
  await walk(dir);
  return out;
}

async function readState(gameDir) {
  try { return JSON.parse(await fsp.readFile(path.join(gameDir, STATE), 'utf8')); } catch { return { files: [] }; }
}

/** Chemins relatifs (ex. "mods/x.jar") installés par le modpack serveur. */
async function requiredFiles(gameDir) {
  const set = new Set((await readState(gameDir)).files || []);
  try {
    const bundled = JSON.parse(await fsp.readFile(path.join(gameDir, '.spartacube-bundled.json'), 'utf8'));
    for (const f of bundled.files || []) set.add(`mods/${f}`);
  } catch { /* aucun mod intégré */ }
  return set;
}

async function syncModpack(gameDir, manifestUrl, { managedFolders = ['mods'], allowUserMods = true } = {}, onProgress = () => {}) {
  if (!manifestUrl) return { skipped: true };
  onProgress({ step: 'modpack', label: 'Vérification du modpack…', done: 0, total: 1 });

  const manifest = await getJson(manifestUrl);
  if (!manifest || !Array.isArray(manifest.files)) throw new Error('Manifest du modpack invalide');

  // Validation stricte AVANT de toucher au disque
  const files = manifest.files.map(f => {
    const dest = safeJoin(gameDir, f.path);
    assertHttps(f.url);
    if (f.overwrite !== false && !/^[a-f0-9]{40}$/i.test(f.sha1 || '')) {
      throw new Error(`SHA-1 manquant pour ${f.path}`);
    }
    return { ...f, rel: path.relative(gameDir, dest).split(path.sep).join('/'), dest };
  });

  let done = 0;
  await pool(files, 6, async f => {
    if (f.overwrite === false) {
      const exists = await fsp.access(f.dest).then(() => true, () => false);
      if (!exists) await download(f.url, f.dest, { sha1: f.sha1, size: f.size });
    } else {
      await download(f.url, f.dest, { sha1: f.sha1, size: f.size });
      // Un mod requis désactivé par erreur est retiré (la version active vient d'être posée)
      await fsp.rm(`${f.dest}.disabled`, { force: true });
    }
    done++;
    onProgress({ step: 'modpack', label: 'Mise à jour du modpack…', done, total: files.length });
  });

  const wanted = new Set(files.map(f => f.rel));
  const previous = (await readState(gameDir)).files || [];
  let removed = 0;

  // 1) Fichiers d'un ancien manifest qui ne sont plus demandés
  for (const rel of previous) {
    if (wanted.has(rel)) continue;
    await fsp.rm(safeJoin(gameDir, rel), { force: true });
    removed++;
  }
  // 2) Mode strict : rien d'autre que le modpack dans les dossiers gérés
  if (!allowUserMods) {
    for (const folder of managedFolders) {
      for (const file of await listFiles(safeJoin(gameDir, folder))) {
        const rel = path.relative(gameDir, file).split(path.sep).join('/');
        if (!wanted.has(rel)) { await fsp.rm(file, { force: true }); removed++; }
      }
    }
  }

  await fsp.writeFile(path.join(gameDir, STATE), JSON.stringify({ version: manifest.version, files: [...wanted] }, null, 2));
  return { version: manifest.version, files: files.length, removed };
}

module.exports = { syncModpack, requiredFiles };
