'use strict';
// Centre de vote : minuteurs par site (mémorisés localement) et compteur du mois.
const fs = require('fs');
const path = require('path');
const { app } = require('electron');

const file = () => path.join(app.getPath('userData'), 'votes.json');

function read() {
  try { return JSON.parse(fs.readFileSync(file(), 'utf8')); } catch { return { last: {}, history: [] }; }
}
function write(d) { fs.writeFileSync(file(), JSON.stringify(d, null, 2)); }

function cleanSites(sites) {
  return (Array.isArray(sites) ? sites : []).slice(0, 12).map((s, i) => {
    let url = null;
    try { if (new URL(s.url).protocol === 'https:') url = String(s.url); } catch { /* ignoré */ }
    return { id: `site${i}`, name: String(s.name || `Site ${i + 1}`).slice(0, 40), url, cooldownMin: Math.max(10, Math.min(2880, Number(s.cooldownMin) || 90)) };
  }).filter(s => s.url);
}

function status(sites) {
  const d = read();
  const now = Date.now();
  const month = new Date().toISOString().slice(0, 7);
  return {
    monthCount: d.history.filter(h => h.startsWith(month)).length,
    sites: cleanSites(sites).map(s => {
      const last = d.last[s.url] || 0;
      const next = last + s.cooldownMin * 60_000;
      return { ...s, nextAt: next > now ? next : 0 };
    })
  };
}

/** Clic sur « Voter » : démarre le minuteur du site. */
function markVoted(url) {
  const d = read();
  d.last[url] = Date.now();
  d.history.push(new Date().toISOString());
  d.history = d.history.slice(-500);
  write(d);
}

/** Prochain vote disponible (pour le rappel en jeu). */
function nextAvailable(sites) {
  const s = status(sites).sites;
  if (!s.length) return null;
  return { anyAvailable: s.some(x => !x.nextAt), soonest: Math.min(...s.map(x => x.nextAt || 0)) };
}

module.exports = { status, markVoted, nextAvailable, cleanSites };
