#!/usr/bin/env node
'use strict';
// Génère manifest.json à partir d'un dossier de modpack.
//
// Usage :
//   node tools/generate-manifest.js <dossier-modpack> <url-de-base> [--keep options.txt,config/xaero]
//
// Exemple :
//   node tools/generate-manifest.js ./modpack https://spartacube.fr/launcher/files --keep options.txt
//
// Puis envoie le CONTENU de ./modpack dans https://spartacube.fr/launcher/files/
// et manifest.json dans https://spartacube.fr/launcher/manifest.json
//
// --keep : fichiers/dossiers posés une seule fois (le joueur peut ensuite les modifier).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const [, , dirArg, baseArg, ...rest] = process.argv;
if (!dirArg || !baseArg) {
  console.error('Usage : node tools/generate-manifest.js <dossier-modpack> <url-de-base> [--keep a,b]');
  process.exit(1);
}
if (!baseArg.startsWith('https://')) {
  console.error("L'URL de base doit être en HTTPS.");
  process.exit(1);
}
const keepIdx = rest.indexOf('--keep');
const keep = keepIdx >= 0 ? String(rest[keepIdx + 1] || '').split(',').filter(Boolean).map(s => s.replace(/\\/g, '/').replace(/\/$/, '')) : [];

const root = path.resolve(dirArg);
const base = baseArg.replace(/\/$/, '');
const files = [];

function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name.startsWith('.')) continue;
    const full = path.join(dir, e.name);
    if (e.isDirectory()) { walk(full); continue; }
    const rel = path.relative(root, full).split(path.sep).join('/');
    const buf = fs.readFileSync(full);
    files.push({
      path: rel,
      url: `${base}/${rel.split('/').map(encodeURIComponent).join('/')}`,
      sha1: crypto.createHash('sha1').update(buf).digest('hex'),
      size: buf.length,
      overwrite: !keep.some(k => rel === k || rel.startsWith(`${k}/`))
    });
  }
}
walk(root);
files.sort((a, b) => a.path.localeCompare(b.path));

const manifest = { version: new Date().toISOString(), files };
const out = path.join(process.cwd(), 'manifest.json');
fs.writeFileSync(out, JSON.stringify(manifest, null, 2));
console.log(`✔ ${files.length} fichiers → ${out}`);
