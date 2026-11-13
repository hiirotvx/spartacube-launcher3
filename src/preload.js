'use strict';
// Pont minimal et explicite entre l'interface et le processus principal.
const { contextBridge, ipcRenderer } = require('electron');

const listen = channel => cb => {
  const handler = (_e, data) => cb(data);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
};
const call = (channel, ...args) => ipcRenderer.invoke(channel, ...args);

contextBridge.exposeInMainWorld('launcher', {
  init: () => call('init'),
  versions: () => call('versions'),
  login: () => call('login'),
  logout: () => call('logout'),
  play: () => call('play'),
  status: () => call('status'),
  saveSettings: s => call('save-settings', s),

  mods: {
    list: v => call('mods:list', v),
    toggle: (v, file, enabled) => call('mods:toggle', v, file, enabled),
    remove: (v, file) => call('mods:delete', v, file),
    add: v => call('mods:add', v),
    openFolder: v => call('mods:open-folder', v),
    search: (v, q, offset) => call('modrinth:search', v, q, offset),
    install: (v, id) => call('modrinth:install', v, id)
  },

  skin: {
    profile: () => call('skin:profile'),
    upload: (dataUrl, variant) => call('skin:upload', dataUrl, variant),
    reset: () => call('skin:reset'),
    cape: id => call('skin:cape', id),
    fromPlayer: name => call('skin:from-player', name),
    library: () => call('skin:library'),
    librarySave: (name, dataUrl, variant) => call('skin:library-save', name, dataUrl, variant),
    libraryDelete: id => call('skin:library-delete', id)
  },

  openUrl: url => call('open-url', url),
  openWebsite: () => call('open-website'),
  openGameFolder: () => call('open-game-folder'),
  openLogs: () => call('open-logs'),
  installUpdate: () => call('install-update'),
  minimize: () => ipcRenderer.send('win', 'min'),
  close: () => ipcRenderer.send('win', 'close'),
  onProgress: listen('progress'),
  onGameStarted: listen('game-started'),
  onGameClosed: listen('game-closed'),
  onUpdate: listen('update')
});
