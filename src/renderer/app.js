'use strict';
const $ = id => document.getElementById(id);
const L = window.launcher;

const state = {
  settings: null,
  totalRamMb: 8192,
  playing: false,
  officialVersion: '',
  version: '',
  allowUserMods: true,
  news: [],
  admins: [],
  canEditNews: false,
  profile: null,
  modsLoadedFor: null,
  modrinthOffset: 0,
  skinVariant: 'classic',
  skinLoaded: false
};

/* ===================== Utilitaires ===================== */

function el(tag, props = {}, ...children) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') n.className = v;
    else if (k === 'text') n.textContent = v;
    else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
    else if (v !== undefined && v !== null && v !== false) n.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children) if (c) n.append(c);
  return n;
}

function toast(msg, kind = '') {
  const t = el('div', { class: `toast ${kind}`, text: msg });
  $('toasts').append(t);
  setTimeout(() => t.remove(), kind === 'err' ? 7000 : 3500);
}

/** Appelle une API { ok, data | error } et affiche l'erreur. */
async function api(promise, okMsg) {
  const r = await promise;
  if (!r.ok) { toast(r.error, 'err'); return null; }
  if (okMsg) toast(okMsg, 'ok');
  return r.data === undefined ? true : r.data;
}

function fmtGo(mb) { return `${(mb / 1024).toFixed(1).replace('.0', '')} Go`; }
function fmtNum(n) { return n >= 1e6 ? `${(n / 1e6).toFixed(1)} M` : n >= 1e3 ? `${Math.round(n / 1e3)} k` : String(n); }

async function saveSettings(patch) {
  state.settings = await L.saveSettings({ ...state.settings, ...patch });
}

/* ===================== Navigation ===================== */

function showView(v) {
  $('loginView').classList.toggle('hidden', v !== 'login');
  $('appView').classList.toggle('hidden', v !== 'app');
}

