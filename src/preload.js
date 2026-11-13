'use strict';
// Pont minimal et explicite entre l'interface et le processus principal.
const { contextBridge, ipcRenderer } = require('electron');

const listen = channel => cb => {
  const handler = (_e, data) => cb(data);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
};

contextBridge.exposeInMainWorld('launcher', {
  init: () => ipcRenderer.invoke('init'),
  login: () => ipcRenderer.invoke('login'),
  logout: () => ipcRenderer.invoke('logout'),
  play: () => ipcRenderer.invoke('play'),
  status: () => ipcRenderer.invoke('status'),
  saveSettings: s => ipcRenderer.invoke('save-settings', s),
  openWebsite: () => ipcRenderer.invoke('open-website'),
  openGameFolder: () => ipcRenderer.invoke('open-game-folder'),
  openLogs: () => ipcRenderer.invoke('open-logs'),
  installUpdate: () => ipcRenderer.invoke('install-update'),
  minimize: () => ipcRenderer.send('win', 'min'),
  close: () => ipcRenderer.send('win', 'close'),
  onProgress: listen('progress'),
  onGameStarted: listen('game-started'),
  onGameClosed: listen('game-closed'),
  onUpdate: listen('update')
});
