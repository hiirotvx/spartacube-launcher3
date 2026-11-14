'use strict';
// Réglages du joueur + stockage CHIFFRÉ du jeton de session Microsoft.
const fs = require('fs');
const path = require('path');
const { app, safeStorage } = require('electron');

const dir = () => app.getPath('userData');
const settingsFile = () => path.join(dir(), 'settings.json');
const accountFile = () => path.join(dir(), 'account.bin');

function readSettings(defaults) {
  try {
    return { ...defaults, ...JSON.parse(fs.readFileSync(settingsFile(), 'utf8')) };
  } catch {
    return { ...defaults };
  }
}

function writeSettings(s) {
  fs.mkdirSync(dir(), { recursive: true });
  fs.writeFileSync(settingsFile(), JSON.stringify(s, null, 2));
}

/** Le refresh token n'est JAMAIS écrit en clair : sans chiffrement OS disponible, il n'est pas sauvegardé. */
function saveRefreshToken(token) {
  if (!safeStorage.isEncryptionAvailable()) return false;
  fs.mkdirSync(dir(), { recursive: true });
  fs.writeFileSync(accountFile(), safeStorage.encryptString(token), { mode: 0o600 });
  return true;
}

function loadRefreshToken() {
  try {
    if (!safeStorage.isEncryptionAvailable()) return null;
    return safeStorage.decryptString(fs.readFileSync(accountFile()));
  } catch {
    return null;
  }
}

function clearRefreshToken() {
  try { fs.rmSync(accountFile(), { force: true }); } catch { /* rien */ }
}

/* ---------- Plusieurs comptes : { active, accounts: [{ uuid, name, token }] }, chiffré par le système ---------- */
const accountsFile = () => path.join(dir(), 'accounts.bin');

function loadAccounts() {
  try {
    if (!safeStorage.isEncryptionAvailable()) return { active: null, accounts: [] };
    const d = JSON.parse(safeStorage.decryptString(fs.readFileSync(accountsFile())));
    const accounts = (Array.isArray(d.accounts) ? d.accounts : [])
      .filter(a => a && typeof a.uuid === 'string' && typeof a.token === 'string')
      .map(a => ({ uuid: a.uuid, name: String(a.name || '').slice(0, 32), token: a.token }));
    return { active: typeof d.active === 'string' ? d.active : null, accounts };
  } catch {
    return { active: null, accounts: [] };
  }
}

function saveAccounts(data) {
  if (!safeStorage.isEncryptionAvailable()) return false;
  fs.mkdirSync(dir(), { recursive: true });
  fs.writeFileSync(accountsFile(), safeStorage.encryptString(JSON.stringify(data)), { mode: 0o600 });
  return true;
}

module.exports = { readSettings, writeSettings, saveRefreshToken, loadRefreshToken, clearRefreshToken, loadAccounts, saveAccounts };
