'use strict';
// Fenêtre « Console du jeu » : accès limité aux messages du jeu, rien d'autre.
const { contextBridge, ipcRenderer } = require('electron');

const listen = channel => cb => {
  const handler = (_e, data) => cb(data);
  ipcRenderer.on(channel, handler);
  return () => ipcRenderer.removeListener(channel, handler);
};

contextBridge.exposeInMainWorld('consoleApi', {
  getGameLog: () => ipcRenderer.invoke('logs:get'),
  gameRunning: () => ipcRenderer.invoke('logs:running'),
  openLogs: () => ipcRenderer.invoke('open-logs'),
  onGameLog: listen('game-log'),
  onGameLogReset: listen('game-log-reset'),
  onGameStarted: listen('game-started'),
  onGameClosed: listen('game-closed')
});
