'use strict';
// Actualités : lecture depuis l'API du plugin SpartaCubeBadge, publication par les admins.
// Publication sans mot de passe : le launcher « rejoint » la session Mojang avec
// serverId = sha1(nonce + ':' + sha256(contenu)). Le plugin vérifie auprès de Mojang que
// c'est bien ce compte, puis qu'il est admin. Le jeton Microsoft n'est jamais envoyé au serveur.
const crypto = require('crypto');
const dns = require('dns').promises;

const sha = (alg, s) => crypto.createHash(alg).update(s, 'utf8').digest('hex');

const resolved = new Map();

/** Adresse de l'API. Si le domaine est un enregistrement SRV Minecraft, on vise la vraie machine. */
async function baseUrl(apiUrl) {
  const u = new URL(apiUrl);
  if (u.protocol !== 'http:' && u.protocol !== 'https:') throw new Error('URL d\'API invalide');
  if (resolved.has(u.origin)) return resolved.get(u.origin);
  // Sans port explicite : l'API est sur le port du serveur Minecraft (SRV si présent, sinon 25565)
  let origin = u.origin;
  try {
    const srv = await dns.resolveSrv(`_minecraft._tcp.${u.hostname}`);
    if (srv && srv[0] && srv[0].name) {
      const v = new URL(u.origin);
      v.hostname = srv[0].name.replace(/\.$/, '');
      if (!u.port) v.port = String(srv[0].port);
      origin = v.origin;
    } else if (!u.port) {
      const v = new URL(u.origin); v.port = '25565'; origin = v.origin;
    }
  } catch {
    if (!u.port) { const v = new URL(u.origin); v.port = '25565'; origin = v.origin; }
  }
  resolved.set(u.origin, origin);
  return origin;
}

function friendly(e) {
  if (e && (e.name === 'AbortError' || /abort/i.test(e.message || ''))) {
    return new Error('Le serveur des actualités ne répond pas. Vérifie que le plugin SpartaCubeBadge 1.3.1 tourne sur le serveur (Paper requis).');
  }
  if (e && /fetch failed|ECONNREFUSED|ENOTFOUND|EHOSTUNREACH/i.test(`${e.message} ${e.cause && e.cause.code}`)) {
    return new Error('Connexion au serveur des actualités impossible (serveur éteint ou plugin pas à jour).');
  }
  return e;
}

async function request(url, opts = {}, timeoutMs = 8000) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    return await fetch(url, { ...opts, signal: ctrl.signal, redirect: 'error' });
  } finally {
    clearTimeout(t);
  }
}

/** Liste des actualités, ou null si l'API est injoignable. */
async function fetchNews(apiUrl) {
  if (!apiUrl) return null;
  try {
    const r = await request(`${await baseUrl(apiUrl)}/news`, {}, 5000);
    if (!r.ok) return null;
    const list = await r.json();
    return Array.isArray(list) ? list : null;
  } catch {
    return null;
  }
}

async function publish(apiUrl, list, auth) {
  try {
    return await doPublish(apiUrl, list, auth);
  } catch (e) {
    throw friendly(e);
  }
}

async function doPublish(apiUrl, list, { accessToken, uuid, name }) {
  const base = await baseUrl(apiUrl);
  const ch = await request(`${base}/news/challenge`);
  if (!ch.ok) throw new Error((await ch.json().catch(() => ({}))).error || `Serveur injoignable (${ch.status})`);
  const { nonce } = await ch.json();
  if (!/^[0-9a-f]{32}$/.test(String(nonce))) throw new Error('Réponse du serveur invalide');

  const payload = JSON.stringify(list);
  const serverId = sha('sha1', `${nonce}:${sha('sha256', payload)}`);
  const join = await request('https://sessionserver.mojang.com/session/minecraft/join', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accessToken, selectedProfile: String(uuid).replace(/-/g, ''), serverId })
  });
  if (join.status !== 204 && !join.ok) throw new Error('Vérification Microsoft impossible. Déconnecte-toi puis reconnecte-toi.');

  const res = await request(`${base}/news/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: name, nonce, payload })
  }, 15000);
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || `Publication refusée (${res.status})`);
  return body.count;
}

module.exports = { fetchNews, publish };
