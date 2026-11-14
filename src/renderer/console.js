'use strict';
/* global consoleApi */
const $ = id => document.getElementById(id);
const out = $('out');
let count = 0;

function matches(text) {
  const f = $('filter').value.trim().toLowerCase();
  return !f || text.toLowerCase().includes(f);
}

function append(lines) {
  if (!lines || !lines.length) return;
  if (!count) out.replaceChildren();
  const frag = document.createDocumentFragment();
  for (const l of lines) {
    const d = document.createElement('div');
    d.className = /\/(ERROR|FATAL)\]|Exception|\tat /.test(l) ? 'ln err' : /\/WARN\]/.test(l) ? 'ln warn' : /\/DEBUG\]/.test(l) ? 'ln dim' : 'ln';
    d.textContent = l;
    if (!matches(l)) d.classList.add('hide');
    frag.append(d);
    count++;
  }
  out.append(frag);
  while (count > 2000 && out.firstChild) { out.firstChild.remove(); count--; }
  if ($('follow').checked) out.scrollTop = out.scrollHeight;
}

function clear(message) {
  count = 0;
  const d = document.createElement('div');
  d.className = 'empty';
  d.textContent = message;
  out.replaceChildren(d);
}

function setRunning(on) {
  $('dot').classList.toggle('live', on);
  $('state').textContent = on ? 'Jeu en cours — messages en direct' : (count ? 'Jeu fermé — derniers messages' : 'Le jeu n\'est pas lancé');
}

consoleApi.onGameLog(append);
consoleApi.onGameLogReset(() => clear('Démarrage du jeu…'));
consoleApi.onGameStarted(() => setRunning(true));
consoleApi.onGameClosed(() => setRunning(false));
consoleApi.getGameLog().then(lines => { append(lines); return consoleApi.gameRunning(); }).then(setRunning).catch(() => {});

$('filter').addEventListener('input', () => {
  for (const n of out.querySelectorAll('.ln')) n.classList.toggle('hide', !matches(n.textContent));
});
$('clear').onclick = () => clear('Console vidée.');
$('folder').onclick = () => consoleApi.openLogs();
$('copy').onclick = async () => {
  const text = [...out.querySelectorAll('.ln:not(.hide)')].map(n => n.textContent).join('\n');
  try { await navigator.clipboard.writeText(text); $('copy').textContent = 'Copié ✔'; }
  catch { $('copy').textContent = 'Copie impossible'; }
  setTimeout(() => { $('copy').textContent = 'Copier'; }, 1500);
};
