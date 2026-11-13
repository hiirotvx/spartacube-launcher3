'use strict';
// Gestion des mods d'une instance : liste, activer/désactiver, supprimer, ajouter,
// et recherche / installation depuis Modrinth (fichiers vérifiés en SHA-1).
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const AdmZip = require('adm-zip');
const { getJson, download } = require('./http');
const { requiredFiles } = require('./modpack');

const MODRINTH = 'https://api.modrinth.com/v2';
const MODRINTH_STATE = '.spartacube-modrinth.json';
const metaCache = new Map();

function modsDir(gameDir) { return path.join(gameDir, 'mods'); }

/** N'accepte qu'un simple nom de fichier .jar / .jar.disabled (aucun chemin). */
function safeModFile(name) {
  if (typeof name !== 'string' || name !== path.basename(name) || !/^[^\\/:*?"<>|\0]+\.jar(\.disabled)?$/i.test(name)) {
    throw new Error(`Nom de mod invalide : ${name}`);
  }
  return name;
}

function readMeta(file, stat) {
  const key = `${file}:${stat.size}:${stat.mtimeMs}`;
  if (metaCache.has(key)) return metaCache.get(key);
  let meta = {};
  try {
    const zip = new AdmZip(file);
    const entry = zip.getEntry('fabric.mod.json');
    if (entry) {
      const j = JSON.parse(entry.getData().toString('utf8').replace(/^﻿/, ''));
      meta = { id: j.id, name: j.name, version: j.version, description: j.description };
      let icon = typeof j.icon === 'string' ? j.icon : j.icon && Object.values(j.icon).pop();
      if (icon) {
        const ie = zip.getEntry(icon.replace(/^\//, ''));
        if (ie && ie.header.size < 256 * 1024 && /\.png$/i.test(icon)) {
          meta.icon = `data:image/png;base64,${ie.getData().toString('base64')}`;
        }
      }
    }
  } catch { /* jar illisible : on affiche juste le nom du fichier */ }
  metaCache.set(key, meta);
  return meta;
}

async function listMods(gameDir) {
  const dir = modsDir(gameDir);
  await fsp.mkdir(dir, { recursive: true });
  const required = await requiredFiles(gameDir);
  const out = [];
  for (const name of await fsp.readdir(dir)) {
    if (!/\.jar(\.disabled)?$/i.test(name)) continue;
    const full = path.join(dir, name);
    const stat = await fsp.stat(full);
    if (!stat.isFile()) continue;
    const enabled = !name.endsWith('.disabled');
    const base = enabled ? name : name.slice(0, -'.disabled'.length);
    const meta = readMeta(full, stat);
    out.push({
      id: meta.id || null,
      file: name,
      name: meta.name || base.replace(/\.jar$/i, ''),
      version: meta.version || '',
      description: typeof meta.description === 'string' ? meta.description.slice(0, 200) : '',
      icon: meta.icon || null,
      enabled,
      required: required.has(`mods/${base}`),
      size: stat.size
    });
  }
  return out.sort((a, b) => (b.required - a.required) || a.name.localeCompare(b.name));
}

async function toggleMod(gameDir, file, enabled) {
  safeModFile(file);
  const dir = modsDir(gameDir);
  const base = file.replace(/\.disabled$/i, '');
  if ((await requiredFiles(gameDir)).has(`mods/${base}`)) throw new Error('Ce mod est requis par le serveur SpartaCube');
  const from = path.join(dir, file);
  const to = path.join(dir, enabled ? base : `${base}.disabled`);
  if (from !== to) await fsp.rename(from, to);
}

async function deleteMod(gameDir, file) {
  safeModFile(file);
  const base = file.replace(/\.disabled$/i, '');
  if ((await requiredFiles(gameDir)).has(`mods/${base}`)) throw new Error('Ce mod est requis par le serveur SpartaCube');
  await fsp.rm(path.join(modsDir(gameDir), file), { force: true });
  const st = await readModrinthState(gameDir);
  for (const [pid, f] of Object.entries(st)) if (f === base) delete st[pid];
  await writeModrinthState(gameDir, st);
}

async function addModFiles(gameDir, sources) {
  const dir = modsDir(gameDir);
  await fsp.mkdir(dir, { recursive: true });
  const added = [];
  for (const src of sources) {
    const name = safeModFile(path.basename(src));
    // Vérifie que c'est bien une archive de mod Fabric
    try {
      const zip = new AdmZip(src);
      if (!zip.getEntry('fabric.mod.json')) throw new Error();
    } catch {
      throw new Error(`${name} n'est pas un mod Fabric valide`);
    }
    await fsp.copyFile(src, path.join(dir, name));
    added.push(name);
  }
  return added;
}

/* ---------- Modrinth ---------- */

async function readModrinthState(gameDir) {
  try { return JSON.parse(await fsp.readFile(path.join(gameDir, MODRINTH_STATE), 'utf8')); } catch { return {}; }
}
async function writeModrinthState(gameDir, st) {
  await fsp.mkdir(gameDir, { recursive: true });
  await fsp.writeFile(path.join(gameDir, MODRINTH_STATE), JSON.stringify(st, null, 2));
}

async function searchModrinth(gameDir, version, query, offset = 0) {
  const facets = JSON.stringify([['project_type:mod'], ['categories:fabric'], [`versions:${version}`]]);
  const url = `${MODRINTH}/search?limit=20&offset=${Math.max(0, offset | 0)}&index=relevance`
    + `&query=${encodeURIComponent(String(query || '').slice(0, 100))}&facets=${encodeURIComponent(facets)}`;
  const res = await getJson(url);
  const installed = await readModrinthState(gameDir);
  return {
    total: res.total_hits,
    hits: res.hits.map(h => ({
      id: h.project_id,
      slug: h.slug,
      title: h.title,
      description: h.description,
      author: h.author,
      downloads: h.downloads,
      icon: typeof h.icon_url === 'string' && h.icon_url.startsWith('https://') ? h.icon_url : null,
      clientSide: h.client_side,
      installed: !!installed[h.project_id]
    }))
  };
}

async function installFromModrinth(gameDir, version, projectId, seen = new Set()) {
  if (!/^[A-Za-z0-9]{1,16}$/.test(projectId)) throw new Error('Projet Modrinth invalide');
  if (seen.has(projectId)) return [];
  seen.add(projectId);

  const state = await readModrinthState(gameDir);
  if (state[projectId] && fs.existsSync(path.join(modsDir(gameDir), state[projectId]))) return [];

  const versions = await getJson(`${MODRINTH}/project/${projectId}/version?loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(JSON.stringify([version]))}`);
  if (!versions.length) throw new Error(`Aucune version Fabric ${version} pour ce mod`);
  const v = versions.find(x => x.version_type === 'release') || versions[0];
  const file = v.files.find(f => f.primary) || v.files[0];
  const url = new URL(file.url);
  if (url.protocol !== 'https:' || url.hostname !== 'cdn.modrinth.com') throw new Error('Source de téléchargement non autorisée');
  const name = safeModFile(path.basename(file.filename));

  await download(file.url, path.join(modsDir(gameDir), name), { sha1: file.hashes.sha1, size: file.size });
  const st = await readModrinthState(gameDir);
  st[projectId] = name;
  await writeModrinthState(gameDir, st);

  const installed = [name];
  for (const dep of v.dependencies || []) {
    if (dep.dependency_type === 'required' && dep.project_id) {
      installed.push(...await installFromModrinth(gameDir, version, dep.project_id, seen));
    }
  }
  return installed;
}

/**
 * Installe une seule fois par instance les mods par défaut (ex. Sodium, Lithium, Iris…).
 * - un mod déjà présent (même identifiant Fabric, ex. fourni par le modpack) n'est pas doublé ;
 * - un mod supprimé ensuite par le joueur n'est pas réinstallé ;
 * - un mod indisponible pour cette version est simplement ignoré.
 */
async function installDefaultMods(gameDir, version, defaults, onProgress = () => {}) {
  if (!Array.isArray(defaults) || !defaults.length) return [];
  const markerFile = path.join(gameDir, '.spartacube-defaults.json');
  let done = [];
  try { done = JSON.parse(await fsp.readFile(markerFile, 'utf8')); } catch { /* premier lancement */ }
  const todo = defaults.filter(d => d && d.project && !done.includes(d.project));
  if (!todo.length) return [];

  const presentIds = new Set((await listMods(gameDir)).map(m => m.id).filter(Boolean));
  const installed = [];
  let i = 0;
  for (const d of todo) {
    onProgress({ step: 'mods', label: `Installation des mods par défaut (${d.name || d.project})…`, done: i++, total: todo.length });
    try {
      if (!(d.fabricId && presentIds.has(d.fabricId))) installed.push(...await installFromModrinth(gameDir, version, d.project));
    } catch (e) {
      installed.push(`! ${d.name || d.project} : ${e.message}`);
    }
    done.push(d.project);
  }
  await fsp.writeFile(markerFile, JSON.stringify(done, null, 2));
  return installed;
}

module.exports = { installDefaultMods, listMods, toggleMod, deleteMod, addModFiles, searchModrinth, installFromModrinth, modsDir };
