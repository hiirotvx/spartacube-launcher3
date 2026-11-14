'use strict';
// Actualités : lecture depuis l'API du plugin SpartaCubeBadge, publication par les admins.
// Publication sans mot de passe : le launcher « rejoint » la session Mojang avec
// serverId = sha1(nonce + ':' + sha256(contenu)). Le plugin vérifie auprès de Mojang que
// c'est bien ce compte, puis qu'il est admin. Le jeton Microsoft n'est jamais envoyé au serveur.
const crypto = require('crypto');

const sha = (alg, s) => crypto.createHash(alg).update(s, 'utf8').digest('hex');

function baseUrl(apiUrl) {
  const u = new URL(apiUrl);
  if (u.protocol !== 'http:' && u.protocol !== 'https:') throw new Error('URL d\'API invalide');
  return u.origin;
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
    const r = await request(`${baseUrl(apiUrl)}/news`, {}, 5000);
    if (!r.ok) return null;
    const list = await r.json();
    return Array.isArray(list) ? list : null;
  } catch {
    return null;
  }
}

async function publish(apiUrl, list, { accessToken, uuid, name }) {
  const base = baseUrl(apiUrl);
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
