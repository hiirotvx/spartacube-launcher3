#!/usr/bin/env node
'use strict';
// Vérifie la syntaxe de tous les fichiers JS du projet (sans lancer Electron).
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const roots = ['src', 'tools'];
let n = 0;
function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const f = path.join(d, e.name);
    if (e.isDirectory()) walk(f);
    else if (f.endsWith('.js')) { execFileSync(process.execPath, ['--check', f], { stdio: 'inherit' }); n++; }
  }
}
roots.forEach(walk);
JSON.parse(fs.readFileSync('launcher.config.json', 'utf8'));
console.log(`✔ ${n} fichiers JS OK, launcher.config.json OK`);