function goto(page) {
  document.querySelectorAll('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.page === page));
  document.querySelectorAll('.page').forEach(p => p.classList.toggle('active', p.id === `page-${page}`));
  if (page === 'news') { $('newsBadge').classList.add('hidden'); try { localStorage.setItem('newsSeen', String(state.news.length)); } catch { /* */ } }
  if (page === 'mods') loadMods();
  if (page === 'vote') loadVotes();
  if (page === 'shots') loadShots();
  if (page === 'skin') initSkinPage();
  if (page === 'settings') fillSettings();
}
document.querySelectorAll('.nav-btn').forEach(b => b.addEventListener('click', () => goto(b.dataset.page)));
document.querySelectorAll('[data-goto]').forEach(b => b.addEventListener('click', () => goto(b.dataset.goto)));

$('btnMin').onclick = () => L.minimize();
$('btnClose').onclick = () => L.close();
$('btnWebsite').onclick = () => L.openWebsite();

/* ===================== Braises ===================== */
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

/* ===================== Compte ===================== */

function isAdmin() {
  return !!(state.profile && state.canEditNews && state.admins.includes(String(state.profile.uuid).toLowerCase().replace(/-/g, '')));
}

function setProfile(p) {
  state.profile = p;
  $('btnNewsEdit').classList.toggle('hidden', !isAdmin());
  $('playerName').textContent = p.name;
  $('settingsName').textContent = p.name;
  $('avatar').src = `https://mc-heads.net/avatar/${encodeURIComponent(p.uuid)}/96`;
  showView('app');
}

$('btnLogin').onclick = async () => {
  $('btnLogin').disabled = true;
  $('loginError').textContent = '';
  const r = await L.login();
  $('btnLogin').disabled = false;
  if (r.ok) { setProfile(r.profile); if (!state.settings.onboarded) startOnboarding(); }
  else $('loginError').textContent = r.error;
};
$('btnLogout').onclick = async () => { await L.logout(); state.skinLoaded = false; showView('login'); };

/* ===================== Actualités ===================== */

function newsMeta(n) {
  return el('div', { class: 'news-meta' }, n.tag ? el('span', { class: 'tag', text: n.tag }) : null, n.date ? el('span', { text: n.date }) : null);
}

function renderNews(info) {
  if (info.message) {
    $('serverMessage').textContent = info.message;
    $('serverMessage').classList.remove('hidden');
  }
  state.news = info.news;

  const home = $('homeNews');
  home.replaceChildren();
  if (!info.news.length) home.append(el('div', { class: 'empty', text: 'Aucune actualité pour le moment.' }));
  for (const n of info.news.slice(0, 4)) {
    home.append(el('div', { class: 'card news-card', onclick: () => { goto('news'); focusNews(n.id); } },
      el('h3', { text: n.title }), el('p', { text: n.text }), newsMeta(n)));
  }

  const list = $('newsList');
  list.replaceChildren();
  if (!info.news.length) list.append(el('div', { class: 'empty', text: 'Aucune actualité pour le moment. Reviens bientôt !' }));
  for (const n of info.news) {
    list.append(el('article', { class: 'card news-full', id: `news-${n.id}` },
      n.image ? el('img', { src: n.image, alt: '', loading: 'lazy' }) : null,
      el('div', { class: 'body' },
        newsMeta(n), el('h3', { text: n.title }), el('p', { text: n.text }),
        n.url ? el('button', { class: 'btn-secondary', text: 'En savoir plus ↗', onclick: () => L.openUrl(n.url) }) : null)));
  }

  let seen = 0;
  try { seen = Number(localStorage.getItem('newsSeen') || 0); } catch { /* */ }
  if (info.news.length > seen) $('newsBadge').classList.remove('hidden');
}

function focusNews(id) {
  const card = $(`news-${id}`);
  if (!card) return;
  document.querySelectorAll('.news-full.focus').forEach(c => c.classList.remove('focus'));
  card.classList.add('focus');
  card.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/* ===================== Édition des actualités (admins) ===================== */

let neDraft = [];

function today() {
  const d = new Date();
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

function neField(n, key, label, opts = {}) {
  const input = opts.area
    ? el('textarea', { maxlength: opts.max || 4000, placeholder: opts.ph || '' })
    : el('input', { type: 'text', maxlength: opts.max || 120, placeholder: opts.ph || '' });
  input.value = n[key] || '';
  input.oninput = () => { n[key] = input.value; };
  return el('label', { class: opts.full ? 'full' : '' }, label, input);
}

function renderNewsEditor() {
  const list = $('neList');
  list.replaceChildren();
  if (!neDraft.length) list.append(el('div', { class: 'empty', text: 'Aucune actualité. Clique sur « + Nouvelle actualité ».' }));
  neDraft.forEach((n, i) => {
    const move = d => { const j = i + d; if (j < 0 || j >= neDraft.length) return; [neDraft[i], neDraft[j]] = [neDraft[j], neDraft[i]]; renderNewsEditor(); };
    list.append(el('div', { class: 'ne-item' },
      el('div', { class: 'ne-tools' },
        el('span', { class: 'num', text: `#${i + 1}` }),
        el('button', { class: 'icon-btn small', title: 'Monter', text: '↑', onclick: () => move(-1) }),
        el('button', { class: 'icon-btn small', title: 'Descendre', text: '↓', onclick: () => move(1) }),
        el('button', { class: 'icon-btn small', title: 'Supprimer', text: '🗑', onclick: () => { neDraft.splice(i, 1); renderNewsEditor(); } })),
      neField(n, 'title', 'Titre *', { full: true, max: 120, ph: 'Event Halloween ce week-end !' }),
      neField(n, 'text', 'Texte', { full: true, area: true, ph: 'Détails de l\'actualité…' }),
      neField(n, 'tag', 'Étiquette', { max: 24, ph: 'Event, Mise à jour…' }),
      neField(n, 'date', 'Date', { max: 40 }),
      neField(n, 'image', 'Image (https://…)', { max: 500 }),
      neField(n, 'url', 'Lien « En savoir plus » (https://…)', { max: 500 })));
  });
}

async function openNewsEditor() {
  const fresh = await api(L.news.get());
  neDraft = (fresh || state.news).map(({ id, ...n }) => ({ ...n }));
  renderNewsEditor();
  $('newsEditor').classList.remove('hidden');
}

function closeNewsEditor() { $('newsEditor').classList.add('hidden'); }

$('btnNewsEdit').onclick = openNewsEditor;
$('neClose').onclick = closeNewsEditor;
$('neCancel').onclick = closeNewsEditor;
$('neAdd').onclick = () => {
  neDraft.unshift({ title: '', text: '', tag: '', date: today(), image: '', url: '' });
  renderNewsEditor();
  $('neList').scrollTop = 0;
};
$('nePublish').onclick = async () => {
  const bad = neDraft.find(n => !String(n.title || '').trim());
  if (bad) { toast('Chaque actualité doit avoir un titre.', 'err'); return; }
  const badLink = neDraft.find(n => (n.image && !n.image.startsWith('https://')) || (n.url && !n.url.startsWith('https://')));
  if (badLink) { toast('Les liens et images doivent commencer par https://', 'err'); return; }
  const btn = $('nePublish');
  btn.disabled = true;
  btn.textContent = 'Publication…';
  const r = await api(L.news.publish(neDraft));
  btn.disabled = false;
  btn.textContent = 'Publier pour tous les joueurs';
  if (!r) return;
  toast(`${r.count} actualité(s) publiée(s) ✔`, 'ok');
  renderNews({ news: r.news, message: '' });
  closeNewsEditor();
};

/* ===================== Versions ===================== */

async function loadVersions() {
  const sel = $('versionSelect');
  const fill = list => {
    sel.replaceChildren();
    for (const v of list) {
      sel.append(el('option', { value: v.id, text: v.official ? `${v.id}  ★ SpartaCube` : v.id }));
    }
    sel.value = state.version;
  };
  fill([{ id: state.officialVersion, official: true }]);
  const list = await api(L.versions());
  if (list && list.length) fill(list);
  updateVersionHint();
}

function updateVersionHint() {
  const official = state.version === state.officialVersion;
  $('versionHint').textContent = official ? 'Modpack serveur inclus' : 'Version perso : sans modpack serveur';
  $('modsVersion').textContent = state.version;
}

$('versionSelect').addEventListener('change', async e => {
  state.version = e.target.value;
  state.modsLoadedFor = null;
  updateVersionHint();
  await saveSettings({ selectedVersion: state.version === state.officialVersion ? '' : state.version });
});

/* ===================== Statut serveur ===================== */

async function refreshStatus() {
  const s = await L.status();
  const st = $('status');
  st.classList.toggle('on', s.online);
  st.classList.toggle('off', !s.online);
  $('statusText').textContent = s.online
    ? `${s.players ? `${s.players.online}/${s.players.max} joueurs` : 'En ligne'} · ${s.latency} ms`
    : 'Serveur hors ligne';
  const heads = $('onlineHeads');
  heads.replaceChildren();
  for (const p of (s.online && s.players && s.players.sample) || []) {
    heads.append(el('img', { src: `https://mc-heads.net/avatar/${encodeURIComponent(p.id)}/32`, alt: p.name, title: p.name }));
  }
}

/* ===================== Jouer ===================== */

$('btnPlay').onclick = async () => {
  if (state.playing) return;
  state.playing = true;
  $('btnPlay').disabled = true;
  $('versionSelect').disabled = true;
  $('btnPlay').textContent = 'PRÉPARATION';
  $('playError').textContent = '';
  $('progressBox').classList.remove('hidden');
  const r = await L.play();
  if (!r.ok) {
    resetPlay();
    $('progressBox').classList.add('hidden');
    $('playError').textContent = r.error;
  }
};
function resetPlay() {
  state.playing = false;
  $('btnPlay').disabled = false;
  $('versionSelect').disabled = false;
  $('btnPlay').textContent = 'JOUER';
}
L.onProgress(p => {
  $('progressLabel').textContent = p.label;
  const pct = p.total ? Math.min(100, Math.round((p.done / p.total) * 100)) : 0;
  $('progressFill').style.width = `${pct}%`;
});
L.onGameStarted(() => {
  $('progressBox').classList.add('hidden');
  $('btnPlay').textContent = 'EN JEU';
});
L.onGameClosed(resetPlay);

/* ===================== Mods ===================== */

document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x === t));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === `tab-${t.dataset.tab}`));
  if (t.dataset.tab === 'explore' && !$('modResults').childElementCount) searchMods(true);
}));

