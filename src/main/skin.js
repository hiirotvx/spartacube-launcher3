'use strict';
// Skins & capes via l'API officielle Minecraft Services + bibliothèque locale de skins.
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const { app } = require('electron');

const API = 'https://api.minecraftservices.com/minecraft/profile';
const TEXTURE_HOSTS = new Set(['textures.minecraft.net']);
const UA = 'SpartaCubeLauncher/1.1 (+https://spartacube.fr)';

async function api(token, method, url, body, isJson = true) {
  const headers = { Authorization: `Bearer ${token}`, 'User-Agent': UA };
  if (body && isJson) headers['Content-Type'] = 'application/json';
  const res = await fetch(url, { method, headers, body: body && isJson ? JSON.stringify(body) : body, signal: AbortSignal.timeout(30_000) });
  if (res.status === 429) throw new Error('Trop de requêtes vers Minecraft, réessaie dans une minute');
  if (res.status === 401) throw new Error('Session Minecraft expirée, reconnecte-toi');
  if (!res.ok) {
    const txt = await res.text().catch(() => '');
    throw new Error(`Minecraft a refusé la demande (${res.status})${txt ? ` : ${txt.slice(0, 160)}` : ''}`);
  }
  const ct = res.headers.get('content-type') || '';
  return ct.includes('json') ? res.json() : null;
}

/** Télécharge une texture Mojang et la renvoie en data URL (hôte vérifié). */
async function textureDataUrl(url) {
  const u = new URL(url);
  if (!TEXTURE_HOSTS.has(u.hostname)) throw new Error('Hôte de texture non autorisé');
  u.protocol = 'https:';
  const res = await fetch(u, { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(20_000) });
  if (!res.ok) throw new Error(`Texture introuvable (${res.status})`);
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > 512 * 1024) throw new Error('Texture trop lourde');
  return `data:image/png;base64,${buf.toString('base64')}`;
}

function variantOf(v) { return String(v).toLowerCase() === 'slim' ? 'slim' : 'classic'; }

async function getProfile(token) {
  const p = await api(token, 'GET', API);
  const active = (p.skins || []).find(s => s.state === 'ACTIVE');
  const capes = [];
  for (const c of p.capes || []) {
    capes.push({ id: c.id, alias: c.alias, active: c.state === 'ACTIVE', dataUrl: await textureDataUrl(c.url).catch(() => null) });
  }
  return {
    name: p.name,
    skin: active ? { dataUrl: await textureDataUrl(active.url), variant: variantOf(active.variant) } : null,
    capes
  };
}

/** Vérifie qu'on envoie un vrai PNG de skin (64×64 ou 64×32). */
function parseSkinPng(dataUrl) {
  const m = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(String(dataUrl));
  if (!m) throw new Error('Le skin doit être une image PNG');
  const buf = Buffer.from(m[1], 'base64');
  if (buf.length > 64 * 1024) throw new Error('Image trop lourde (64 Ko max)');
  if (buf.readUInt32BE(0) !== 0x89504e47 || buf.toString('ascii', 12, 16) !== 'IHDR') throw new Error('PNG invalide');
  const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
  if (w !== 64 || (h !== 64 && h !== 32)) throw new Error(`Un skin doit faire 64×64 ou 64×32 pixels (reçu ${w}×${h})`);
  return buf;
}

async function uploadSkin(token, dataUrl, variant) {
  const buf = parseSkinPng(dataUrl);
  const form = new FormData();
  form.append('variant', variantOf(variant));
  form.append('file', new Blob([buf], { type: 'image/png' }), 'skin.png');
  await api(token, 'POST', `${API}/skins`, form, false);
  return getProfile(token);
}

async function resetSkin(token) {
  await api(token, 'DELETE', `${API}/skins/active`);
  return getProfile(token);
}

async function setCape(token, capeId) {
  if (capeId) {
    if (!/^[0-9a-f-]{32,36}$/i.test(capeId)) throw new Error('Cape invalide');
    await api(token, 'PUT', `${API}/capes/active`, { capeId });
  } else {
    await api(token, 'DELETE', `${API}/capes/active`);
  }
  return getProfile(token);
}

/** Récupère le skin public d'un joueur (pour s'en inspirer dans l'éditeur). */
async function skinFromPlayer(name) {
  if (!/^\w{3,16}$/.test(String(name))) throw new Error('Pseudo invalide');
  const r = await fetch(`https://api.mojang.com/users/profiles/minecraft/${encodeURIComponent(name)}`, { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(15_000) });
  if (r.status === 404 || r.status === 204) throw new Error('Joueur introuvable');
  if (!r.ok) throw new Error(`Mojang a répondu ${r.status}`);
  const { id } = await r.json();
  const s = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${id}`, { headers: { 'User-Agent': UA }, signal: AbortSignal.timeout(15_000) });
  if (!s.ok) throw new Error(`Mojang a répondu ${s.status}`);
  const prof = await s.json();
  const prop = (prof.properties || []).find(p => p.name === 'textures');
  const tex = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8')).textures || {};
  if (!tex.SKIN) throw new Error('Ce joueur utilise le skin par défaut');
  return { dataUrl: await textureDataUrl(tex.SKIN.url), variant: tex.SKIN.metadata && tex.SKIN.metadata.model === 'slim' ? 'slim' : 'classic' };
}

/* ---------- Bibliothèque locale ---------- */

const libDir = () => path.join(app.getPath('userData'), 'skins');
const indexFile = () => path.join(libDir(), 'index.json');

async function readIndex() {
  try { return JSON.parse(await fsp.readFile(indexFile(), 'utf8')); } catch { return []; }
}

async function listLibrary() {
  const out = [];
  for (const e of await readIndex()) {
    try {
      const buf = await fsp.readFile(path.join(libDir(), `${e.id}.png`));
      out.push({ ...e, dataUrl: `data:image/png;base64,${buf.toString('base64')}` });
    } catch { /* fichier supprimé à la main */ }
  }
  return out;
}

async function saveToLibrary(name, dataUrl, variant) {
  const buf = parseSkinPng(dataUrl);
  const id = crypto.randomBytes(8).toString('hex');
  await fsp.mkdir(libDir(), { recursive: true });
  await fsp.writeFile(path.join(libDir(), `${id}.png`), buf);
  const idx = await readIndex();
  idx.unshift({ id, name: String(name || 'Mon skin').slice(0, 40), variant: variantOf(variant), created: Date.now() });
  await fsp.writeFile(indexFile(), JSON.stringify(idx.slice(0, 60), null, 2));
  return listLibrary();
}

async function deleteFromLibrary(id) {
  if (!/^[0-9a-f]{16}$/.test(id)) throw new Error('Skin invalide');
  await fsp.rm(path.join(libDir(), `${id}.png`), { force: true });
  await fsp.writeFile(indexFile(), JSON.stringify((await readIndex()).filter(e => e.id !== id), null, 2));
  return listLibrary();
}

module.exports = { getProfile, uploadSkin, resetSkin, setCape, skinFromPlayer, listLibrary, saveToLibrary, deleteFromLibrary, parseSkinPng };
