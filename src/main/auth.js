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

async function finish(xbox) {
  const mc = await xbox.getMinecraft();
  if (!mc.profile || mc.isDemo()) {
    throw new Error("Ce compte Microsoft ne possède pas Minecraft: Java Edition. Achète le jeu sur minecraft.net pour jouer sur SpartaCube.");
  }
  store.saveRefreshToken(xbox.save());
  current = mc;
  return publicProfile();
}

function publicProfile() {
  if (!current) return null;
  return { name: current.profile.name, uuid: current.profile.id };
}

async function login(cfg) {
  const auth = makeAuth(cfg);
  const xbox = await auth.launch('electron', {
    width: 520, height: 680, resizable: false, title: 'Connexion Microsoft – SpartaCube',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  return finish(xbox);
}

/** Reconnexion silencieuse au démarrage grâce au refresh token chiffré. */
async function restore(cfg) {
  const token = store.loadRefreshToken();
  if (!token) return null;
  try {
    const xbox = await makeAuth(cfg).refresh(token);
    return await finish(xbox);
  } catch {
    store.clearRefreshToken();
    return null;
  }
}

/** Renvoie l'objet d'autorisation pour minecraft-launcher-core (jeton rafraîchi si besoin). */
async function getLaunchAuth() {
  if (!current) throw new Error('Non connecté');
  if (!current.validate()) await current.refresh(true);
  return current.mclc();
}

function logout() {
  current = null;
  store.clearRefreshToken();
}

module.exports = { login, restore, logout, getLaunchAuth, publicProfile };