function modIcon(src) {
  return src ? el('img', { class: 'mod-icon', src, alt: '' }) : el('div', { class: 'mod-icon', text: '🧩' });
}

async function loadMods(force = false) {
  if (!force && state.modsLoadedFor === state.version) return;
  state.modsLoadedFor = state.version;
  const official = state.version === state.officialVersion;
  const locked = official && !state.allowUserMods;
  $('btnModAdd').disabled = locked;
  $('modsHint').textContent = locked
    ? 'Le serveur SpartaCube impose sa liste de mods : les mods personnels sont désactivés sur la version officielle.'
    : official
      ? 'Les mods marqués « Serveur » sont fournis par SpartaCube et mis à jour automatiquement. Tu peux ajouter les tiens (mods côté client uniquement).'
      : `Mods de ton instance Minecraft ${state.version}.`;

  loadProfiles();
  const list = await api(L.mods.list(state.version));
  const box = $('modsList');
  box.replaceChildren();
  if (!list) return;
  $('modsCount').textContent = list.length ? `(${list.length})` : '';
  if (!list.length) box.append(el('div', { class: 'empty', text: 'Aucun mod installé. Ajoute un .jar ou explore Modrinth.' }));
  for (const m of list) {
    const sw = el('button', { class: `switch ${m.enabled ? 'on' : ''}`, title: m.enabled ? 'Désactiver' : 'Activer' });
    sw.onclick = async () => {
      if (await api(L.mods.toggle(state.version, m.file, !m.enabled))) loadMods(true);
    };
    const del = el('button', { class: 'icon-btn small', title: 'Supprimer', text: '🗑' });
    del.onclick = async () => {
      if (await api(L.mods.remove(state.version, m.file), `${m.name} supprimé`)) loadMods(true);
    };
    box.append(el('div', { class: `card mod-row ${m.enabled ? '' : 'disabled'}` },
      modIcon(m.icon),
      el('div', { class: 'mod-info' }, el('b', { text: m.name }), m.version ? el('span', { class: 'ver', text: m.version }) : null, el('p', { text: m.description || m.file })),
      el('div', { class: 'mod-actions' }, m.required ? el('span', { class: 'lock', text: '🔒 Serveur' }) : null, m.required ? null : sw, m.required ? null : del)));
  }
}

