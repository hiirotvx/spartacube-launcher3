'use strict';
// Ping du serveur (protocole « Server List Ping ») pour afficher joueurs en ligne / MOTD.
const net = require('net');

function varInt(n) {
  const out = [];
  do {
    let b = n & 0x7f;
    n >>>= 7;
    if (n !== 0) b |= 0x80;
    out.push(b);
  } while (n !== 0);
  return Buffer.from(out);
}

function readVarInt(buf, off) {
  let num = 0, shift = 0, b;
  do {
    if (off >= buf.length) return null;
    b = buf[off++];
    num |= (b & 0x7f) << shift;
    shift += 7;
    if (shift > 35) throw new Error('VarInt trop long');
  } while (b & 0x80);
  return [num, off];
}

function packet(id, payload) {
  const body = Buffer.concat([varInt(id), payload]);
  return Buffer.concat([varInt(body.length), body]);
}

function ping(host, port = 25565, timeout = 4000) {
  return new Promise(resolve => {
    const sock = net.createConnection({ host, port });
    let data = Buffer.alloc(0);
    const t0 = Date.now();
    const done = res => { sock.destroy(); resolve(res); };
    sock.setTimeout(timeout, () => done({ online: false }));
    sock.on('error', () => done({ online: false }));
    sock.on('connect', () => {
      const h = Buffer.from(host, 'utf8');
      const p = Buffer.alloc(2); p.writeUInt16BE(port);
      sock.write(packet(0x00, Buffer.concat([varInt(767), varInt(h.length), h, p, varInt(1)])));
      sock.write(packet(0x00, Buffer.alloc(0)));
    });
    sock.on('data', chunk => {
      data = Buffer.concat([data, chunk]);
      if (data.length > 1 << 20) return done({ online: false });
      try {
        const len = readVarInt(data, 0); if (!len) return;
        if (data.length < len[1] + len[0]) return;
        const id = readVarInt(data, len[1]); if (!id || id[0] !== 0) return done({ online: false });
        const sl = readVarInt(data, id[1]); if (!sl) return;
        const json = JSON.parse(data.slice(sl[1], sl[1] + sl[0]).toString('utf8'));
        done({
          online: true,
          players: json.players ? { online: json.players.online, max: json.players.max } : null,
          version: json.version ? json.version.name : null,
          latency: Date.now() - t0
        });
      } catch {
        done({ online: false });
      }
    });
  });
}

module.exports = { ping };
