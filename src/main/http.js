'use strict';
// Téléchargements HTTPS avec vérification SHA-1, retries et écriture atomique.
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const { Readable } = require('stream');
const { pipeline } = require('stream/promises');

const UA = 'SpartaCubeLauncher/1.0 (+https://spartacube.fr)';

function assertHttps(url) {
  const u = new URL(url);
  if (u.protocol !== 'https:') throw new Error(`URL refusée (HTTPS obligatoire) : ${url}`);
  return u;
}

async function fetchWithRetry(url, opts = {}, retries = 3) {
  assertHttps(url);
  let lastErr;
  for (let i = 0; i <= retries; i++) {
    try {
      const res = await fetch(url, {
        ...opts,
        headers: { 'User-Agent': UA, ...(opts.headers || {}) },
        signal: AbortSignal.timeout(opts.timeout || 60_000)
      });
      if (!res.ok) throw new Error(`HTTP ${res.status} sur ${url}`);
      return res;
    } catch (e) {
      lastErr = e;
      if (i < retries) await new Promise(r => setTimeout(r, 800 * (i + 1)));
    }
  }
  throw lastErr;
}

async function getJson(url) {
  const res = await fetchWithRetry(url, { headers: { Accept: 'application/json' } });
  return res.json();
}

async function sha1File(file) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash('sha1');
    fs.createReadStream(file).on('error', reject).on('data', d => h.update(d)).on('end', () => resolve(h.digest('hex')));
  });
}

async function fileMatches(file, sha1, size) {
  try {
    const st = await fsp.stat(file);
    if (!st.isFile()) return false;
    if (size != null && st.size !== size) return false;
    if (!sha1) return true;
    return (await sha1File(file)) === sha1.toLowerCase();
  } catch {
    return false;
  }
}

/** Télécharge `url` vers `dest`, vérifie le SHA-1, écrit dans un .part puis renomme. */
async function download(url, dest, { sha1, size } = {}) {
  if (await fileMatches(dest, sha1, size)) return false;
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  const tmp = `${dest}.part`;
  const res = await fetchWithRetry(url, { timeout: 10 * 60_000 });
  const hash = crypto.createHash('sha1');
  const src = Readable.fromWeb(res.body);
  src.on('data', d => hash.update(d));
  await pipeline(src, fs.createWriteStream(tmp));
  const got = hash.digest('hex');
  if (sha1 && got !== sha1.toLowerCase()) {
    await fsp.rm(tmp, { force: true });
    throw new Error(`Fichier corrompu (SHA-1 invalide) : ${path.basename(dest)}`);
  }
  await fsp.rename(tmp, dest);
  return true;
}

/** Exécute des tâches async avec une concurrence limitée. */
async function pool(items, limit, worker) {
  let i = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (i < items.length) {
      const idx = i++;
      await worker(items[idx], idx);
    }
  });
  await Promise.all(runners);
}

/** Vérifie qu'un chemin relatif reste bien dans `root` (anti path-traversal). */
function safeJoin(root, rel) {
  if (typeof rel !== 'string' || rel.length === 0 || rel.includes('\0')) throw new Error(`Chemin invalide : ${rel}`);
  if (path.isAbsolute(rel) || /^[a-zA-Z]:/.test(rel)) throw new Error(`Chemin absolu refusé : ${rel}`);
  const full = path.resolve(root, rel);
  const base = path.resolve(root) + path.sep;
  if (!full.startsWith(base)) throw new Error(`Chemin hors du dossier de jeu refusé : ${rel}`);
  return full;
}

module.exports = { fetchWithRetry, getJson, download, sha1File, fileMatches, pool, safeJoin, assertHttps };
