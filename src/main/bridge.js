'use strict';
// Pont local jeu ↔ launcher : le mod SpartaCube Client demande l'ouverture de la page de vote.
// Écoute uniquement sur 127.0.0.1, protégé par un jeton aléatoire écrit dans le dossier de l'instance.
const http = require('http');
const crypto = require('crypto');
const fsp = require('fs/promises');
const path = require('path');
const { BrowserWindow, session } = require('electron');

const token = crypto.randomBytes(24).toString('hex');
let port = 0;
let voteUrl = 'https://spartacube.fr/vote';
let voteWin = null;

function openVoteWindow(iconPath) {
  if (voteWin && !voteWin.isDestroyed()) {
    voteWin.show();
    voteWin.focus();
    return;
  }
  const ses = session.fromPartition('persist:vote');
  ses.setPermissionRequestHandler((_wc, _perm, cb) => cb(false));
  voteWin = new BrowserWindow({
    width: 1050,
    height: 780,
    title: 'Voter pour SpartaCube',
    icon: iconPath,
    autoHideMenuBar: true,
    alwaysOnTop: true,
    backgroundColor: '#140b12',
    webPreferences: { session: ses, sandbox: true, contextIsolation: true, nodeIntegration: false }
  });
  voteWin.setAlwaysOnTop(true, 'screen-saver');
  // Les sites de vote ouvrent souvent des onglets : on les charge dans la même fenêtre (HTTPS seulement)
  voteWin.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) voteWin.loadURL(url);
    return { action: 'deny' };
  });
  voteWin.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('https://')) e.preventDefault(); });
  voteWin.on('closed', () => { voteWin = null; });
  voteWin.loadURL(voteUrl);
  voteWin.focus();
}

function start(iconPath) {
  return new Promise(resolve => {
    const server = http.createServer((req, res) => {
      const ok = req.method === 'POST' && req.url === '/vote'
        && typeof req.headers['x-spartacube-token'] === 'string'
        && req.headers['x-spartacube-token'].length === token.length
        && crypto.timingSafeEqual(Buffer.from(req.headers['x-spartacube-token']), Buffer.from(token));
      if (!ok) { res.writeHead(403); res.end(); return; }
      openVoteWindow(iconPath);
      res.writeHead(204);
      res.end();
    });
    server.on('error', () => resolve(0));
    server.listen(0, '127.0.0.1', () => {
      port = server.address().port;
      resolve(port);
    });
  });
}

function setVoteUrl(url) {
  try { if (new URL(url).protocol === 'https:') voteUrl = url; } catch { /* garde l'URL par défaut */ }
}

/** Écrit les infos du pont dans le dossier de l'instance (lu par le mod). */
async function writeBridgeFile(gameDir) {
  if (!port) return;
  const file = path.join(gameDir, '.spartacube-bridge.json');
  await fsp.writeFile(file, JSON.stringify({ port, token, voteUrl }), { mode: 0o600 });
}

module.exports = { start, setVoteUrl, writeBridgeFile, openVoteWindow };
