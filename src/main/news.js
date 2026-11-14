'use strict';
// Actualités : lues depuis le flux RSS du site (Azuriom : https://spartacube.fr/api/rss).
// Dernière version réussie gardée en cache pour l'affichage hors ligne.
const fs = require('fs');
const path = require('path');

const decode = s => String(s || '')
  .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
  .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#0*39;|&apos;/g, "'")
  .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
  .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
  .replace(/&nbsp;/g, ' ')
  .replace(/&amp;/g, '&');

/** HTML → texte brut lisible (paragraphes, listes, retours à la ligne). */
function htmlToText(html) {
  // Le flux échappe le HTML (&lt;p&gt;) : on décode une fois, on retire les balises, puis on décode le texte
  const raw = String(html || '').replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1');
  const unescaped = /&lt;\/?[a-z]/i.test(raw) ? decode(raw) : raw;
  return decode(unescaped
    .replace(/<(script|style)[\s\S]*?<\/\1>/gi, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<li[^>]*>/gi, '\n• ')
    .replace(/<\/li>/gi, '')
    .replace(/<\/(p|div|h[1-6]|ul|ol|blockquote)>/gi, '\n')
    .replace(/<[^>]+>/g, ''))
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function tag(xml, name) {
  const m = xml.match(new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${name}>`, 'i'));
  return m ? m[1] : '';
}

function frDate(s) {
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return '';
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

const https = u => { try { return new URL(u).protocol === 'https:' ? u : null; } catch { return null; } };

function parseRss(xml) {
  const items = xml.match(/<item[\s>][\s\S]*?<\/item>/gi) || [];
  return items.slice(0, 30).map(it => {
    const enclosure = (it.match(/<enclosure[^>]*url="([^"]+)"/i) || [])[1];
    const content = tag(it, 'content:encoded');
    const desc = tag(it, 'description');
    const body = htmlToText(content) || htmlToText(desc);
    const summary = htmlToText(desc);
    const text = summary && body && !body.startsWith(summary) ? `${summary}\n\n${body}` : body || summary;
    return {
      title: htmlToText(tag(it, 'title')).slice(0, 120),
      text: text.slice(0, 4000),
      date: frDate(htmlToText(tag(it, 'pubDate'))),
      tag: htmlToText(tag(it, 'dc:creator')).slice(0, 24),
      image: https(decode(enclosure || '')),
      url: https(htmlToText(tag(it, 'link')))
    };
  }).filter(n => n.title);
}

async function fetchNews(feedUrl, cacheDir) {
  const cache = cacheDir ? path.join(cacheDir, 'news-cache.json') : null;
  if (feedUrl) {
    try {
      const u = new URL(feedUrl);
      if (u.protocol !== 'https:') throw new Error('Flux non HTTPS');
      const ctrl = new AbortController();
      const t = setTimeout(() => ctrl.abort(), 8000);
      const r = await fetch(feedUrl, { signal: ctrl.signal, headers: { 'User-Agent': 'SpartaCube-Launcher' } }).finally(() => clearTimeout(t));
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const xml = (await r.text()).slice(0, 2_000_000);
      const list = parseRss(xml);
      if (cache) { try { fs.writeFileSync(cache, JSON.stringify(list)); } catch { /* cache facultatif */ } }
      return list;
    } catch { /* hors ligne : cache */ }
  }
  if (cache) { try { return JSON.parse(fs.readFileSync(cache, 'utf8')); } catch { /* pas de cache */ } }
  return null;
}

module.exports = { fetchNews, parseRss };
