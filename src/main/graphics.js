'use strict';
// Détection du matériel + préréglage graphique appliqué au premier lancement d'une instance.
const os = require('os');
const fsp = require('fs/promises');
const path = require('path');
const { app } = require('electron');

const PRESETS = {
  low: { label: 'PC modeste', renderDistance: 8, simulationDistance: 6, particles: 1, entityShadows: false, shaders: false },
  medium: { label: 'Équilibré', renderDistance: 12, simulationDistance: 8, particles: 0, entityShadows: true, shaders: false },
  high: { label: 'PC costaud', renderDistance: 16, simulationDistance: 10, particles: 0, entityShadows: true, shaders: true }
};

/** Renvoie { preset, gpu, ramGb, cores, recommendedRamMb }. */
async function detect() {
  const ramGb = Math.round(os.totalmem() / 1024 ** 3);
  const cores = os.cpus().length;
  let gpu = 'Inconnue';
  let dedicated = false;
  try {
    const info = await app.getGPUInfo('basic');
    const dev = (info.gpuDevice || []).find(d => d.active) || (info.gpuDevice || [])[0];
    if (dev) {
      // 0x10de = NVIDIA, 0x1002 = AMD, 0x8086 = Intel
      dedicated = dev.vendorId === 0x10de || (dev.vendorId === 0x1002 && (info.gpuDevice || []).length >= 1);
      gpu = dev.vendorId === 0x10de ? 'NVIDIA' : dev.vendorId === 0x1002 ? 'AMD' : dev.vendorId === 0x8086 ? 'Intel' : `0x${dev.vendorId.toString(16)}`;
      if (dev.deviceString) gpu = dev.deviceString;
    }
  } catch { /* pas d'info GPU : on se base sur la RAM */ }
  let preset = 'medium';
  if (ramGb <= 8 || (!dedicated && cores <= 4)) preset = 'low';
  else if (dedicated && ramGb >= 16 && cores >= 6) preset = 'high';
  const recommendedRamMb = ramGb <= 8 ? 3072 : ramGb <= 16 ? 4096 : 6144;
  return { preset, gpu, ramGb, cores, recommendedRamMb, presets: PRESETS };
}

/** Écrit options.txt (et la config Iris) uniquement si l'instance n'en a pas encore. */
async function applyPreset(gameDir, presetName) {
  const p = PRESETS[presetName] || PRESETS.medium;
  const lines = [
    'guiScale:2',
    `renderDistance:${p.renderDistance}`,
    `simulationDistance:${p.simulationDistance}`,
    `particles:${p.particles}`,
    `entityShadows:${p.entityShadows}`,
    'lang:fr_fr'
  ];
  try { await fsp.writeFile(path.join(gameDir, 'options.txt'), `${lines.join('\n')}\n`, { flag: 'wx' }); } catch { /* déjà présent */ }
  const iris = path.join(gameDir, 'config', 'iris.properties');
  await fsp.mkdir(path.dirname(iris), { recursive: true });
  try { await fsp.writeFile(iris, `enableShaders=${p.shaders}\n`, { flag: 'wx' }); } catch { /* déjà présent */ }
}

module.exports = { detect, applyPreset, PRESETS };
