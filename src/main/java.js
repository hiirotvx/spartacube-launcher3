'use strict';
// Installation automatique de Java : on utilise les runtimes officiels de Mojang
// (les mêmes que le launcher Minecraft officiel), vérifiés fichier par fichier en SHA-1.
const fsp = require('fs/promises');
const path = require('path');
const { getJson, download, pool } = require('./http');

const RUNTIME_INDEX =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

function platformKey() {
  const { platform, arch } = process;
  if (platform === 'win32') return arch === 'arm64' ? 'windows-arm64' : arch === 'ia32' ? 'windows-x86' : 'windows-x64';
  if (platform === 'darwin') return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  if (platform === 'linux') return arch === 'ia32' ? 'linux-i386' : arch === 'x64' ? 'linux' : null;
  return null;
}

function javaBinary(runtimeDir) {
  if (process.platform === 'win32') return path.join(runtimeDir, 'bin', 'javaw.exe');
  if (process.platform === 'darwin') return path.join(runtimeDir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(runtimeDir, 'bin', 'java');
}

async function exists(p) {
  try { await fsp.access(p); return true; } catch { return false; }
}

/**
 * Garantit que le runtime Java `component` (ex. "java-runtime-delta") est installé.
 * @returns chemin absolu de l'exécutable java
 */
async function ensureJava(baseDir, component, onProgress = () => {}) {
  const key = platformKey();
  if (!key) throw new Error(`Plateforme non supportée pour Java automatique : ${process.platform}/${process.arch}`);

  const index = await getJson(RUNTIME_INDEX);
  const entry = index[key] && index[key][component] && index[key][component][0];
  if (!entry) throw new Error(`Aucun runtime Java "${component}" disponible pour ${key}`);

  const runtimeDir = path.join(baseDir, 'runtime', component, key);
  const marker = path.join(runtimeDir, '.spartacube-runtime');
  const bin = javaBinary(runtimeDir);

  if ((await exists(bin)) && (await fsp.readFile(marker, 'utf8').catch(() => '')) === entry.manifest.sha1) {
    return bin;
  }

  onProgress({ step: 'java', label: `Installation de Java ${entry.version.name}…`, done: 0, total: 1 });
  const manifest = await getJson(entry.manifest.url);
  const files = Object.entries(manifest.files);

  for (const [rel, f] of files) {
    if (f.type === 'directory') await fsp.mkdir(path.join(runtimeDir, rel), { recursive: true });
  }

  const toDownload = files.filter(([, f]) => f.type === 'file');
  let done = 0;
  await pool(toDownload, 8, async ([rel, f]) => {
    const dest = path.join(runtimeDir, rel);
    await download(f.downloads.raw.url, dest, { sha1: f.downloads.raw.sha1, size: f.downloads.raw.size });
    if (f.executable && process.platform !== 'win32') await fsp.chmod(dest, 0o755);
    done++;
    if (done % 10 === 0 || done === toDownload.length) {
      onProgress({ step: 'java', label: `Installation de Java ${entry.version.name}…`, done, total: toDownload.length });
    }
  });

  if (process.platform !== 'win32') {
    for (const [rel, f] of files) {
      if (f.type !== 'link') continue;
      const linkPath = path.join(runtimeDir, rel);
      await fsp.rm(linkPath, { force: true });
      await fsp.mkdir(path.dirname(linkPath), { recursive: true });
      await fsp.symlink(f.target, linkPath).catch(() => {});
    }
  }

  await fsp.writeFile(marker, entry.manifest.sha1);
  return bin;
}

module.exports = { ensureJava };
