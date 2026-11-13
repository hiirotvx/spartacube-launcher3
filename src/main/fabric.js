'use strict';
// Récupère la version vanilla (pour connaître le Java requis) et installe le profil Fabric.
const fsp = require('fs/promises');
const path = require('path');
const { getJson } = require('./http');

const MOJANG_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';

/** Renvoie le composant Java requis par la version (ex. "java-runtime-delta"). */
async function getJavaComponent(mcVersion) {
  const manifest = await getJson(MOJANG_MANIFEST);
  const v = manifest.versions.find(x => x.id === mcVersion);
  if (!v) throw new Error(`Version Minecraft inconnue : ${mcVersion}`);
  const json = await getJson(v.url);
  return (json.javaVersion && json.javaVersion.component) || 'jre-legacy';
}

async function resolveLoaderVersion(mcVersion, wanted) {
  if (wanted && wanted !== 'latest') return wanted;
  const loaders = await getJson(`${FABRIC_META}/versions/loader/${encodeURIComponent(mcVersion)}`);
  if (!Array.isArray(loaders) || loaders.length === 0) throw new Error(`Fabric n'est pas disponible pour ${mcVersion}`);
  const stable = loaders.find(l => l.loader && l.loader.stable) || loaders[0];
  return stable.loader.version;
}

/**
 * Écrit versions/<id>/<id>.json pour minecraft-launcher-core.
 * @returns l'identifiant de version Fabric (ex. "fabric-loader-0.16.9-1.21.1")
 */
async function ensureFabric(root, mcVersion, loaderWanted) {
  const loader = await resolveLoaderVersion(mcVersion, loaderWanted);
  const profile = await getJson(
    `${FABRIC_META}/versions/loader/${encodeURIComponent(mcVersion)}/${encodeURIComponent(loader)}/profile/json`
  );
  const id = profile.id;
  if (!/^[\w.\-]+$/.test(id)) throw new Error(`Identifiant Fabric inattendu : ${id}`);
  const dir = path.join(root, 'versions', id);
  await fsp.mkdir(dir, { recursive: true });
  await fsp.writeFile(path.join(dir, `${id}.json`), JSON.stringify(profile, null, 2));
  return { id, loader };
}

module.exports = { getJavaComponent, ensureFabric };