$('btnModAdd').onclick = async () => {
  const added = await api(L.mods.add(state.version));
  if (added && added.length) { toast(`${added.length} mod(s) ajouté(s)`, 'ok'); loadMods(true); }
};
$('btnModFolder').onclick = () => L.mods.openFolder(state.version);

async function searchMods(reset) {
  if (reset) { state.modrinthOffset = 0; $('modResults').replaceChildren(); }
  $('btnModSearch').disabled = true;
  const res = await api(L.mods.search(state.version, $('modSearch').value.trim(), state.modrinthOffset));
  $('btnModSearch').disabled = false;
  if (!res) return;
  const box = $('modResults');
  if (reset && !res.hits.length) box.append(el('div', { class: 'empty', text: `Aucun mod Fabric trouvé pour ${state.version}.` }));
  const locked = state.version === state.officialVersion && !state.allowUserMods;
  for (const h of res.hits) {
    const btn = el('button', { class: h.installed ? 'btn-secondary' : 'btn-primary', text: h.installed ? 'Installé ✔' : 'Installer' });
    btn.disabled = h.installed || locked;
    btn.onclick = async () => {
      btn.disabled = true; btn.textContent = 'Installation…';
      const files = await api(L.mods.install(state.version, h.id));
      if (files) {
        btn.textContent = 'Installé ✔'; btn.className = 'btn-secondary';
        toast(`${h.title} installé${files.length > 1 ? ` (+ ${files.length - 1} dépendance(s))` : ''}`, 'ok');
        state.modsLoadedFor = null;
      } else { btn.disabled = false; btn.textContent = 'Installer'; }
    };
    box.append(el('div', { class: 'card mod-row' },
      modIcon(h.icon),
      el('div', { class: 'mod-info' }, el('b', { text: h.title }), el('span', { class: 'ver', text: `par ${h.author} · ${fmtNum(h.downloads)} téléchargements` }), el('p', { text: h.description })),
      el('div', { class: 'mod-actions' }, h.clientSide === 'unsupported' ? el('span', { class: 'lock', text: 'Serveur uniquement' }) : null, btn)));
  }
  state.modrinthOffset += res.hits.length;
  $('btnModMore').classList.toggle('hidden', state.modrinthOffset >= res.total);
}
$('btnModSearch').onclick = () => searchMods(true);
$('modSearch').addEventListener('keydown', e => { if (e.key === 'Enter') searchMods(true); });
$('btnModMore').onclick = () => searchMods(false);

/* ===================== Profils de mods ===================== */

async function loadProfiles() {
  const p = await api(L.profiles.list(state.version));
  if (!p) return;
  const sel = $('profileSelect');
  const keep = sel.value;
  sel.replaceChildren();
  const g1 = el('optgroup', { label: 'Profils SpartaCube' });
  for (const n of p.builtin) g1.append(el('option', { value: n, text: n }));
  sel.append(g1);
  if (p.custom.length) {
    const g2 = el('optgroup', { label: 'Mes profils' });
    for (const n of p.custom) g2.append(el('option', { value: n, text: n }));
    sel.append(g2);
  }
  if ([...sel.options].some(o => o.value === keep)) sel.value = keep;
  $('btnProfileDelete').disabled = !p.custom.includes(sel.value);
  sel.onchange = () => { $('btnProfileDelete').disabled = !p.custom.includes(sel.value); };
}
$('btnProfileApply').onclick = async () => {
  const name = $('profileSelect').value;
  const n = await api(L.profiles.apply(state.version, name));
  if (n !== null) { toast(n ? `Profil « ${name} » appliqué (${n} mod(s) modifié(s))` : `Profil « ${name} » déjà actif`, 'ok'); loadMods(true); }
};
$('btnProfileSave').onclick = async () => {
  const name = $('profileName').value.trim();
  if (!name) { toast('Donne un nom au profil (ex. Mon PvP)'); $('profileName').focus(); return; }
  $('profileName').value = '';
  if (await api(L.profiles.save(state.version, name), `Profil « ${name.trim()} » enregistré`)) { await loadProfiles(); $('profileSelect').value = name.trim(); }
};
$('btnProfileDelete').onclick = async () => {
  const name = $('profileSelect').value;
  if (await api(L.profiles.remove(state.version, name), `Profil « ${name} » supprimé`)) loadProfiles();
};

/* ===================== Voter ===================== */

