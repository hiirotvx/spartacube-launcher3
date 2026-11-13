'use strict';
// Éditeur de skin pixel par pixel (texture 64×64) avec annuler/rétablir,
// pinceau, gomme, remplissage, pipette, grille et repères des zones du corps.
(function () {
  const S = 8; // 1 pixel de skin = 8 pixels à l'écran (512 / 64)

  // Repères des zones (couche de base / couche extérieure)
  const ZONES = [
    { r: [0, 0, 32, 16], n: 'Tête', o: false }, { r: [32, 0, 32, 16], n: 'Chapeau', o: true },
    { r: [0, 16, 16, 16], n: 'Jambe D', o: false }, { r: [16, 16, 24, 16], n: 'Torse', o: false }, { r: [40, 16, 16, 16], n: 'Bras D', o: false },
    { r: [0, 32, 16, 16], n: 'Jambe D+', o: true }, { r: [16, 32, 24, 16], n: 'Veste', o: true }, { r: [40, 32, 16, 16], n: 'Bras D+', o: true },
    { r: [0, 48, 16, 16], n: 'Jambe G+', o: true }, { r: [16, 48, 16, 16], n: 'Jambe G', o: false },
    { r: [32, 48, 16, 16], n: 'Bras G', o: false }, { r: [48, 48, 16, 16], n: 'Bras G+', o: true }
  ];

  function hexToRgba(hex) {
    const n = parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 255];
  }
  function rgbaToHex(r, g, b) {
    return `#${[r, g, b].map(v => v.toString(16).padStart(2, '0')).join('')}`;
  }

  class SkinEditor {
    constructor(canvas, { onChange = () => {}, onPick = () => {} } = {}) {
      this.view = canvas;
      this.vctx = canvas.getContext('2d');
      this.tex = document.createElement('canvas');
      this.tex.width = 64; this.tex.height = 64;
      this.ctx = this.tex.getContext('2d', { willReadFrequently: true });
      this.tool = 'pencil';
      this.color = '#c4122f';
      this.showGrid = false;
      this.showZones = true;
      this.undoStack = [];
      this.redoStack = [];
      this.onChange = onChange;
      this.onPick = onPick;
      this.drawing = false;
      this.last = null;
      this.bind();
      this.newTemplate();
      this.undoStack = [];
    }

    /* ---------- Historique ---------- */
    snapshot() {
      this.undoStack.push(this.ctx.getImageData(0, 0, 64, 64));
      if (this.undoStack.length > 80) this.undoStack.shift();
      this.redoStack = [];
    }
    undo() {
      if (!this.undoStack.length) return;
      this.redoStack.push(this.ctx.getImageData(0, 0, 64, 64));
      this.ctx.putImageData(this.undoStack.pop(), 0, 0);
      this.changed();
    }
    redo() {
      if (!this.redoStack.length) return;
      this.undoStack.push(this.ctx.getImageData(0, 0, 64, 64));
      this.ctx.putImageData(this.redoStack.pop(), 0, 0);
      this.changed();
    }

    /* ---------- Rendu ---------- */
    changed() { this.render(); this.onChange(this.tex); }

    render() {
      const c = this.vctx;
      c.imageSmoothingEnabled = false;
      for (let y = 0; y < 64; y += 2) {
        for (let x = 0; x < 64; x += 2) {
          c.fillStyle = ((x + y) / 2) % 2 ? '#2b1d27' : '#211520';
          c.fillRect(x * S, y * S, S * 2, S * 2);
        }
      }
      c.drawImage(this.tex, 0, 0, 512, 512);
      if (this.showGrid) {
        c.strokeStyle = 'rgba(255,255,255,.08)';
        c.lineWidth = 1;
        c.beginPath();
        for (let i = 0; i <= 64; i++) {
          c.moveTo(i * S + .5, 0); c.lineTo(i * S + .5, 512);
          c.moveTo(0, i * S + .5); c.lineTo(512, i * S + .5);
        }
        c.stroke();
      }
      if (this.showZones) {
        c.font = '10px Segoe UI, sans-serif';
        for (const z of ZONES) {
          const [x, y, w, h] = z.r;
          c.strokeStyle = z.o ? 'rgba(96,165,250,.55)' : 'rgba(255,176,46,.6)';
          c.setLineDash(z.o ? [4, 3] : []);
          c.strokeRect(x * S + .5, y * S + .5, w * S - 1, h * S - 1);
          c.fillStyle = z.o ? 'rgba(147,197,253,.85)' : 'rgba(255,210,150,.85)';
          c.fillText(z.n, x * S + 3, y * S + 11);
        }
        c.setLineDash([]);
      }
    }

    /* ---------- Outils ---------- */
    pos(ev) {
      const r = this.view.getBoundingClientRect();
      return [Math.floor(((ev.clientX - r.left) / r.width) * 64), Math.floor(((ev.clientY - r.top) / r.height) * 64)];
    }

    plot(x, y) {
      if (x < 0 || y < 0 || x > 63 || y > 63) return;
      if (this.tool === 'eraser') this.ctx.clearRect(x, y, 1, 1);
      else { this.ctx.fillStyle = this.color; this.ctx.fillRect(x, y, 1, 1); }
    }

    line(x0, y0, x1, y1) {
      const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
      const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
      let err = dx + dy;
      for (;;) {
        this.plot(x0, y0);
        if (x0 === x1 && y0 === y1) break;
        const e2 = 2 * err;
        if (e2 >= dy) { err += dy; x0 += sx; }
        if (e2 <= dx) { err += dx; y0 += sy; }
      }
    }

    fill(x, y) {
      const img = this.ctx.getImageData(0, 0, 64, 64);
      const d = img.data;
      const i0 = (y * 64 + x) * 4;
      const target = [d[i0], d[i0 + 1], d[i0 + 2], d[i0 + 3]];
      const repl = hexToRgba(this.color);
      if (target.every((v, k) => v === repl[k])) return;
      const stack = [[x, y]];
      while (stack.length) {
        const [cx, cy] = stack.pop();
        if (cx < 0 || cy < 0 || cx > 63 || cy > 63) continue;
        const i = (cy * 64 + cx) * 4;
        if (d[i] !== target[0] || d[i + 1] !== target[1] || d[i + 2] !== target[2] || d[i + 3] !== target[3]) continue;
        d[i] = repl[0]; d[i + 1] = repl[1]; d[i + 2] = repl[2]; d[i + 3] = repl[3];
        stack.push([cx + 1, cy], [cx - 1, cy], [cx, cy + 1], [cx, cy - 1]);
      }
      this.ctx.putImageData(img, 0, 0);
    }

    bind() {
      const v = this.view;
      v.addEventListener('contextmenu', e => e.preventDefault());
      v.addEventListener('mousedown', e => {
        const [x, y] = this.pos(e);
        const pick = this.tool === 'picker' || e.button === 2 || e.altKey;
        if (pick) {
          const [r, g, b, a] = this.ctx.getImageData(x, y, 1, 1).data;
          if (a > 0) { this.color = rgbaToHex(r, g, b); this.onPick(this.color); }
          return;
        }
        this.snapshot();
        if (this.tool === 'fill') { this.fill(x, y); this.changed(); return; }
        this.drawing = true;
        this.last = [x, y];
        this.plot(x, y);
        this.changed();
      });
      window.addEventListener('mousemove', e => {
        if (!this.drawing) return;
        const [x, y] = this.pos(e);
        this.line(this.last[0], this.last[1], x, y);
        this.last = [x, y];
        this.changed();
      });
      window.addEventListener('mouseup', () => { this.drawing = false; });
    }

    /* ---------- Chargement / export ---------- */
    load(dataUrl) {
      return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => {
          if (img.width !== 64 || (img.height !== 64 && img.height !== 32)) {
            reject(new Error(`Un skin doit faire 64×64 ou 64×32 pixels (image : ${img.width}×${img.height})`));
            return;
          }
          this.snapshot();
          this.ctx.clearRect(0, 0, 64, 64);
          this.ctx.drawImage(img, 0, 0);
          if (img.height === 32) this.upgradeLegacy();
          this.changed();
          resolve();
        };
        img.onerror = () => reject(new Error('Image illisible'));
        img.src = dataUrl;
      });
    }

    /** Ancien format 64×32 → 64×64 : la jambe et le bras gauches copient les droits (en miroir). */
    upgradeLegacy() {
      const mirror = (sx, sy, dx, dy) => {
        this.ctx.save();
        this.ctx.translate(dx + 16, dy);
        this.ctx.scale(-1, 1);
        this.ctx.drawImage(this.tex, sx, sy, 16, 16, 0, 0, 16, 16);
        this.ctx.restore();
      };
      mirror(0, 16, 16, 48);
      mirror(40, 16, 32, 48);
    }

    /** Skin de départ original aux couleurs SpartaCube. */
    newTemplate() {
      this.snapshot();
      const c = this.ctx;
      c.clearRect(0, 0, 64, 64);
      const box = (u, v, w, h, d, side, top = side, bottom = side) => {
        c.fillStyle = top; c.fillRect(u + d, v, w, d);
        c.fillStyle = bottom; c.fillRect(u + d + w, v, w, d);
        c.fillStyle = side; c.fillRect(u, v + d, 2 * (w + d), h);
      };
      const skin = '#d9a07a', hair = '#5b3a24', tunic = '#b3122c', pants = '#3b2630', gold = '#e2a52b';
      box(0, 0, 8, 8, 8, skin, hair, skin);        // tête
      c.fillStyle = hair; c.fillRect(0, 8, 32, 2);  // cheveux sur les côtés
      box(16, 16, 8, 12, 4, tunic, tunic, pants);  // torse
      box(40, 16, 4, 12, 4, skin);                  // bras droit
      box(32, 48, 4, 12, 4, skin);                  // bras gauche
      box(0, 16, 4, 12, 4, pants);                  // jambe droite
      box(16, 48, 4, 12, 4, pants);                 // jambe gauche
      c.fillStyle = gold; c.fillRect(16, 27, 24, 1); // ceinture
      c.fillStyle = tunic; c.fillRect(40, 20, 16, 3); c.fillRect(32, 52, 16, 3); // manches
      // visage
      c.fillStyle = '#ffffff'; c.fillRect(9, 12, 1, 1); c.fillRect(14, 12, 1, 1);
      c.fillStyle = '#2d1c12'; c.fillRect(10, 12, 1, 1); c.fillRect(13, 12, 1, 1);
      c.fillStyle = '#9c5a3c'; c.fillRect(11, 14, 2, 1);
      // casque spartiate (couche extérieure)
      c.fillStyle = '#9aa0a6'; c.fillRect(40, 0, 8, 8); c.fillRect(32, 8, 32, 3);
      c.fillStyle = '#d32f2f'; c.fillRect(43, 0, 2, 8);
      this.changed();
    }

    toDataURL() { return this.tex.toDataURL('image/png'); }
  }

  window.SkinEditor = SkinEditor;
})();
