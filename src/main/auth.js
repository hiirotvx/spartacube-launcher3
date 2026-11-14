'use strict';
// Connexion Microsoft officielle (OAuth Microsoft → Xbox Live → Minecraft Services).
// Aucun mode « crack » : un compte qui ne possède pas Minecraft Java est refusé.
const { Auth } = require('msmc');
const store = require('./store');

let current = null; // objet Minecraft de msmc

function makeAuth(cfg) {
  const ms = (cfg && cfg.microsoft) || {};
  if (ms.clientId) {
    return new Auth({ client_id: ms.clientId, redirect: ms.redirect || 'http://localhost', prompt: 'select_account' });
  }
  return new Auth('select_account');
}

const MAX_ACCOUNTS = 8;

async function finish(xbox) {
  const mc = await xbox.getMinecraft();
  if (!mc.profile || mc.isDemo()) {
    throw new Error("Ce compte Microsoft ne possède pas Minecraft: Java Edition. Achète le jeu sur minecraft.net pour jouer sur SpartaCube.");
  }
  // Enregistre (ou met à jour) le compte et le rend actif. Les jetons restent chiffrés par le système.
  const data = store.loadAccounts();
  const entry = { uuid: mc.profile.id, name: mc.profile.name, token: xbox.save() };
  const i = data.accounts.findIndex(a => a.uuid === entry.uuid);
  if (i >= 0) data.accounts[i] = entry;
  else {
    if (data.accounts.length >= MAX_ACCOUNTS) throw new Error(`Tu as déjà ${MAX_ACCOUNTS} comptes enregistrés. Retires-en un avant d'en ajouter un autre.`);
    data.accounts.push(entry);
  }
  data.active = entry.uuid;
  store.saveAccounts(data);
  current = mc;
  return publicProfile();
}

function publicProfile() {
  if (!current) return null;
  return { name: current.profile.name, uuid: current.profile.id };
}

/** Comptes enregistrés (sans les jetons). */
function list() {
  const data = store.loadAccounts();
  const activeId = current ? current.profile.id : null;
  return data.accounts.map(a => ({ uuid: a.uuid, name: a.name, active: a.uuid === activeId }));
}

async function login(cfg) {
  const auth = makeAuth(cfg);
  const xbox = await auth.launch('electron', {
    width: 520, height: 680, resizable: false, title: 'Connexion Microsoft – SpartaCube',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  return finish(xbox);
}

/** Passe sur un autre compte enregistré. */
async function switchTo(cfg, uuid) {
  const data = store.loadAccounts();
  const acc = data.accounts.find(a => a.uuid === uuid);
  if (!acc) throw new Error('Compte introuvable');
  try {
    const xbox = await makeAuth(cfg).refresh(acc.token);
    return await finish(xbox);
  } catch (e) {
    throw new Error(`Impossible de reprendre le compte ${acc.name}. Retire-le puis reconnecte-le.`);
  }
}

/** Retire un compte. Si c'était le compte actif, on bascule sur un autre s'il en reste. */
async function remove(cfg, uuid) {
  const data = store.loadAccounts();
  data.accounts = data.accounts.filter(a => a.uuid !== uuid);
  const wasActive = current && current.profile.id === uuid;
  if (data.active === uuid) data.active = data.accounts[0] ? data.accounts[0].uuid : null;
  store.saveAccounts(data);
  if (!wasActive) return publicProfile();
  current = null;
  for (const a of data.accounts) {
    try { return await switchTo(cfg, a.uuid); } catch { /* compte suivant */ }
  }
  return null;
}

/** Reconnexion silencieuse au démarrage grâce aux jetons chiffrés. */
async function restore(cfg) {
  let data = store.loadAccounts();
  // Ancien format (un seul compte) : repris puis converti
  const legacy = store.loadRefreshToken();
  if (!data.accounts.length && legacy) {
    try {
      const p = await finish(await makeAuth(cfg).refresh(legacy));
      store.clearRefreshToken();
      return p;
    } catch {
      store.clearRefreshToken();
      return null;
    }
  }
  const order = [...data.accounts].sort((a, b) => (b.uuid === data.active) - (a.uuid === data.active));
  for (const a of order) {
    try { return await finish(await makeAuth(cfg).refresh(a.token)); } catch { /* jeton expiré : compte suivant */ }
  }
  return null;
}

/** Renvoie l'objet d'autorisation pour minecraft-launcher-core (jeton rafraîchi si besoin). */
async function getLaunchAuth() {
  if (!current) throw new Error('Non connecté');
  if (!current.validate()) await current.refresh(true);
  return current.mclc();
}

async function getAccessToken() {
  return (await getLaunchAuth()).access_token;
}

/** Déconnexion du compte actif (il est retiré de la liste). Renvoie le compte sur lequel on a basculé, s'il en reste. */
async function logout(cfg) {
  if (!current) return null;
  return remove(cfg, current.profile.id);
}

module.exports = { login, restore, logout, getLaunchAuth, getAccessToken, publicProfile, list, switchTo, remove };