let voteTimer = null;
function fmtDuration(ms) {
  const m = Math.ceil(ms / 60000);
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')}` : `${m} min`;
}
async function loadVotes() {
  const st = await api(L.votes.status());
  if (!st) return;
  renderVotes(st);
  clearInterval(voteTimer);
  voteTimer = setInterval(() => { if ($('page-vote').classList.contains('active')) renderVotes(st); else clearInterval(voteTimer); }, 1000);
}
function renderVotes(st) {
  $('voteMonth').textContent = st.monthCount;
  const box = $('voteSites');
  box.replaceChildren();
  if (!st.sites.length) box.append(el('div', { class: 'empty', text: 'Aucun site de vote configuré pour le moment.' }));
  const now = Date.now();
  for (const site of st.sites) {
    const ready = !site.nextAt || site.nextAt <= now;
    const btn = el('button', { class: ready ? 'btn-primary' : 'btn-secondary', text: ready ? '★ Voter maintenant' : 'Voter quand même' });
    btn.onclick = async () => { const s2 = await api(L.votes.open(site.url)); if (s2) { toast('Merci pour ton vote ! 🔥', 'ok'); loadVotes(); updateVoteBadge(s2); } };
    box.append(el('div', { class: 'card vote-card' },
      el('h3', { text: site.name }),
      el('div', { class: `timer ${ready ? 'ready' : ''}`, text: ready ? '● Disponible' : `Prochain vote dans ${fmtDuration(site.nextAt - now)}` }),
      btn));
  }
}
function updateVoteBadge(st) {
  const now = Date.now();
  $('voteBadge').classList.toggle('hidden', !st || !st.sites.some(x => !x.nextAt || x.nextAt <= now));
}

/* ===================== Captures ===================== */

let currentShot = null;
async function loadShots() {
  $('shotsVersion').textContent = state.version;
  const grid = $('shotsGrid');
  grid.replaceChildren(el('div', { class: 'empty', text: 'Chargement…' }));
  const list = await api(L.shots.list(state.version));
  grid.replaceChildren();
  if (!list) return;
  if (!list.length) grid.append(el('div', { class: 'empty', text: 'Aucune capture pour l\'instant : appuie sur F2 en jeu !' }));
  for (const s of list) {
    if (!s.thumb) continue;
    grid.append(el('div', { class: 'shot', onclick: () => openShot(s.name) },
      el('img', { src: s.thumb, alt: '', loading: 'lazy' }),
      el('span', { text: new Date(s.mtime).toLocaleString('fr-FR') })));
  }
}
async function openShot(name) {
  const data = await api(L.shots.full(state.version, name));
  if (!data) return;
  currentShot = name;
  $('lightboxImg').src = data;
  $('lightboxName').textContent = name;
  $('lightbox').classList.remove('hidden');
}
$('lbClose').onclick = () => $('lightbox').classList.add('hidden');
$('lightbox').addEventListener('click', e => { if (e.target.id === 'lightbox') $('lightbox').classList.add('hidden'); });
$('lbCopy').onclick = () => api(L.shots.copy(state.version, currentShot), 'Capture copiée : colle-la sur Discord (Ctrl+V)');
$('lbReveal').onclick = () => L.shots.reveal(state.version, currentShot);
$('lbDelete').onclick = async () => {
  if (await api(L.shots.remove(state.version, currentShot), 'Capture mise à la corbeille')) { $('lightbox').classList.add('hidden'); loadShots(); }
};
$('btnShotsFolder').onclick = () => L.shots.reveal(state.version, null);

/* ===================== Accueil guidé (premier lancement) ===================== */

