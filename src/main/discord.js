'use strict';
// Discord Rich Presence via l'IPC local de Discord (aucune dépendance externe).
// Protocole : trame = [op int32 LE][longueur int32 LE][JSON]. op 0 = handshake, op 1 = commande.
const net = require('net');
const path = require('path');
const log = require('electron-log/main');

let sock = null;
let clientId = '';
let ready = false;
let pending = null;
let retryTimer = null;
let connecting = false;

function pipePath(i) {
  if (process.platform === 'win32') return `\\\\?\\pipe\\discord-ipc-${i}`;
  const base = process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || process.env.TMP || process.env.TEMP || '/tmp';
  return path.join(base, `discord-ipc-${i}`);
}

function frame(op, payload) {
  const json = Buffer.from(JSON.stringify(payload), 'utf8');
  const head = Buffer.alloc(8);
  head.writeInt32LE(op, 0);
  head.writeInt32LE(json.length, 4);
  return Buffer.concat([head, json]);
}

function connect(i = 0) {
  if (!clientId || sock || (connecting && i === 0)) return;
  connecting = true;
  if (i > 9) { connecting = false; scheduleRetry(); return; }
  const s = net.createConnection(pipePath(i));
  s.once('error', () => { s.destroy(); connect(i + 1); });
  s.once('connect', () => {
    connecting = false;
    sock = s;
    s.removeAllListeners('error');
    s.on('error', () => {});
    s.on('close', () => { sock = null; ready = false; scheduleRetry(); });
    s.on('data', buf => {
      try {
        const op = buf.readInt32LE(0);
        const msg = JSON.parse(buf.slice(8, 8 + buf.readInt32LE(4)).toString('utf8'));
        if (op === 1 && msg.evt === 'READY') {
          ready = true;
          if (pending) send(pending);
        }
      } catch { /* trame partielle ignorée */ }
    });
    s.write(frame(0, { v: 1, client_id: clientId }));
  });
}

function scheduleRetry() {
  if (retryTimer || !clientId) return;
  retryTimer = setTimeout(() => { retryTimer = null; connect(0); }, 30_000);
}

function send(activity) {
  if (!sock || !ready) return;
  const nonce = `${Date.now()}${Math.random()}`;
  sock.write(frame(1, { cmd: 'SET_ACTIVITY', args: { pid: process.pid, activity }, nonce }));
}

/** Démarre la présence Discord (désactivée si aucun Application ID). */
function init(id) {
  clientId = /^\d{17,21}$/.test(String(id || '')) ? String(id) : '';
  if (!clientId) { log.info('Discord Rich Presence désactivé (pas de discordClientId)'); return; }
  connect(0);
}

/**
 * @param state { playing, players, max, version, website }
 */
function update(state) {
  if (!clientId) return;
  const activity = {
    details: state.playing ? `Joue sur SpartaCube (${state.version})` : 'Dans le launcher SpartaCube',
    state: state.players != null ? `${state.players}/${state.max} joueurs en ligne` : 'play.spartacube.fr',
    assets: { large_image: 'logo', large_text: 'SpartaCube' },
    instance: false
  };
  if (state.playing) activity.timestamps = { start: state.startedAt || Date.now() };
  if (state.website) activity.buttons = [{ label: 'Rejoindre SpartaCube', url: state.website }];
  pending = activity;
  if (ready) send(activity); else connect(0);
}

module.exports = { init, update };
