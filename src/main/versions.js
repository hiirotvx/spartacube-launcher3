'use strict';
// Liste des versions proposées dans le sélecteur + dossiers d'instance par version.
const path = require('path');
const { getJson } = require('./http');

let cache = null;

function isSafeVersion(v) {
  return typeof v === 'string' && /^[0-9][\w.\-]{0,31}$/.test(v);
}

function compare(a, b) {
  const pa = a.split(/[.\-]/).map(n => parseInt(n, 10) || 0);
  const pb = b.split(/[.\-]/).map(n => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pb[i] || 0) - (pa[i] || 0);
    if (d) return d;
  }
  return 0;
}

/** Versions stables supportées par Fabric (≥ 1.14), la plus récente en premier. */
async function fabricStableVersions() {
  if (cache) return cache;
  try {
    const list = await getJson('https://meta.fabricmc.net/v2/versions/game');
    cache = list
      .filter(v => v.stable && /^1\.(1[4-9]|[2-9]\d)(\.\d+)?$/.test(v.version))
      .map(v => v.version)
      .sort(compare);
  } catch {
    cache = [];
  }
  return cache;
}

/**
 * @returns [{ id, official, label }]
 */
async function listVersions(cfg) {
  const official = cfg.minecraftVersion;
  const extra = Array.isArray(cfg.versions) ? cfg.versions.filter(isSafeVersion) : [];
  const all = cfg.allowOtherVersions !== false ? await fabricStableVersions() : [];
  const ids = [...new Set([official, ...extra, ...all])].filter(isSafeVersion);
  return ids.map(id => ({ id, official: id === official }));
}

function instanceDir(root, version) {
  if (!isSafeVersion(version)) throw new Error(`Version invalide : ${version}`);
  return path.join(root, 'instances', version);
}

module.exports = { listVersions, instanceDir, isSafeVersion };