const onb = { step: 1, hw: null, preset: 'medium' };
function onbShow(step) {
  onb.step = step;
  document.querySelectorAll('.onb-step').forEach(d => d.classList.toggle('hidden', Number(d.dataset.step) !== step));
  document.querySelectorAll('.onb-steps i').forEach((d, i) => d.classList.toggle('on', i < step));
  $('onbBack').classList.toggle('hidden', step === 1);
  $('onbNext').textContent = step === 3 ? 'C\'est parti !' : 'Suivant';
}
function onbPresets() {
  const box = $('onbPresets');
  box.replaceChildren();
  const desc = { low: 'Fluide avant tout', medium: 'Beau et fluide', high: 'Shaders et grande distance' };
  for (const [k, p] of Object.entries(onb.hw.presets)) {
    const b = el('button', { class: k === onb.preset ? 'active' : '' },
      el('b', { text: p.label }), el('small', { text: desc[k] }), k === onb.hw.preset ? el('span', { class: 'rec', text: 'Conseillé' }) : null);
    b.onclick = () => { onb.preset = k; $('onbShaders').checked = k === 'high'; onbPresets(); };
    box.append(b);
  }
}
async function startOnboarding() {
  $('onboarding').classList.remove('hidden');
  onbShow(1);
  const r = await L.detectHardware();
  onb.hw = r.ok ? r.data : { preset: 'medium', gpu: '?', ramGb: Math.round(state.totalRamMb / 1024), cores: '?', recommendedRamMb: 4096, presets: { low: { label: 'PC modeste' }, medium: { label: 'Équilibré' }, high: { label: 'PC costaud' } } };
  onb.preset = onb.hw.preset;
  $('onbHardware').replaceChildren(
    el('div', { text: `Carte graphique : ${onb.hw.gpu}` }),
    el('div', { text: `Mémoire : ${onb.hw.ramGb} Go · Processeur : ${onb.hw.cores} cœurs` }));
  const max = Math.max(2048, Math.floor(state.totalRamMb * 0.75 / 512) * 512);
  $('onbRam').max = max;
  $('onbRam').value = Math.min(onb.hw.recommendedRamMb, max);
  const upd = () => { $('onbRamLabel').textContent = fmtGo(+$('onbRam').value); $('onbRamHint').textContent = `Conseillé pour ton PC : ${fmtGo(Math.min(onb.hw.recommendedRamMb, max))}`; };
  $('onbRam').oninput = upd;
  upd();
  $('onbShaders').checked = onb.preset === 'high';
  onbPresets();
}
$('onbBack').onclick = () => onbShow(Math.max(1, onb.step - 1));
$('onbNext').onclick = async () => {
  if (onb.step < 3) { onbShow(onb.step + 1); return; }
  const skip = [];
  if (!$('onbMinimap').checked) skip.push('1bokaNcj');
  if (!$('onbShaders').checked) skip.push('YL57xq9U');
  const ram = +$('onbRam').value;
  await saveSettings({ ramMaxMb: ram, ramMinMb: Math.min(2048, ram), graphicsPreset: onb.preset, skipDefaultMods: skip, onboarded: true });
  $('onboarding').classList.add('hidden');
  toast('Tout est prêt, bon jeu sur SpartaCube ! ⚔', 'ok');
};

/* ===================== Thème ===================== */

function applyTheme(serverTheme) {
  let t = state.settings.theme && state.settings.theme !== 'auto' ? state.settings.theme : serverTheme;
  if (!t || t === 'auto') {
    const d = new Date();
    const m = d.getMonth() + 1, day = d.getDate();
    t = (m === 10 && day >= 15) || (m === 11 && day <= 2) ? 'halloween' : (m === 12 || (m === 1 && day <= 7)) ? 'snow' : 'fire';
  }
  document.body.classList.remove('theme-fire', 'theme-snow', 'theme-halloween');
  document.body.classList.add(`theme-${t}`);
}

/* ===================== Skin ===================== */

let viewer = null;
let editor = null;
let viewerQueued = false;

const PALETTE = ['#000000', '#ffffff', '#3b2630', '#5b3a24', '#d9a07a', '#f1c27d', '#c4122f', '#ff5a1f', '#ffb02e', '#e2a52b', '#2e7d32', '#1565c0', '#6a1b9a', '#9aa0a6', '#455a64', '#ec407a'];

function refreshViewer() {
  if (!viewer || viewerQueued) return;
  viewerQueued = true;
  requestAnimationFrame(() => {
    viewerQueued = false;
    viewer.loadSkin(editor.tex, { model: state.skinVariant === 'slim' ? 'slim' : 'default' });
  });
}

function setVariant(v) {
  state.skinVariant = v === 'slim' ? 'slim' : 'classic';
  document.querySelectorAll('#modelSeg button').forEach(b => b.classList.toggle('active', b.dataset.model === state.skinVariant));
  refreshViewer();
}

function setTool(t) {
  editor.tool = t;
  document.querySelectorAll('#toolSeg button').forEach(b => b.classList.toggle('active', b.dataset.tool === t));
}

