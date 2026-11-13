'use strict';
const $ = id => document.getElementById(id);
const L = window.launcher;

let state = { settings: null, totalRamMb: 8192, playing: false };

function show(view) {
  $('loginView').classList.toggle('hidden', view !== 'login');
  $('homeView').classList.toggle('hidden', view !== 'home');
}

function setProfile(p) {
  $('playerName').textContent = p.name;
  $('avatar').src = `https://mc-heads.net/avatar/${encodeURIComponent(p.uuid)}/96`;
  show('home');
}

function fmtGo(mb) { return `${(mb / 1024).toFixed(1).replace('.0', '')} Go`; }

// Braises animées
(function embers() {
  const box = document.querySelector('.embers');
  for (let i = 0; i < 28; i++) {
    const e = document.createElement('i');
    e.style.left = `${Math.random() * 100}%`;
    e.style.animationDuration = `${6 + Math.random() * 8}s`;
    e.style.animationDelay = `${-Math.random() * 12}s`;
    e.style.setProperty('--dx', `${(Math.random() - 0.5) * 160}px`);
    const s = 2 + Math.random() * 3;
    e.style.width = e.style.height = `${s}px`;
    box.appendChild(e);
  }
})();

// Contenu dynamique : uniquement textContent (jamais innerHTML) pour éviter toute injection
function renderNews(info) {
  if (info.message) {
    $('serverMessage').textContent = info.message;
    $('serverMessage').classList.remove('hidden');
  }
  const list = $('newsList');
  list.replaceChildren();
  for (const n of info.news) {
    const card = document.createElement('div');
    card.className = 'card news';
    const h = document.createElement('h3'); h.textContent = String(n.title || '');
    const p = document.createElement('p'); p.textContent = String(n.text || '');
    card.append(h, p);
    if (n.date) { const s = document.createElement('small'); s.textContent = String(n.date); card.append(s); }
    list.append(card);
  }
}

async function refreshStatus() {
  const s = await L.status();
  const el = $('status');
  el.classList.toggle('on', s.online);
  el.classList.toggle('off', !s.online);
  $('statusText').textContent = s.online
    ? `${s.players ? `${s.players.online}/${s.players.max} joueurs` : 'En ligne'} · ${s.latency} ms`
    : 'Serveur hors ligne';
}

// Paramètres
function openSettings() {
  const s = state.settings;
  const max = Math.max(2048, Math.floor(state.totalRamMb * 0.75 / 512) * 512);
  $('ramMax').max = max; $('ramMin').max = max;
  $('ramMax').value = Math.min(s.ramMaxMb, max);
  $('ramMin').value = Math.min(s.ramMinMb, max);
  $('autoConnect').checked = s.autoConnect;
  $('closeOnStart').checked = s.closeLauncherOnStart;
  $('jvmArgs').value = s.jvmArgs || '';
  updateRamLabels();
  $('settingsModal').classList.remove('hidden');
}
function updateRamLabels() {
  if (+$('ramMin').value > +$('ramMax').value) $('ramMin').value = $('ramMax').value;
  $('ramMaxLabel').textContent = fmtGo(+$('ramMax').value);
  $('ramMinLabel').textContent = fmtGo(+$('ramMin').value);
}
$('ramMax').addEventListener('input', updateRamLabels);
$('ramMin').addEventListener('input', updateRamLabels);
$('btnSettings').onclick = openSettings;
$('btnCancelSettings').onclick = () => $('settingsModal').classList.add('hidden');
$('btnSaveSettings').onclick = async () => {
  state.settings = await L.saveSettings({
    ramMaxMb: +$('ramMax').value,
    ramMinMb: +$('ramMin').value,
    autoConnect: $('autoConnect').checked,
    closeLauncherOnStart: $('closeOnStart').checked,
    jvmArgs: $('jvmArgs').value
  });
  $('settingsModal').classList.add('hidden');
};
$('btnFolder').onclick = () => L.openGameFolder();
$('btnLogs').onclick = () => L.openLogs();
$('btnWebsite').onclick = () => L.openWebsite();
$('btnMin').onclick = () => L.minimize();
$('btnClose').onclick = () => L.close();

// Connexion
$('btnLogin').onclick = async () => {
  $('btnLogin').disabled = true;
  $('loginError').textContent = '';
  const r = await L.login();
  $('btnLogin').disabled = false;
  if (r.ok) setProfile(r.profile);
  else $('loginError').textContent = r.error;
};
$('btnLogout').onclick = async () => { await L.logout(); show('login'); };

// Jouer
$('btnPlay').onclick = async () => {
  if (state.playing) return;
  state.playing = true;
  $('btnPlay').disabled = true;
  $('btnPlay').textContent = 'PRÉPARATION';
  $('playError').textContent = '';
  $('progressBox').classList.remove('hidden');
  const r = await L.play();
  if (!r.ok) {
    state.playing = false;
    $('btnPlay').disabled = false;
    $('btnPlay').textContent = 'JOUER';
    $('progressBox').classList.add('hidden');
    $('playError').textContent = r.error;
  }
};
L.onProgress(p => {
  $('progressLabel').textContent = p.label;
  const pct = p.total ? Math.min(100, Math.round((p.done / p.total) * 100)) : 0;
  $('progressFill').style.width = `${pct}%`;
});
L.onGameStarted(() => {
  $('progressBox').classList.add('hidden');
  $('btnPlay').textContent = 'EN JEU';
});
L.onGameClosed(() => {
  state.playing = false;
  $('btnPlay').disabled = false;
  $('btnPlay').textContent = 'JOUER';
});

// Mise à jour du launcher
L.onUpdate(u => {
  $('updateBar').classList.remove('hidden');
  if (u.state === 'ready') {
    $('updateText').textContent = `Version ${u.version} prête.`;
    $('btnUpdate').classList.remove('hidden');
  } else {
    $('updateText').textContent = `Mise à jour du launcher en cours…${u.percent != null ? ` ${u.percent}%` : ''}`;
  }
});
$('btnUpdate').onclick = () => L.installUpdate();

// Démarrage
(async () => {
  const info = await L.init();
  state.settings = info.settings;
  state.totalRamMb = info.totalRamMb;
  $('appVersion').textContent = `v${info.appVersion} · Minecraft ${info.minecraftVersion}`;
  renderNews(info);
  if (info.profile) setProfile(info.profile);
  else show('login');
  $('splash').classList.add('fade');
  refreshStatus();
  setInterval(refreshStatus, 30_000);
})();
