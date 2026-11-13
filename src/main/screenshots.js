'use strict';
// Galerie des captures d'écran (F2) d'une instance.
const fsp = require('fs/promises');
const path = require('path');
const { nativeImage, clipboard, shell } = require('electron');

function dirOf(gameDir) { return path.join(gameDir, 'screenshots'); }

function safeName(name) {
  if (typeof name !== 'string' || name !== path.basename(name) || !/^[\w .\-()]+\.png$/i.test(name)) throw new Error('Capture invalide');
  return name;
}

async function list(gameDir) {
  const dir = dirOf(gameDir);
  let names = [];
  try { names = (await fsp.readdir(dir)).filter(n => /\.png$/i.test(n)); } catch { return []; }
  const items = [];
  for (const n of names) {
    const st = await fsp.stat(path.join(dir, n));
    items.push({ name: n, mtime: st.mtimeMs, size: st.size });
  }
  items.sort((a, b) => b.mtime - a.mtime);
  return items.slice(0, 120).map(it => {
    const img = nativeImage.createFromPath(path.join(dir, it.name));
    return { ...it, thumb: img.isEmpty() ? null : img.resize({ width: 320, quality: 'good' }).toDataURL() };
  });
}

async function full(gameDir, name) {
  const img = nativeImage.createFromPath(path.join(dirOf(gameDir), safeName(name)));
  if (img.isEmpty()) throw new Error('Image illisible');
  const s = img.getSize();
  return (s.width > 1920 ? img.resize({ width: 1920, quality: 'good' }) : img).toDataURL();
}

async function copy(gameDir, name) {
  const img = nativeImage.createFromPath(path.join(dirOf(gameDir), safeName(name)));
  if (img.isEmpty()) throw new Error('Image illisible');
  clipboard.writeImage(img);
}

async function remove(gameDir, name) {
  await shell.trashItem(path.join(dirOf(gameDir), safeName(name))); // corbeille, récupérable
}

async function reveal(gameDir, name) {
  if (name) shell.showItemInFolder(path.join(dirOf(gameDir), safeName(name)));
  else { await fsp.mkdir(dirOf(gameDir), { recursive: true }); await shell.openPath(dirOf(gameDir)); }
}

module.exports = { list, full, copy, remove, reveal };