async function initSkinPage() {
  if (!editor) {
    editor = new window.SkinEditor($('skinEditor'), {
      onChange: refreshViewer,
      onPick: c => { $('colorPicker').value = c; setTool('pencil'); }
    });
    try {
      viewer = new window.skinview3d.SkinViewer({ canvas: $('skinViewer'), width: 290, height: 380 });
      viewer.zoom = 0.85;
      viewer.animation = new window.skinview3d.IdleAnimation();
    } catch (e) {
      toast(`Aperçu 3D indisponible : ${e.message}`, 'err');
    }
    refreshViewer();

    const pal = $('palette');
    for (const c of PALETTE) {
      const b = el('button', { title: c });
      b.style.background = c;
      b.onclick = () => { editor.color = c; $('colorPicker').value = c; if (editor.tool !== 'fill') setTool('pencil'); };
      pal.append(b);
    }
    $('colorPicker').addEventListener('input', e => { editor.color = e.target.value; });
    document.querySelectorAll('#toolSeg button').forEach(b => b.addEventListener('click', () => setTool(b.dataset.tool)));
    document.querySelectorAll('#modelSeg button').forEach(b => b.addEventListener('click', () => setVariant(b.dataset.model)));
    document.querySelectorAll('#animSeg button').forEach(b => b.addEventListener('click', () => {
      document.querySelectorAll('#animSeg button').forEach(x => x.classList.toggle('active', x === b));
      if (!viewer) return;
      viewer.autoRotate = b.dataset.anim === 'rotate';
      viewer.animation = b.dataset.anim === 'walk' ? new window.skinview3d.WalkingAnimation() : new window.skinview3d.IdleAnimation();
    }));
    $('btnUndo').onclick = () => editor.undo();
    $('btnRedo').onclick = () => editor.redo();
    $('chkGrid').onchange = e => { editor.showGrid = e.target.checked; editor.render(); };
    $('chkZones').onchange = e => { editor.showZones = e.target.checked; editor.render(); };
    document.addEventListener('keydown', e => {
      if (!$('page-skin').classList.contains('active') || e.target.tagName === 'INPUT') return;
      if (e.ctrlKey && e.key.toLowerCase() === 'z') { e.preventDefault(); editor.undo(); }
      else if (e.ctrlKey && e.key.toLowerCase() === 'y') { e.preventDefault(); editor.redo(); }
      else if (!e.ctrlKey) {
        const map = { b: 'pencil', e: 'eraser', f: 'fill', i: 'picker' };
        if (map[e.key.toLowerCase()]) setTool(map[e.key.toLowerCase()]);
      }
    });
  }
  if (!state.skinLoaded) {
    state.skinLoaded = true;
    await loadCurrentSkin(true);
    loadLibrary();
  }
}

async function loadCurrentSkin(quiet = false) {
  const prof = await api(L.skin.profile());
  if (!prof) return;
  if (prof.skin) {
    try { await editor.load(prof.skin.dataUrl); setVariant(prof.skin.variant); } catch (e) { toast(e.message, 'err'); }
  } else if (!quiet) toast('Ton compte utilise un skin par défaut de Minecraft');
  renderCapes(prof.capes);
}

function renderCapes(capes) {
  const sel = $('capeSelect');
  sel.replaceChildren(el('option', { value: '', text: capes.length ? 'Aucune' : 'Aucune cape sur ce compte' }));
  for (const c of capes) sel.append(el('option', { value: c.id, text: c.alias || 'Cape' }));
  const active = capes.find(c => c.active);
  sel.value = active ? active.id : '';
  sel.disabled = !capes.length;
  sel.dataset.capes = JSON.stringify(capes.map(c => ({ id: c.id, dataUrl: c.dataUrl })));
  showCape(sel.value);
}

function showCape(id) {
  if (!viewer) return;
  const capes = JSON.parse($('capeSelect').dataset.capes || '[]');
  const c = capes.find(x => x.id === id);
  if (c && c.dataUrl) viewer.loadCape(c.dataUrl); else viewer.resetCape();
}

$('capeSelect').addEventListener('change', async e => {
  const prof = await api(L.skin.cape(e.target.value), e.target.value ? 'Cape équipée' : 'Cape retirée');
  if (prof) renderCapes(prof.capes);
});

$('btnSkinImport').onclick = () => $('skinFile').click();
$('skinFile').addEventListener('change', e => {
  const f = e.target.files[0];
  e.target.value = '';
  if (!f) return;
  const reader = new FileReader();
  reader.onload = () => editor.load(reader.result).then(() => toast('Skin importé dans l\'éditeur', 'ok'), err => toast(err.message, 'err'));
  reader.readAsDataURL(f);
});
$('btnSkinCurrent').onclick = () => loadCurrentSkin();
$('btnSkinNew').onclick = () => { editor.newTemplate(); setVariant('classic'); };
$('btnSkinPlayer').onclick = async () => {
  const name = $('skinPlayer').value.trim();
  if (!name) return;
  const s = await api(L.skin.fromPlayer(name));
  if (s) { await editor.load(s.dataUrl); setVariant(s.variant); toast(`Skin de ${name} chargé dans l'éditeur`, 'ok'); }
};
$('skinPlayer').addEventListener('keydown', e => { if (e.key === 'Enter') $('btnSkinPlayer').click(); });
$('btnSkinDownload').onclick = () => {
  const a = el('a', { href: editor.toDataURL(), download: `skin-${state.profile ? state.profile.name : 'spartacube'}.png` });
  a.click();
};
$('btnSkinApply').onclick = async () => {
  $('btnSkinApply').disabled = true;
  const prof = await api(L.skin.upload(editor.toDataURL(), state.skinVariant), 'Skin appliqué sur ton compte Minecraft !');
  $('btnSkinApply').disabled = false;
  if (prof) { renderCapes(prof.capes); $('avatar').src = `https://mc-heads.net/avatar/${encodeURIComponent(state.profile.uuid)}/96?t=${Date.now()}`; }
};
$('btnSkinReset').onclick = async () => {
  if (await api(L.skin.reset(), 'Skin par défaut remis sur ton compte')) loadCurrentSkin(true);
};
$('btnSkinSave').onclick = async () => {
  const name = `Skin du ${new Date().toLocaleDateString('fr-FR')} ${new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`;
  const list = await api(L.skin.librarySave(name, editor.toDataURL(), state.skinVariant), 'Skin gardé dans ta bibliothèque');
  if (list) renderLibrary(list);
};

