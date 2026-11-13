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
// - overwrite:false → le fichier n'est posé que s'il n'existe pas (réglages que le joueur peut modifier)
// - Tout fichier présent dans un dossier "géré" (managedFolders, ex. "mods") mais absent du
//   manifest est supprimé : les joueurs ne peuvent pas ajouter de mods non autorisés par erreur.
const fsp = require('fs/promises');
const path = require('path');
const { getJson, download, pool, safeJoin, assertHttps } = require('./http');

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

async function syncModpack(gameDir, manifestUrl, managedFolders = ['mods'], onProgress = () => {}) {
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
    return { ...f, dest };
  });

  let done = 0;
  await pool(files, 6, async f => {
    if (f.overwrite === false) {
      const exists = await fsp.access(f.dest).then(() => true, () => false);
      if (!exists) await download(f.url, f.dest, { sha1: f.sha1, size: f.size });
    } else {
      await download(f.url, f.dest, { sha1: f.sha1, size: f.size });
    }
    done++;
    onProgress({ step: 'modpack', label: 'Mise à jour du modpack…', done, total: files.length });
  });

  // Nettoyage des dossiers gérés
  const allowed = new Set(files.map(f => path.resolve(f.dest)));
  let removed = 0;
  for (const folder of managedFolders) {
    const dir = safeJoin(gameDir, folder);
    for (const file of await listFiles(dir)) {
      if (!allowed.has(path.resolve(file))) {
        await fsp.rm(file, { force: true });
        removed++;
      }
    }
  }
  return { version: manifest.version, files: files.length, removed };
}

module.exports = { syncModpack };