async function loadLibrary() {
  const list = await api(L.skin.library());
  if (list) renderLibrary(list);
}

function renderLibrary(list) {
  const box = $('skinLibrary');
  box.replaceChildren();
  if (!list.length) box.append(el('div', { class: 'empty', text: 'Ta bibliothèque est vide : clique sur « Garder dans ma bibliothèque » pour y ranger tes skins.' }));
  for (const s of list) {
    const open = () => editor.load(s.dataUrl).then(() => setVariant(s.variant));
    box.append(el('div', { class: 'lib-item' },
      el('img', { src: s.dataUrl, alt: '', title: 'Ouvrir dans l\'éditeur', onclick: open }),
      el('span', { text: s.name }),
      el('div', { class: 'row' },
        el('button', { text: 'Ouvrir', onclick: open }),
        el('button', { text: '✕', title: 'Supprimer', onclick: async () => { const l = await api(L.skin.libraryDelete(s.id)); if (l) renderLibrary(l); } }))));
  }
}

/* ===================== Réglages ===================== */

function fillSettings() {
  const s = state.settings;
  const max = Math.max(2048, Math.floor(state.totalRamMb * 0.75 / 512) * 512);
  $('ramMax').max = max; $('ramMin').max = max;
  $('ramMax').value = Math.min(s.ramMaxMb, max);
  $('ramMin').value = Math.min(s.ramMinMb, max);
  $('autoConnect').checked = s.autoConnect;
  $('closeOnStart').checked = s.closeLauncherOnStart;
  $('jvmArgs').value = s.jvmArgs || '';
  $('graphicsPreset').value = s.graphicsPreset || 'medium';
  $('themeSelect').value = s.theme || 'auto';
  updateRamLabels();
}
function updateRamLabels() {
  if (+$('ramMin').value > +$('ramMax').value) $('ramMin').value = $('ramMax').value;
  $('ramMaxLabel').textContent = fmtGo(+$('ramMax').value);
  $('ramMinLabel').textContent = fmtGo(+$('ramMin').value);
}
$('ramMax').addEventListener('input', updateRamLabels);
$('ramMin').addEventListener('input', updateRamLabels);
$('btnSaveSettings').onclick = async () => {
  await saveSettings({
    ramMaxMb: +$('ramMax').value,
    ramMinMb: +$('ramMin').value,
    autoConnect: $('autoConnect').checked,
    closeLauncherOnStart: $('closeOnStart').checked,
    jvmArgs: $('jvmArgs').value,
    graphicsPreset: $('graphicsPreset').value,
    theme: $('themeSelect').value
  });
  applyTheme(state.serverTheme);
  toast('Réglages enregistrés', 'ok');
};
$('btnFolder').onclick = () => L.openGameFolder();
$('btnLogs').onclick = () => L.openLogs();
$('btnOnboarding').onclick = () => startOnboarding();

/* ===================== Mise à jour du launcher ===================== */

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

/* ===================== Démarrage ===================== */

(async () => {
  const info = await L.init();
  state.settings = info.settings;
  state.totalRamMb = info.totalRamMb;
  state.officialVersion = info.officialVersion;
  state.version = info.selectedVersion;
  state.allowUserMods = info.allowUserMods;
  $('appVersion').textContent = `v${info.appVersion}`;
  state.serverTheme = info.theme;
  applyTheme(info.theme);
  state.admins = info.admins || [];
  state.canEditNews = info.canEditNews;
  renderNews(info);
  if (info.destinations.length) {
    const sel = $('destSelect');
    sel.append(el('option', { value: '', text: 'Aucune (point de connexion habituel)' }));
    for (const d of info.destinations) sel.append(el('option', { value: d.command, text: `${d.name}  (${d.command})` }));
    sel.value = info.settings.destination || '';
    sel.onchange = () => saveSettings({ destination: sel.value });
    $('destBox').classList.remove('hidden');
  }
  if (info.profile) { setProfile(info.profile); if (!info.settings.onboarded) startOnboarding(); }
  else showView('login');
  if (info.hasVoteSites) { L.votes.status().then(r => r.ok && updateVoteBadge(r.data)); setInterval(() => L.votes.status().then(r => r.ok && updateVoteBadge(r.data)), 60_000); }
  $('splash').classList.add('fade');
  loadVersions();
  refreshStatus();
  setInterval(refreshStatus, 30_000);
})();
