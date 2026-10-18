
// ---------- building helpers: every diorama lives in its own group and is shown one at a time ----------
function inGroup(g, fn) { const prev = INST_PARENT; INST_PARENT = g; try { fn(); } finally { INST_PARENT = prev; } }
function treesInto(g) { inGroup(g, () => flushScenery()); LOGS.length = 0; LEAVES.length = 0; ROCKS.length = 0; }
const GROUPS = [];
function newGroup() { const g = new THREE.Group(); g.visible = false; world.add(g); GROUPS.push(g); return g; }
const CANVAS_SIGN = (text, bg, fg, w, h) => canvasTex(w || 128, h || 32, (g, W, H) => { noise(g, 0, 0, W, H, bg, 0.06, hashStr(text) % 997); g.fillStyle = fg; g.textAlign = 'center'; g.textBaseline = 'middle'; fitFont(g, text, W - 12, Math.floor(H * 0.62)); g.fillText(text, W / 2, H / 2 + 1); }, true);
// signs inside the scenes can carry a second language; setSceneLang swaps every registered texture
const SCENE_TEXT = [];
function setSceneLang(l) { for (const [m, t] of SCENE_TEXT) { const x = t[l] || t.es; if (m.map !== x) { m.map = x; if (m.emissiveMap) m.emissiveMap = x; m.needsUpdate = true; } } }
function label(parent, text, bg, fg, pos, rotY, w, h, glowK) {
  const texts = typeof text === 'string' ? { es: text } : text, tex = CANVAS_SIGN(texts.es, bg, fg, 128, 32);
  const m = glowK ? cel({ map: tex, emissiveMap: tex, emissive: new THREE.Color(1, 1, 1).multiplyScalar(glowK * GLOW_K), side: THREE.DoubleSide }) : cel({ map: tex, side: THREE.DoubleSide });
  if (texts.en) SCENE_TEXT.push([m, { es: tex, en: CANVAS_SIGN(texts.en, bg, fg, 128, 32) }]);
  const p = new THREE.Mesh(new THREE.PlaneGeometry(w, h), m); p.position.copy(pos); p.rotation.y = rotY || 0; parent.add(p); return p;
}
Object.assign(T, {
  sand: tile(g => noise(g, 0, 0, 16, 16, 0xe3c88c, 0.18, 201)),
  sandSide: tile(g => { noise(g, 0, 0, 16, 16, 0xd2ae6e, 0.2, 202); noise(g, 0, 0, 16, 3, 0xe3c88c, 0.18, 203); }),
  sandstone: tile(g => { noise(g, 0, 0, 16, 16, 0xd8b878, 0.14, 204); g.fillStyle = '#c4a060'; [4, 9, 13].forEach(y => g.fillRect(0, y, 16, 1)); }),
  terra: tile(g => { for (let y = 0; y < 16; y++) noise(g, 0, y, 16, 1, [0xb8643a, 0xc87848, 0xa85a34, 0xd08a58][Math.floor(y / 4)], 0.12, 205 + y); }),
  iron: tile(g => noise(g, 0, 0, 16, 16, 0x4a4a54, 0.15, 240)),
  water: tile(g => { noise(g, 0, 0, 16, 16, 0x2f7fd0, 0.16, 300); g.fillStyle = 'rgba(255,255,255,.55)'; [[2, 3, 3], [9, 6, 4], [4, 11, 3], [11, 13, 3]].forEach(([x, y, w]) => g.fillRect(x, y, w, 1)); }),
  ice: tile(g => { noise(g, 0, 0, 16, 16, 0xcfeeff, 0.12, 301); g.fillStyle = '#a8dcf6'; [[3, 4], [10, 2], [6, 11], [13, 9]].forEach(([x, y]) => g.fillRect(x, y, 2, 1)); }),
  snowTop: tile(g => noise(g, 0, 0, 16, 16, 0xf6fbff, 0.06, 302)),
  floorN: tile(g => { noise(g, 0, 0, 16, 16, 0x172456, 0.12, 303); g.fillStyle = '#25356e'; g.fillRect(0, 0, 16, 1); g.fillRect(0, 0, 1, 16); }),
  floorGlow: tile(g => { g.fillStyle = '#000000'; g.fillRect(0, 0, 16, 16); g.fillStyle = '#ff6a1f'; g.fillRect(0, 0, 16, 1); g.fillRect(0, 0, 1, 16); }),
  panel: tile(g => { noise(g, 0, 0, 16, 16, 0x13204a, 0.08, 304); g.fillStyle = '#0c1636'; [0, 8].forEach(x => g.fillRect(x, 0, 1, 16)); g.fillRect(0, 15, 16, 1); }),
  concrete: tile(g => { noise(g, 0, 0, 16, 16, 0xbfc2c8, 0.08, 305); g.fillStyle = '#a9adb5'; g.fillRect(0, 0, 16, 1); g.fillRect(0, 0, 1, 16); }),
  solar: tile(g => { g.fillStyle = '#1b3f8f'; g.fillRect(0, 0, 16, 16); g.fillStyle = '#5a86d8'; for (let i = 0; i < 16; i += 4) { g.fillRect(i, 0, 1, 16); g.fillRect(0, i, 16, 1); } }),
  earth: canvasTex(64, 32, g => { noise(g, 0, 0, 64, 32, 0x2a6fd0, 0.12, 306); const r = rng(307); for (let k = 0; k < 9; k++) { const x = Math.floor(r() * 56), y = 4 + Math.floor(r() * 20), w = 4 + Math.floor(r() * 9), h = 2 + Math.floor(r() * 6); noise(g, x, y, w, h, r() < 0.6 ? 0x4c9a3a : 0xc9a865, 0.15, 308 + k); } g.fillStyle = 'rgba(255,255,255,.75)'; for (let k = 0; k < 14; k++) g.fillRect(Math.floor(r() * 62), Math.floor(r() * 30), 2 + Math.floor(r() * 4), 1); }, true)
});
const SAND_MATS = { top: [tmat('sandSide'), tmat('sandSide'), tmat('sand'), tmat('sandstone'), tmat('sandSide'), tmat('sandSide')], mid: tmat('sandstone'), low: tmat('terra') };
const STONE_MATS = { top: [tmat('stone'), tmat('stone'), tmat('cobble'), tmat('stone'), tmat('stone'), tmat('stone')], mid: tmat('stone'), low: tmat('stone') };
const CONC_MATS = { top: [tmat('stone'), tmat('stone'), tmat('concrete'), tmat('stone'), tmat('stone'), tmat('stone')], mid: tmat('stone'), low: tmat('stone') };
(function moreSkies() {
  const cv = document.createElement('canvas'); cv.width = 4; cv.height = 256;
  const g = cv.getContext('2d'), gr = g.createLinearGradient(0, 0, 0, 256);
  gr.addColorStop(0, '#2c2a6a'); gr.addColorStop(0.5, '#c25a6a'); gr.addColorStop(0.8, '#ff9a4a'); gr.addColorStop(1, '#ffd08a');
  g.fillStyle = gr; g.fillRect(0, 0, 4, 256);
  SKY.dusk = new THREE.CanvasTexture(cv); SKY.dusk.encoding = THREE.sRGBEncoding;
  const sv = document.createElement('canvas'); sv.width = 4; sv.height = 4; const s = sv.getContext('2d'); s.fillStyle = '#0a1230'; s.fillRect(0, 0, 4, 4);
  SKY.studio = new THREE.CanvasTexture(sv); SKY.studio.encoding = THREE.sRGBEncoding;
})();

// ---------- the channel logo: planet Earth as a cube, one pixel-art face per side ----------
const EARTH_FACES = {
  front: ['..wwwwww...www..', '.gggggggg..ww...', 'ggggggggggg.....', '.gggggggggg.....', '..gggggggg......', '...ggggsgg......', '....gggsg.......', '.....gggg..c....', '......gg...cc...', '.......gggg.....', '......gggggg....', '......ggggsgg...', '.......gggsgg...', '........gggg....', '........ggg.....', '.........g......'],
  right: ['....wwwwwww.....', '.....gggg.gg....', '...gggggggggg...', '....ggg.gggggg..', '......c..gggggg.', '....ssssssss....', '...ssssssssss...', '...sssssssssg...', '....ssssgggg....', '.....gggggggg...', '......gggggg....', '......ggggg.....', '.......gggg..g..', '.......ggg...g..', '........g.......', '................'],
  back: ['wwwwwwwwwwwww...', 'gggggggggggggg..', 'ggggggggggggggg.', 'gggggsssggggggg.', 'ggggssssssgggg..', 'gggsssssgggggg.g', 'ggggsgggggggg..g', 'gggg.gggg.ggg...', 'ggg...ggg..gg...', '.gg....g....g...', '............gg..', '..c.........ggg.', '.cc.............', '................', '.........c......', '................'],
  left: ['wwww.......wwwww', 'gg............gg', 'g...........c..g', '....c...........', '...cc...........', '................', '........g.......', '........gg......', '................', '....sssss.......', '...sssssssg.....', '...ssssssgg.....', '....ssssgg......', '.....g.....g....', '...........gg...', '................'],
  top: ['................', '..gggg....gggg..', '.gggwwwwwwwwggg.', '.ggwwwwwwwwwwgg.', '..wwwwwwwwwwww..', '..wwwwwwwwwwww..', '.gwwwwwwwwwwwwg.', '.gwwwwwwwwwwwwg.', '.gwwwwwwwwwwwwg.', '.gwwwwwwwwwwwwg.', '..wwwwwwwwwwww..', '..wwwwwwwwwwww..', '.ggwwwwwwwwwwgg.', '.gggwwwwwwwwggg.', '..gggg....gggg..', '................'],
  bottom: ['................', '................', '...wwwwwwwwww...', '..wwwwwwwwwwww..', '..wwwwwwwwwwww..', '.wwwwwwwwwwwwww.', '.wwwwwwwwwwwwww.', '.wwwwwwwwwwwwww.', '.wwwwwwwwwwwwww.', '.wwwwwwwwwwwwww.', '..wwwwwwwwwwww..', '..wwwwwwwwwwww..', '...wwwwwwwwww...', '................', '................', '................']
};
const EARTH_COL = { '.': 0x2a74d8, g: 0x4ca83e, s: 0xd9b46a, w: 0xf2f8ff, c: 0xffffff };
const EARTH_CANVAS = {};
for (const k in EARTH_FACES) {
  const cv = document.createElement('canvas'); cv.width = cv.height = 16; const g = cv.getContext('2d'), r = rng(hashStr(k));
  EARTH_FACES[k].forEach((row, y) => [...row].forEach((ch, x) => { g.fillStyle = shade(EARTH_COL[ch], 1 + (r() - 0.5) * (ch === '.' ? 0.14 : 0.18)); g.fillRect(x, y, 1, 1); }));
  EARTH_CANVAS[k] = cv;
}
// an isometric icon of the cube for the on-screen bug, the page header and the desk sign
function earthIcon(size) {
  const cv = document.createElement('canvas'); cv.width = cv.height = size; const g = cv.getContext('2d'); g.imageSmoothingEnabled = false;
  const S = size / 64, face = (img, ox, oy, ux, uy, vx, vy, dark) => {
    g.setTransform(ux * S / 16, uy * S / 16, vx * S / 16, vy * S / 16, ox * S, oy * S); g.drawImage(img, 0, 0);
    if (dark) { g.fillStyle = 'rgba(8,16,48,' + dark + ')'; g.fillRect(0, 0, 16, 16); }
  };
  face(EARTH_CANVAS.top, 32, 4, 28, 14, -28, 14, 0);
  face(EARTH_CANVAS.front, 4, 18, 28, 14, 0, 30, 0.12);
  face(EARTH_CANVAS.right, 32, 32, 28, -14, 0, 30, 0.3);
  g.setTransform(1, 0, 0, 1, 0, 0); return cv;
}
const EARTH_ICON = earthIcon(64), EARTH_ICON_URL = EARTH_ICON.toDataURL();
document.querySelectorAll('[data-earth-icon]').forEach(e => { e.style.background = 'url(' + EARTH_ICON_URL + ') center / contain no-repeat'; e.style.boxShadow = 'none'; e.style.imageRendering = 'pixelated'; });

// ---------- poses for the show ----------
Object.assign(ANIMS, {
  point(P, t) { ANIMS.idle(P, t); P.aRX = -1.45 + Math.sin(t * 5) * 0.06; P.eR = 0.08; P.aRZ = -0.08; P.torsoRY = -0.12; P.headRX = -0.05; },
  present(P, t) { ANIMS.talk(P, t); P.aRX = -0.6; P.aRZ = -0.55 + Math.sin(t * 2.2) * 0.08; P.eR = 0.5; },
  march(P, t) { ANIMS.walk(P, t * 0.75); P.aLX = P.aRX = -1.95 + Math.sin(t * 5.2) * 0.04; P.eL = P.eR = 0.45; P.aLZ = 0.05; P.aRZ = -0.05; },
  vote(P, t) { ANIMS.idle(P, t); P.aRX = -0.95 + Math.abs(Math.sin(t * 4)) * 0.12; P.eR = 0.75; P.headRX = 0.32; P.torsoRX = 0.08; },
  // livelier gestures for the host; each Short cuts between them at every pause in the narration
  hype(P, t) { ANIMS.idle(P, t); const b = Math.abs(Math.sin(t * 5.2)); P.bodyY = b * 0.035; P.kL = P.kR = (1 - b) * 0.12; P.headRX = -0.06 + Math.sin(t * 5.2) * 0.05; P.headRZ = Math.sin(t * 2.3) * 0.08; P.aLX = -0.5 + Math.sin(t * 5.2) * 0.35; P.aRX = -0.5 + Math.sin(t * 5.2 + 1.2) * 0.35; P.eL = P.eR = 0.9; P.aLZ = 0.3; P.aRZ = -0.3; P.torsoRY = Math.sin(t * 2.6) * 0.1; },
  flap(P, t) { ANIMS.idle(P, t); const f = Math.sin(t * 14); P.aLZ = 0.95 + f * 0.35; P.aRZ = -0.95 - f * 0.35; P.eL = P.eR = 0.1; P.bodyY = Math.abs(Math.sin(t * 7)) * 0.03; P.headRX = -0.08; P.headRZ = Math.sin(t * 3.5) * 0.06; },
  shrug(P, t) { ANIMS.idle(P, t); const s = Math.sin(t * 3) * 0.06; P.aLX = -0.55; P.aRX = -0.55; P.aLZ = 0.75 + s; P.aRZ = -0.75 - s; P.eL = P.eR = 1.3; P.headRZ = 0.18; P.headRX = 0.05; P.torsoRZ = 0.04; },
  wow(P, t) { ANIMS.idle(P, t); P.aLZ = 2.5 + Math.sin(t * 8) * 0.12; P.aRZ = -2.5 - Math.sin(t * 8 + 1) * 0.12; P.eL = P.eR = 0.2; P.headRX = -0.12; P.bodyY = Math.abs(Math.sin(t * 6)) * 0.03; },
  think(P, t) { ANIMS.idle(P, t); P.aRX = -1.2; P.eR = 2.0; P.aRZ = 0.25; P.aLX = -0.4; P.eL = 1.1; P.aLZ = -0.15; P.headRZ = -0.12 + Math.sin(t * 1.5) * 0.04; P.headRX = 0.06; P.torsoRY = -0.1; },
  lean(P, t) { ANIMS.idle(P, t); P.torsoRX = 0.22; P.headRX = -0.15 + Math.sin(t * 4.6) * 0.04; P.aRX = -1.5 + Math.sin(t * 5) * 0.08; P.eR = 0.1; P.aRZ = -0.05; P.aLX = -0.2; P.eL = 0.6; P.bodyY = -0.01; },
  count(P, t) { ANIMS.idle(P, t); P.aRX = -2.4 + Math.sin(t * 4) * 0.1; P.eR = 0.3; P.aRZ = -0.2; P.aLX = -0.3; P.eL = 0.9; P.aLZ = 0.2; P.headRZ = -0.06 + Math.sin(t * 2) * 0.04; },
});

// ---------- the anchor and a pool of extras ----------
// ---------- the host: Mr. News, a penguin in an orange plaid suit and a reporter's hat with a press card ----------
const PRESS_TEX = canvasTex(32, 20, (c, w, h) => { c.fillStyle = '#ffffff'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff6a1f'; c.fillRect(0, 0, w, 6); c.fillStyle = '#13204a'; c.textAlign = 'center'; c.textBaseline = 'middle'; fitFont(c, 'NEWS', w - 4, 9); c.fillText('NEWS', w / 2, 13); }, true);
const plaid = (g, q, dark, gold) => {
  // a loud plaid: darker bands every four pixels and a thin gold thread
  for (let y = 0; y < q[3]; y++) for (let x = 0; x < q[2]; x++) {
    const hb = y % 4 === 1, vb = x % 4 === 1;
    if (hb || vb) { g.fillStyle = hb && vb ? dark : 'rgba(150,40,0,.28)'; g.fillRect(q[0] + x, q[1] + y, 1, 1); }
    if (y % 8 === 3 && x % 2 === 0) { g.fillStyle = gold; g.fillRect(q[0] + x, q[1] + y, 1, 1); }
  }
};
// the suit every mascot wears
const SUIT = {
  paintBody: (g, B) => {
    for (const k of ['front', 'back', 'left', 'right']) plaid(g, B[k], 'rgba(120,30,0,.45)', '#ffd23f');
    const f = B.front, P = (x, y, col) => { g.fillStyle = col; g.fillRect(f[0] + x, f[1] + y, 1, 1); };
    for (let y = 1; y < 6; y++) { P(3, y, '#f6f4ee'); P(4, y, '#f6f4ee'); }
    for (let y = 1; y < 6; y++) { P(2, y, '#ffd23f'); P(5, y, '#ffd23f'); }
    [2, 3, 4, 5].forEach(x => P(x, 0, '#13204a')); P(3, 1, '#13204a'); P(4, 1, '#13204a'); P(3, 0, '#ffd23f');
    P(1, 2, '#2a74d8'); P(1, 3, '#4ca83e'); P(0, 2, '#2a74d8');
    for (let y = 6; y < 12; y++) P(4, y, 'rgba(90,20,0,.6)');
    P(3, 7, '#ffd23f'); P(3, 9, '#ffd23f');
  },
  paintArm: (g, N) => { for (const k of ['front', 'back', 'left', 'right']) { const q = N[k]; plaid(g, [q[0], q[1], q[2], 9], 'rgba(120,30,0,.45)', '#ffd23f'); g.fillStyle = '#f6f4ee'; g.fillRect(q[0], q[1] + 9, q[2], 1); } }
};
// mascots are built from a spec: head colours and pattern, a snout with a jaw that talks, extras and a tail
HAIR.fur = (g, H, O, d) => { for (const k in H) noise(g, ...H[k], d.fur, 0.1, 520); if (d.headPaint) d.headPaint(g, H, d); };
const PXB = (x, y, z) => [x * PX, y * PX, z * PX];
function mascotDef(m) {
  return Object.assign({ style: 'fur', fur: m.fur, skin: m.skin, headPaint: m.headPaint, bigEyes: true, headScale: 1.2, hairHex: m.dark, browHex: m.brow || m.dark, eye: m.eye || '#15121f', mouthPx: m.mouth || '#5b1f2a', expr: 'feliz',
    shirt: 0xff6a1f, sleeve: 10, sleeveHex: 0xff6a1f, hand: m.paw || m.fur, leg: m.legLow || m.fur, legTop: 0x1d2d63, legTopTo: 5, shoe: m.foot || m.dark, sole: m.foot || m.dark, shoeFrom: 10,
    paintBody: SUIT.paintBody, paintArm: SUIT.paintArm,
    extra: c => {
      const hc = c.p.hc, B = makeBatch();
      m.build(B, c);
      if (m.snout) {
        // a snout with a nose on top and a lower jaw that drops while the mascot talks
        const s = m.snout; B.box(s.w * PX, s.h * PX, s.d * PX, mat(s.color), PXB(0, s.y, 4 + s.d / 2));
        if (s.nose) B.box(s.nose[0] * PX, s.nose[1] * PX, 0.8 * PX, mat(s.noseColor), PXB(0, s.y + s.h / 2 - s.nose[1] / 2, 4 + s.d + 0.3));
        const jaw = new THREE.Group(); jaw.position.set(0, (s.y - s.h / 2) * PX, 4.2 * PX); hc.add(jaw);
        box((s.w - 0.6) * PX, 0.9 * PX, (s.d - 0.4) * PX, mat(s.jaw || s.color), jaw, PXB(0, -0.35, (s.d - 0.4) / 2));
        c.jaw = jaw;
      }
      B.flush(hc, { cast: false });
      if (m.tail) { const TB = makeBatch(); m.tail(TB); TB.flush(c.p.torso); }
      c.jawOpen = 0;
    },
    tick: (c, now) => { if (c.eyes) drawEyes(c, now); if (!c.jaw) return; const target = c.mo ? 0.5 : c.expr === 'sorpresa' ? 0.3 : 0; c.jawOpen += (target - c.jawOpen) * 0.45; c.jaw.rotation.x = c.jawOpen; }
  }, m.over || {});
}
// ---------- big shiny anime eyes: a finer pixel decal in front of the blocky face ----------
// 8 texels per head pixel, so the eyes get lids, a two-tone iris and highlights while staying pixel art
const EYE_W = 48, EYE_H = 30, EYE_COL = { blush: '#ff8fae', blushHi: '#ffc2d2', lid: '#141a2e', white: '#ffffff', irisTop: '#123a78', irisMid: '#1f86d6', irisLow: '#7fe3ff', pupil: '#0a0f1f' };
function chibiEyes(c, y0, y1) {
  const cv = document.createElement('canvas'); cv.width = EYE_W; cv.height = EYE_H;
  const tex = new THREE.CanvasTexture(cv); tex.encoding = THREE.sRGBEncoding; tex.magFilter = THREE.NearestFilter; tex.minFilter = THREE.LinearFilter; tex.generateMipmaps = false;
  const w = 6 * PX, h = EYE_H / 8 * PX; y0 = y1 - EYE_H / 8;
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), cel({ map: tex, alphaTest: 0.5, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2 }));
  m.position.set(0, (y0 + y1) / 2 * PX, 4.04 * PX); c.p.hc.add(m);
  c.eyes = { g: cv.getContext('2d'), tex, key: '' };
}
// one eye in a 21x26 box; `side` is -1 for the left eye (its outer corner is on the left), 1 for the right
function eyeShape(g, ox, side, state) {
  const W = 21, H = 26, px = (x, y, col) => { const X = side < 0 ? x : W - 1 - x; g.fillStyle = col; g.fillRect(ox + X, y, 1, 1); };
  const inE = (x, y, cx, cy, rx, ry) => ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1;
  const arc = (base, bend, thick) => { for (let x = 1; x < W - 1; x++) { const u = (x - 10) / 9.5, y = Math.round(base + bend * (1 - u * u)); for (let t = 0; t < thick; t++) px(x, y + t, EYE_COL.lid); } px(0, Math.round(base) - 1, EYE_COL.lid); px(0, Math.round(base) - 2, EYE_COL.lid); };
  if (state === 'blink') { arc(15, 3, 2); return; }
  if (state === 'happy') { arc(17, -6, 3); return; }
  const wide = state === 'sorpresa', cx = 10.5, cy = 14.2, rx = wide ? 10 : 9.4, ry = wide ? 12.3 : 11.4;
  const irx = wide ? 4.6 : 7.4, iry = wide ? 5.6 : 9.6, icy = wide ? 14.5 : 15.6;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (!inE(x, y, cx, cy, rx + 1.1, ry + 1.1)) continue;
    const inside = inE(x, y, cx, cy, rx, ry);
    // a thick upper lid and a thin line round the rest
    let col = !inside || y < (wide ? 4 : 5) ? EYE_COL.lid : EYE_COL.white;
    if (state === 'enojado' && y < 3 + 0.5 * x) col = EYE_COL.lid;
    if (state === 'triste' && y < 3 + 0.5 * (W - 1 - x)) col = EYE_COL.lid;
    if (col === EYE_COL.white && inE(x, y, cx, icy, irx, iry)) {
      const k = (y - (icy - iry)) / (2 * iry);
      col = k < 0.38 ? EYE_COL.irisTop : k < 0.72 ? EYE_COL.irisMid : EYE_COL.irisLow;
      if (inE(x, y, cx, icy - (wide ? 0 : 0.6), wide ? 1.8 : 3.4, wide ? 2.2 : 4.4)) col = EYE_COL.pupil;
    }
    px(x, y, col);
  }
  // the outer lashes flick up and out
  px(0, 4, EYE_COL.lid); px(0, 3, EYE_COL.lid); px(1, 3, EYE_COL.lid); px(0, 2, EYE_COL.lid);
  px(1, 19, EYE_COL.lid); px(2, 21, EYE_COL.lid);
  // highlights stay on the same side in both eyes, as if lit from one window
  const hl = (x, y) => { g.fillStyle = EYE_COL.white; g.fillRect(ox + x, y, 1, 1); };
  const hx = wide ? 8 : 6, hy = wide ? 10 : 9;
  for (let y = 0; y < 5; y++) for (let x = 0; x < 4; x++) if (((x - 1.5) / 2) ** 2 + ((y - 2) / 2.5) ** 2 <= 1) hl(hx + x, hy + y);
  hl(wide ? 12 : 14, wide ? 17 : 20); hl(wide ? 13 : 15, wide ? 17 : 20); hl(wide ? 12 : 14, wide ? 18 : 21);
}
// the square version: same lids, iris and shine, built from boxes like everything else on the show
function eyeShapeSquare(g, ox, side, state) {
  const W = 21, lid = EYE_COL.lid, px = (x, y, col) => { const X = side < 0 ? x : W - 1 - x; g.fillStyle = col; g.fillRect(ox + X, y, 1, 1); };
  const inR = (x, y, x0, y0, x1, y1, r) => { if (x < x0 || x > x1 || y < y0 || y > y1) return false; const dx = Math.max(x0 + r - x, 0, x - (x1 - r)), dy = Math.max(y0 + r - y, 0, y - (y1 - r)); return dx * dx + dy * dy <= r * r + 0.5; };
  // closed eyes are straight strokes: a flat line for a blink, a chevron for a happy squint
  if (state === 'blink') { for (let x = 2; x <= 18; x++) { px(x, 16, lid); px(x, 17, lid); } px(1, 15, lid); px(0, 14, lid); return; }
  if (state === 'happy') { for (let x = 2; x <= 18; x++) { const y = 19 - Math.round(7 * (1 - Math.abs(x - 10) / 8)); px(x, y, lid); px(x, y + 1, lid); px(x, y + 2, lid); } px(1, 19, lid); px(0, 18, lid); return; }
  const wide = state === 'sorpresa';
  const [x0, y0, x1, y1] = wide ? [0, 3, 20, 23] : [1, 5, 19, 23];
  const [ix0, iy0, ix1, iy1] = wide ? [7, 9, 13, 15] : [4, 7, 16, 21];
  const [qx0, qy0, qx1, qy1] = wide ? [9, 11, 11, 13] : [8, 10, 12, 16];
  for (let y = y0 - 1; y <= y1 + 1; y++) for (let x = Math.max(0, x0 - 1); x <= Math.min(W - 1, x1 + 1); x++) {
    if (!inR(x, y, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 2.5)) continue;
    const inside = inR(x, y, x0, y0, x1, y1, 1.5);
    let col = !inside || y < y0 + 2 ? lid : EYE_COL.white;
    if (state === 'enojado' && y < y0 + 0.5 * x) col = lid;
    if (state === 'triste' && y < y0 + 0.5 * (W - 1 - x)) col = lid;
    if (col === EYE_COL.white && x >= ix0 && x <= ix1 && y >= iy0 && y <= iy1) {
      const k = (y - iy0) / (iy1 - iy0 + 1);
      col = k < 0.38 ? EYE_COL.irisTop : k < 0.72 ? EYE_COL.irisMid : EYE_COL.irisLow;
      if (x >= qx0 && x <= qx1 && y >= qy0 && y <= qy1) col = EYE_COL.pupil;
    }
    px(x, y, col);
  }
  // square lashes stepping up and out, and a tick under the outer corner
  px(0, y0 + 1, lid); px(0, y0, lid); px(0, y0 - 1, lid); px(1, y0 - 2, lid); px(1, y1 - 1, lid);
  // square highlights, on the same side in both eyes
  const hl = (x, y, n) => { g.fillStyle = EYE_COL.white; g.fillRect(ox + x, y, n, n); };
  if (wide) { hl(8, 10, 2); hl(12, 13, 1); } else { hl(5, 8, 4); hl(13, 17, 2); }
}
// rosy anime cheeks under the outer corners, with little shine strokes
function blush(g, ox) {
  for (let y = 0; y < 4; y++) for (let x = 0; x < 9; x++) if (((x - 4) / 4.6) ** 2 + ((y - 1.5) / 2.1) ** 2 <= 1) { g.fillStyle = EYE_COL.blush; g.fillRect(ox + x, 26 + y, 1, 1); }
  g.fillStyle = EYE_COL.blushHi; [[2, 27], [3, 26], [5, 27], [6, 26]].forEach(([x, y]) => g.fillRect(ox + x, y + 1, 1, 1));
}
function drawEyes(c, now) {
  const blink = now < c.blinkUntil, ex = c.expr;
  const L = blink ? 'blink' : ex === 'feliz' ? 'happy' : ex, R = blink ? 'blink' : ex === 'feliz' || ex === 'guino' ? 'happy' : ex;
  const key = L + '/' + R; if (key === c.eyes.key) return; c.eyes.key = key;
  const g = c.eyes.g; g.clearRect(0, 0, EYE_W, EYE_H);
  const shape = c.d.eyeStyle === 'square' ? eyeShapeSquare : eyeShape;
  shape(g, 0, -1, L); shape(g, EYE_W - 21, 1, R);
  if (ex !== 'enojado' && ex !== 'triste') { blush(g, 0); blush(g, EYE_W - 9); }
  c.eyes.tex.needsUpdate = true;
}
const paintRows = (g, q, y0, y1, x0, x1, hex, seed) => noise(g, q[0] + x0, q[1] + y0, x1 - x0, y1 - y0, hex, 0.08, seed);
const PENGUIN = { id: 'pinguino', name: 'Pingüino', pitch: 'Ya viene de traje: el más formal del noticiero. Pico naranja a juego con el saco.',
  voice: { en: ['am_michael', 1.5], es: ['em_alex', 1.5] },
  fur: 0x111827, dark: 0x111827, skin: 0xf8fafc, eye: '#ffffff', paw: 0x111827, foot: 0xff8a1f, legLow: 0x111827,
  // big anime eyes on a decal, so the face texture skips its own eyes; the beak is the mouth
  over: { eyeDecal: true, eyeStyle: 'square', noMouth: true },
  headPaint: (g, H) => { const f = H.front; paintRows(g, f, 2, 8, 1, 7, 0xf8fafc, 571); paintRows(g, f, 1, 2, 2, 6, 0xf8fafc, 572); },
  snout: { w: 2.6, h: 1.4, d: 2.2, y: 1.8, color: 0xff9a1f, jaw: 0xf07a12 },
  build: (B, c) => {
    chibiEyes(c, 2.75, 6.0);
    // a cream reporter's fedora, tilted, with a navy band and the NEWS card tucked in it
    const hat = new THREE.Group(); hat.position.set(0, 8 * PX, 0); hat.rotation.set(-0.05, 0, -0.14); c.p.hc.add(hat);
    const HB = makeBatch(), felt = mat(0xeadfc6), band = mat(0x1d2d63);
    HB.box(11.5 * PX, 0.7 * PX, 11 * PX, felt, PXB(0, 0.4, 0.2)); HB.box(8 * PX, 3.6 * PX, 7.6 * PX, felt, PXB(0, 2.5, 0)); HB.box(7.2 * PX, 0.8 * PX, 6.6 * PX, felt, PXB(0, 4.6, 0));
    HB.box(2.4 * PX, 0.5 * PX, 5.4 * PX, mat(0xd6c8a8), PXB(0, 5.05, 0)); HB.box(8.2 * PX, 1 * PX, 7.8 * PX, band, PXB(0, 1.3, 0));
    HB.flush(hat, { cast: true });
    const card = new THREE.Mesh(new THREE.PlaneGeometry(4.2 * PX, 2.6 * PX), cel({ map: PRESS_TEX, side: THREE.DoubleSide }));
    card.position.set(3.3 * PX, 2.6 * PX, 2.6 * PX); card.rotation.set(-0.1, 0.55, 0.12); hat.add(card);
    // optional low-poly touches: faceted feather tufts poking out under the hat like anime hair, and a feather in the band
    const P = new THREE.Group(); c.p.hc.add(P);
    const dark = cel({ color: lin(0x111827), flatShading: true }), sheen = cel({ color: lin(0x1d2d63), flatShading: true });
    const spike = (x, y, z, dx, dy, dz, len, r, m) => { const d = V(dx, dy, dz).normalize(), s = new THREE.Mesh(new THREE.ConeGeometry(r * PX, len * PX, 4), m); s.quaternion.setFromUnitVectors(V(0, 1, 0), d); s.position.set(x * PX, y * PX, z * PX).addScaledVector(d, len * PX / 2); P.add(s); };
    for (const sd of [-1, 1]) [[2.2, 6.6, 4.4, 0.35], [0.4, 7.2, 5.6, 0.05], [-1.6, 6.9, 5.0, -0.25], [-3.0, 6.3, 4.0, -0.5]].forEach(([z, y, len, dz], i) => spike(sd * 3.85, y, z, sd, -0.7 - i * 0.08, dz, len, 1.6, i === 1 ? sheen : dark));
    [[-2.3, -0.35, 4.6], [0, 0, 6.0], [2.3, 0.35, 4.6]].forEach(([x, dx, len], i) => spike(x, i === 1 ? 6.4 : 6.9, -3.85, dx, -0.75, -1, len, 1.7, i === 1 ? sheen : dark));
    const F = new THREE.Group(); F.position.set(-4.3 * PX, 1.8 * PX, -1.2 * PX); F.rotation.set(-1.05, 0, 0.75); hat.add(F);
    const fe = new THREE.Mesh(new THREE.OctahedronGeometry(1), cel({ color: lin(0xff6a1f), flatShading: true })); fe.scale.set(0.6 * PX, 4.2 * PX, 1.7 * PX); fe.position.y = 3.8 * PX; F.add(fe);
    const tip = new THREE.Mesh(new THREE.OctahedronGeometry(1), cel({ color: lin(0xffd23f), flatShading: true })); tip.scale.set(0.5 * PX, 1.4 * PX, 1.2 * PX); tip.position.y = 7.6 * PX; F.add(tip);
    c.polyBits = [P, F]; c.polyBits.forEach(g => { g.visible = !!c.d.polyTufts; });
  },
  tail: TB => TB.box(2.4 * PX, 0.8 * PX, 2 * PX, mat(0x111827), PXB(0, -1.6, -2.8), { rot: [0.5, 0, 0] }) };
const ANCHOR = mascotDef(PENGUIN);
function extraDef(seed) {
  const r = rng(seed), pick = a => a[Math.floor(r() * a.length)];
  const skin = pick([0xf1c8a3, 0xe0a87c, 0xc68a5e, 0x8d5a3b, 0xf6d2b4, 0xd9a07a]);
  const style = pick(['short', 'short', 'bob', 'cap']);
  return { style, hat: style !== 'short', messy: r() < 0.5, bangs: 2, capHex: pick([0xd23a3a, 0x2a6ad8, 0x3aa04a, 0xffd23f]), skin, hairHex: pick([0x1a1820, 0x3a2a20, 0x6b4426, 0x2a1c18, 0x9a4a2a]), eye: '#2a1d14',
    shirt: pick([0xff5c7a, 0x3ec7ff, 0xffd23f, 0x7cf08b, 0xb57bff, 0xff9f43, 0xf4f4f4, 0x4a6cff, 0x2f9a6a]), sleeve: pick([4, 4, 10]), leg: pick([0x2c2f45, 0x3a5a8a, 0x4a4a54, 0x6a5a3a]), shoe: pick([0x1a1820, 0xf4f4f4, 0x5a3a22]), sole: 0x2a2a33, expr: pick(['feliz', 'serio']) };
}
const anchor = makeChar('anchor', ANCHOR, 0.62);
const EXTRAS = []; for (let i = 0; i < 11; i++) EXTRAS.push(makeChar('ex' + i, extraDef(1000 + i * 37), 0.5));
const CHARS = [anchor, ...EXTRAS];
CHARS.forEach(c => world.add(c.root));
const ACTIVE = [];
const keysOf = k => typeof k === 'string' ? [[0, k]] : k;
const keyAt = (keys, t) => { let k = keys[0]; for (const e of keys) if (e[0] <= t) k = e; return k; };
function stand(c, p, rotY, anim, expr, lt) {
  c.root.position.copy(p); c.root.rotation.set(0, rotY, 0); c.root.scale.setScalar(c.baseScale); c.root.visible = true;
  const a = keyAt(keysOf(anim), lt); setAnim(c, a[1], a[0]); c.expr = keyAt(keysOf(expr), lt)[1]; c.clock = lt; c.squashExtra = 0; ACTIVE.push(c);
}
const shot = (p, l, f, focus, bx, up) => ({ p, l, f, focus: focus || l, box: bx || 12, up });
const face = (a, b) => Math.atan2(b.x - a.x, b.z - a.z);
const EMIT = [];
const FX = { sparks: (t, o, seed, n) => ({ t0: t, kind: 'burst', o: o.toArray(), n: n || 22, size: 0.03, speed: 3.4, life: 0.6, colors: [0xffe9a0, 0xffb23a, 0xffffff], seed, glow: true, streak: true, gravity: 3 }),
  ring: (t, o, size, color, f) => ({ t0: t, kind: 'ring', o: o.toArray(), size, color, face: f, life: 0.6 }),
  puff: (t, o, seed, size, life, colors, n, speed) => ({ t0: t, kind: 'puff', o: o.toArray(), n: n || 4, size: size || 0.3, speed: speed || 0.6, life: life || 1.4, colors: colors || [0xf4f1fa, 0xd9d4e6, 0xc9c4d6], seed }),
  confetti: (t, o, seed) => ({ t0: t, kind: 'burst', o: o.toArray(), n: 40, size: 0.07, speed: 3.2, life: 2.4, colors: [0xff6a1f, 0xffd23f, 0x3ec7ff, 0x7cf08b, 0xffffff], seed, spin: 6, gravity: 0.8 }) };

// ---------- the studio ----------
const WALL_MAT = cel({ map: null, emissive: new THREE.Color(1, 1, 1).multiplyScalar(POST.hdr ? 0.85 : 0.6) });
const ANCHOR_SPOT = V(-0.85, 0, -1.2);
const STUDIO = newGroup();
(function studio() {
  const B = makeBatch(), g = STUDIO;
  const floor = cel({ map: T.floorN, emissiveMap: T.floorGlow, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.5) });
  B.box(16, 0.2, 12, floor, [0, -0.1, 0], { tile: 1 });
  B.box(16, 7, 0.3, tmat('panel'), [0, 3.5, -4.6], { tile: 1 });
  for (const s of [-1, 1]) B.box(0.3, 7, 12, tmat('panel'), [s * 8, 3.5, 1.4], { tile: 1 });
  // video wall frame and side screens
  B.box(4.0, 2.4, 0.2, mat(0x0b1230), [1.1, 1.95, -3.12]);
  B.box(4.1, 0.08, 0.24, glow(0xff6a1f, 0.8), [1.1, 3.17, -3.1]);
  for (const s of [-1, 1]) { B.box(0.08, 3.4, 0.08, glow(0xff6a1f, 0.6), [s * 3.6 + 0.2, 1.7, -4.4]); }
  // the anchor desk: an arc of blocks with a glowing stripe and a lit top
  for (let k = -3; k <= 3; k++) {
    const a = k * 0.26, x = ANCHOR_SPOT.x + Math.sin(a) * 1.0, z = ANCHOR_SPOT.z + Math.cos(a) * 1.0, ry = a;
    B.box(0.27, 0.56, 0.36, tmat('panel'), [x, 0.28, z], { rot: [0, ry, 0] });
    B.box(0.29, 0.05, 0.42, mat(0xe8ecf6), [x, 0.585, z - Math.cos(a) * 0.03], { rot: [0, ry, 0] });
    B.box(0.28, 0.05, 0.02, glow(0xff6a1f, 0.9), [x + Math.sin(a) * 0.185, 0.42, z + Math.cos(a) * 0.185], { rot: [0, ry, 0] });
  }
  // ceiling light bars and a studio camera on a tripod
  for (const x of [-4, -1.5, 1, 3.5]) B.box(1.6, 0.1, 0.3, glow(0xf4f8ff, 0.7), [x, 5.2, 0.5]);
  B.box(0.08, 1.3, 0.08, tmat('iron'), [3.6, 0.65, 2.4]); B.box(0.5, 0.36, 0.7, mat(0x2a2f45), [3.6, 1.45, 2.4], { rot: [0, -0.9, 0] }); B.box(0.2, 0.2, 0.2, mat(0x0b0f1f), [3.33, 1.45, 2.2], { rot: [0, -0.9, 0] }); B.box(0.06, 0.06, 0.06, glow(0xff3b4a, 1.2), [3.7, 1.66, 2.5]);
  for (const s of [-0.35, 0.35]) B.box(0.05, 1.2, 0.05, tmat('iron'), [3.6 + s, 0.55, 2.4 + s * 0.4], { rot: [s * 0.4, 0, -s * 0.4] });
  B.flush(g);
  const wall = new THREE.Mesh(new THREE.PlaneGeometry(3.8, 2.14), WALL_MAT); wall.position.set(1.1, 1.95, -3.0); g.add(wall);
  const logo = canvasTex(128, 32, (c, w, h) => { c.fillStyle = '#13204a'; c.fillRect(0, 0, w, h); c.imageSmoothingEnabled = false; c.drawImage(EARTH_ICON, 4, 2, 28, 28); c.fillStyle = '#ffffff'; c.textAlign = 'left'; c.textBaseline = 'middle'; fitFont(c, 'CUBE NEWS', w - 40, 14); c.fillText('CUBE NEWS', 32, h / 2 + 1); }, true);
  const lp = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.225), cel({ map: logo, emissiveMap: logo, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.5) }));
  lp.position.set(ANCHOR_SPOT.x, 0.27, ANCHOR_SPOT.z + 1.19); g.add(lp);
  // side screens with a pixel world map
  const map = canvasTex(64, 96, (c, w, h) => { c.fillStyle = '#0b1a44'; c.fillRect(0, 0, w, h); const r = rng(77); for (let k = 0; k < 160; k++) { c.fillStyle = r() < 0.5 ? '#2f6fe0' : '#1f4fae'; c.fillRect(Math.floor(r() * 16) * 4, Math.floor(r() * 24) * 4, 3, 3); } c.fillStyle = '#ff6a1f'; c.fillRect(20, 40, 4, 4); c.fillRect(40, 24, 4, 4); }, true);
  for (const s of [-1, 1]) { const p = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 2.4), cel({ map, emissiveMap: map, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.7) })); p.position.set(s * 4.6 + 0.6, 2.0, -4.42); g.add(p); }
})();
function wallTex(st) {
  return canvasTex(256, 144, (g, w, h) => {
    const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, '#1d2d63'); gr.addColorStop(1, '#0b1230'); g.fillStyle = gr; g.fillRect(0, 0, w, h);
    g.fillStyle = 'rgba(255,255,255,.05)'; for (let x = 0; x < w; x += 16) g.fillRect(x, 0, 1, h); for (let y = 0; y < h; y += 16) g.fillRect(0, y, w, 1);
    g.fillStyle = '#ff6a1f'; g.fillRect(0, 0, w, 22);
    g.fillStyle = '#141a2e'; g.textAlign = 'left'; g.textBaseline = 'middle'; fitFont(g, st.cat, w - 24, 11); g.fillText(st.cat, 10, 12);
    g.fillStyle = '#ffffff'; g.textAlign = 'center'; fitFont(g, st.big, w - 28, 30); g.fillText(st.big, w / 2, 66);
    g.fillStyle = '#ffb27a'; fitFont(g, st.small, w - 28, 12); g.fillText(st.small, w / 2, 104);
    if (st.icon) st.icon(g, w, h);
  }, true);
}

// ---------- diorama 1: the last troops leave a desert base ----------
function blockPlane() {
  const root = new THREE.Group(), B = makeBatch(), hull = mat(0x8a909c), dark = mat(0x2a2f3a);
  B.box(0.9, 0.9, 5.2, hull, [0, 0.75, 0]); B.box(0.7, 0.7, 0.6, hull, [0, 0.75, 2.85]); B.box(0.5, 0.18, 0.2, dark, [0, 1.0, 3.05]);
  B.box(6.6, 0.1, 1.1, hull, [0, 1.25, 0.3]); B.box(0.12, 1.3, 1.0, hull, [0, 1.75, -2.2]); B.box(2.4, 0.08, 0.7, hull, [0, 2.4, -2.4]);
  for (const x of [-2.5, -1.3, 1.3, 2.5]) { B.box(0.32, 0.32, 0.8, mat(0x5a606c), [x, 1.0, 0.5]); B.box(0.2, 0.2, 0.04, glow(0xffb23a, 0.4), [x, 1.0, 0.08]); }
  for (const [x, z] of [[0, 2.2], [-0.5, -0.4], [0.5, -0.4]]) B.box(0.18, 0.3, 0.3, dark, [x, 0.15, z]);
  B.flush(root); return root;
}
function blockTruck(color) {
  const root = new THREE.Group(), B = makeBatch(), c = mat(color), dark = mat(0x23262e);
  B.box(0.9, 0.7, 0.8, c, [0, 0.6, 0.9]); B.box(0.8, 0.24, 0.05, mat(0x9ec4e8), [0, 0.75, 1.31]);
  B.box(0.95, 0.15, 2.2, dark, [0, 0.3, -0.2]); B.box(0.95, 0.8, 1.7, mat(0x6b6a4a), [0, 0.8, -0.45]);
  for (const z of [0.9, -0.2, -0.9]) for (const s of [-1, 1]) B.box(0.16, 0.34, 0.34, dark, [s * 0.47, 0.17, z]);
  B.flush(root); return root;
}
const D = [];
D[1] = (function iraq() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 13, 1, 501, SAND_MATS));
  const B = makeBatch();
  B.box(2.6, 0.06, 22, tmat('road'), [-3.5, 0.03, 0], { tile: 2.6 });
  for (let x = 1; x <= 10; x += 0.9) for (const z of [-6, 6]) if (!(z === 6 && x > 4 && x < 6.6)) B.box(0.8, 0.8, 0.8, tmat('sandstone'), [x, 0.4, z]);
  for (let z = -6; z <= 6; z += 0.9) B.box(0.8, 0.8, 0.8, tmat('sandstone'), [10.2, 0.4, z]);
  for (const [x, z] of [[3, -3], [6.5, -3.5], [3, 1.5]]) { B.box(2, 0.9, 1.4, mat(0xc9b48a), [x, 0.45, z]); B.box(2.1, 0.14, 1.5, mat(0xa8936a), [x, 0.97, z]); }
  B.box(0.9, 3, 0.9, tmat('sandstone'), [8.5, 1.5, 3.2], { tile: 0.9 }); B.box(1.3, 0.8, 1.3, mat(0x4a8a8a), [8.5, 3.4, 3.2]); B.box(1.5, 0.14, 1.5, mat(0x8a7a5a), [8.5, 3.87, 3.2]);
  B.flush(g);
  const plane = blockPlane(); g.add(plane);
  const trucks = [blockTruck(0xb59a6a), blockTruck(0xa88f60)]; trucks.forEach(t => g.add(t));
  const planeAt = lt => { const s = Math.max(0, lt - 6), z = lt < 6 ? -8 + (lt - 4.5) * 0.6 : -7.1 + 3 * s * s, up = Math.max(0, z - 2); return { z: Math.min(z, 400), y: up * 0.32 + Math.max(0, z - 20) * 0.1, pitch: Math.min(0.28, up * 0.05) }; };
  const truckAt = (lt, k) => { const s = clamp((lt - 9.6 - k * 1.3) / 8); const path = [V(6, 0, 1.2), V(5.3, 0, 6.2), V(4.6, 0, 10.4)]; const p = s < 0.5 ? path[0].clone().lerp(path[1], s * 2) : path[1].clone().lerp(path[2], (s - 0.5) * 2); const q = s < 0.5 ? face(path[0], path[1]) : face(path[1], path[2]); return { p, q }; };
  return {
    g, env: 'day', box: 16,
    fx: () => { const out = []; for (let t = 6.2; t < 8.8; t += 0.18) { const pa = planeAt(t); if (pa.y < 0.3) out.push(FX.puff(t, V(-3.5, 0.1, pa.z - 2.4), 510 + Math.round(t * 10), 0.35, 1.4, [0xe3c88c, 0xd2ae6e])); } return out; },
    update(lt) {
      const pa = planeAt(lt); plane.position.set(-3.5, pa.y, pa.z); plane.rotation.set(-pa.pitch, 0, 0);
      trucks.forEach((t, k) => { const a = truckAt(lt, k); t.position.copy(a.p); t.rotation.y = a.q; });
    },
    cam(lt, v) {
      const pa = planeAt(lt);
      if (lt < 11) return shot(V(v ? 7.5 : 6, (v ? 2.2 : 1.6) + pa.y * 0.6, Math.min(pa.z, 14) - (v ? 4 : 3)), V(-3.5, 1 + pa.y * 0.9, Math.min(pa.z, 16)), v ? 52 : 40, V(0, 0, 0), 16);
      const k = ease(clamp((lt - 11) / 7));
      return shot(V(v ? 2 : 1, v ? 4.2 : 3.4, v ? 18 : 15.5).lerp(V(v ? 1.5 : 0.5, v ? 3.4 : 2.6, v ? 16.5 : 14), k), V(4.8, 0.6, 7.5), v ? 50 : 38, V(4, 0, 4), 16);
    }
  };
})();

// ---------- diorama 2: tankers wait at the Strait of Hormuz ----------
function blockShip(len, hullHex, deckHex, navy) {
  const root = new THREE.Group(), B = makeBatch();
  B.box(1.1, 0.5, len, mat(hullHex), [0, 0.1, 0]); B.box(1.12, 0.12, len, mat(0x1d1a2a), [0, -0.06, 0]); B.box(1.0, 0.08, len - 0.2, mat(deckHex), [0, 0.39, 0]);
  if (navy) { B.box(0.7, 0.6, 1.1, mat(0xa0a6b0), [0, 0.7, -0.2]); B.box(0.08, 0.9, 0.08, tmat('iron'), [0, 1.3, -0.2]); B.box(0.4, 0.2, 0.4, mat(0x8a909c), [0, 0.55, 1.0]); B.box(0.06, 0.06, 0.6, mat(0x5a606c), [0, 0.6, 1.4]); B.box(0.06, 0.06, 0.06, glow(0xff3b4a, 1), [0, 1.78, -0.2]); }
  else { B.box(0.9, 0.7, 0.8, mat(0xf4f1ea), [0, 0.78, -len / 2 + 0.6]); B.box(0.3, 0.5, 0.3, mat(0xd23a3a), [0, 1.3, -len / 2 + 0.5]); for (let z = -len / 2 + 1.4; z < len / 2 - 0.4; z += 0.8) B.box(0.5, 0.12, 0.5, mat(0x8a5a3a), [0, 0.49, z]); }
  B.flush(root); return root;
}
D[2] = (function hormuz() {
  const g = newGroup();
  inGroup(g, () => { voxelIsland(-4, -10, 7.5, 1, 521, SAND_MATS); voxelIsland(6, 10.5, 7, 1, 522, SAND_MATS); });
  const water = cel({ map: T.water }); T.water.repeat.set(1, 1);
  box(30, 0.6, 15, water, g, [1, -0.3, 0.3], { tile: 2, receive: true, cast: false });
  label(g, { es: 'IRÁN', en: 'IRAN' }, 0x13204a, '#ffffff', V(-4, 2.4, -6.8), 0, 2.2, 0.55);
  label(g, { es: 'OMÁN', en: 'OMAN' }, 0x13204a, '#ffffff', V(6, 2.4, 7.6), 0, 2.2, 0.55);
  const tankers = [[-9, 1.5], [-4.5, 3.0], [0, 1.4], [4.6, 2.8]].map(([x, z], k) => { const s = blockShip(4.2, 0x8a2a2a, 0x5a4a3a); s.position.set(x, 0, z); s.rotation.y = PI / 2 + 0.08 * (k % 2 ? 1 : -1); g.add(s); return s; });
  const navy = blockShip(3.2, 0x7a808c, 0x5a606c, true); g.add(navy);
  const B = makeBatch(); for (let z = -4.5; z <= 5; z += 0.9) B.box(0.25, 0.25, 0.25, glow(0xff6a1f, 0.4), [9, 0.08, z]); B.flush(g);
  const navyAt = lt => { const a = lt * 0.3; return { x: Math.sin(a) * 8, z: -1.6 + Math.cos(a) * 0.6, yaw: Math.atan2(Math.cos(a) * 8 * 0.3, -Math.sin(a) * 0.6 * 0.3) }; };
  return {
    g, env: 'day', box: 18,
    update(lt, now) {
      T.water.offset.set((now * 0.02) % 1, (now * 0.05) % 1);
      tankers.forEach((s, k) => { s.position.y = Math.sin(now * 1.3 + k) * 0.04; });
      const n = navyAt(lt); navy.position.set(n.x, Math.sin(now * 1.7) * 0.04, n.z); navy.rotation.y = n.yaw;
    },
    cam(lt, v) {
      if (lt < 11) { const k = ease(clamp((lt - 4.5) / 6.5)); return shot(V(lerp(-14, -6, k), v ? 13 : 10, v ? 18 : 14), V(lerp(-2, 2, k), 0, 1), v ? 52 : 40, V(0, 0, 0), 18); }
      const n = navyAt(lt); return shot(V(n.x + (v ? -3 : -4), v ? 2.4 : 1.8, n.z + (v ? 5.5 : 4.5)), V(n.x + 2, 0.6, n.z + 1.5), v ? 50 : 38, V(n.x, 0, n.z), 12);
    }
  };
})();

// ---------- diorama 3: a launcher raises and fires a missile from a sunflower field ----------
D[3] = (function missile() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 12, 1, 531));
  const stems = [], heads = [], r = rng(532);
  for (let i = -9; i <= 9; i++) for (let j = -9; j <= -1; j++) { if (Math.hypot(i, j) > 10.5 || r() < 0.15) continue; const x = i * 0.8 + (r() - 0.5) * 0.2, z = j * 0.8 + (r() - 0.5) * 0.2, h = 0.7 + r() * 0.3; stems.push({ p: [x, h / 2, z], s: [0.08, h, 0.08] }); heads.push({ p: [x, h + 0.1, z + 0.05], s: [0.36, 0.36, 0.12] }); }
  inGroup(g, () => { instanced(BOX1, mat(0x3f8a2a), stems); instanced(BOX1, tmat('sand', { color: lin(0xffd23f) }), heads); });
  box(1.6, 0.04, 22, tmat('dirt'), g, [0, 0.02, 3.5], { tile: 1, receive: true });
  const truck = new THREE.Group(); g.add(truck); truck.position.set(0, 0, 3.5);
  const B = makeBatch(), olive = mat(0x5a6a3a), dark = mat(0x23262e);
  B.box(1.1, 0.8, 1.1, olive, [0, 0.75, 1.9]); B.box(0.9, 0.26, 0.05, mat(0x9ec4e8), [0, 0.95, 2.46]); B.box(1.1, 0.2, 4.6, dark, [0, 0.35, 0]); B.box(1.0, 0.25, 3.2, olive, [0, 0.58, -0.6]);
  for (const z of [1.7, 0.6, -0.6, -1.8]) for (const s of [-1, 1]) B.box(0.18, 0.4, 0.4, dark, [s * 0.56, 0.2, z]);
  B.flush(truck);
  const arm = new THREE.Group(); arm.position.set(0, 0.75, -2.0); truck.add(arm);
  const M = new THREE.Group(); arm.add(M);
  const MB = makeBatch(); MB.box(0.34, 0.34, 3.0, mat(0xe8ecf0), [0, 0.2, 1.5]); MB.box(0.36, 0.36, 0.2, mat(0x5a6a3a), [0, 0.2, 2.2]); MB.box(0.24, 0.24, 0.3, mat(0xe8ecf0), [0, 0.2, 3.15]); MB.box(0.14, 0.14, 0.22, mat(0x5a6a3a), [0, 0.2, 3.4]); for (const s of [-1, 1]) MB.box(0.04, 0.3, 0.3, mat(0x5a6a3a), [s * 0.2, 0.2, 0.2]); MB.flush(M);
  const cradle = makeBatch(); cradle.box(0.5, 0.12, 3.2, dark, [0, -0.03, 1.5]); cradle.flush(arm);
  const ang = lt => 1.32 * ease(clamp((lt - 5.2) / 3));
  const fly = lt => lt < 10.5 ? 0 : 0.5 * 26 * (lt - 10.5) ** 2;
  const tip = new THREE.Vector3();
  return {
    g, env: 'day', box: 14, shake: [[10.5, 1.2, 0.07]], impact: [10.5],
    fx: () => { const out = [FX.ring(10.5, V(0, 0.2, 1.6), 3.2, 0xffd9a0)]; for (let t = 10.5; t < 11.3; t += 0.04) { const s = fly(t), a = ang(t), y = 0.95 + Math.sin(a) * (0.2 + s), z = 1.5 - Math.cos(a) * (s); out.push(FX.puff(t, V(0, Math.max(0.3, y), 3.5 + z - 2), 540 + Math.round(t * 100), 0.4, 2.2, [0xf4f1fa, 0xdcd8e8, 0xc4c0d2], 3, 0.5)); } for (let t = 10.5; t < 12; t += 0.2) out.push(FX.puff(t, V((Math.sin(t * 9) - 0.2) * 1.5, 0.3, 1.6 + Math.cos(t * 7)), 600 + Math.round(t * 10), 0.6, 2.4, [0xece8f4, 0xd8d4e2], 5, 0.9)); return out; },
    update(lt, now) {
      arm.rotation.x = -ang(lt); M.position.z = fly(lt); M.visible = fly(lt) < 160;
      if (lt >= 10.5 && fly(lt) < 160) EMIT.push(() => { tip.set(0, 0.2, 0); M.localToWorld(tip); const dir = V(0, Math.sin(ang(lt)), -Math.cos(ang(lt))); for (let i = 0; i < 6; i++) PART.add('glow', tip.x, tip.y - dir.y * (0.15 + i * 0.16), tip.z - dir.z * (0.15 + i * 0.16), 0.22 - i * 0.03, [0xfff6c8, 0xffc23a, 0xff7a2a][i % 3], GLOW_K * 1.4, 0); });
    },
    cam(lt, v) {
      if (lt < 10.2) return shot(V(v ? 4.6 : 4, v ? 1.3 : 1.0, v ? 9.5 : 8.2), V(0, 1.3, 2.6), v ? 50 : 38, V(0, 0, 2), 12);
      const y = Math.min(30, 1.5 + fly(lt) * 0.8);
      return shot(V(v ? 9 : 8, v ? 2.4 : 2, v ? 12 : 10.5), V(0, y, Math.max(-8, 1.6 - fly(lt) * 0.2)), v ? 58 : 46, V(0, 0, 2), 16);
    }
  };
})();

// ---------- diorama 4: two neighbours, one bridge, and it breaks ----------
D[4] = (function rift() {
  const g = newGroup(); inGroup(g, () => { voxelIsland(-6.5, 0, 5.5, 1, 541); voxelIsland(6.5, 0, 5.2, 1, 542, SAND_MATS); });
  const B = makeBatch();
  for (const [x, c] of [[-7, 0xf4f1ea], [7, 0xf4f1ea]]) { B.box(3.2, 1.8, 2.2, mat(c), [x, 0.9, -1.6]); B.box(3.6, 0.3, 2.6, mat(0xc9c4b8), [x, 1.95, -1.6]); for (let k = -1; k <= 1; k++) B.box(0.22, 1.6, 0.22, mat(0xe8e4dc), [x + k * 1.0, 0.8, -0.38]); B.box(0.7, 1.1, 0.06, mat(0x5a3a22), [x, 0.55, -0.48]); }
  B.flush(g);
  label(g, 'ETIOPÍA', 0x2f8a4a, '#ffffff', V(-7, 2.75, -1.6), 0, 2.4, 0.6);
  label(g, 'ERITREA', 0x2a6ad8, '#ffffff', V(7, 2.75, -1.6), 0, 2.4, 0.6);
  // the bridge is made of loose blocks so its middle can fall
  const blocks = [];
  for (let k = 0; k < 13; k++) { const x = -2.4 + k * 0.4, m = box(0.4, 0.5, 1.4, tmat('cobble'), g, [x, -0.25, 1.0], { tile: 0.4 }); for (const z of [0.4, 1.6]) box(0.08, 0.35, 0.08, mat(0x8a8478), m, [0, 0.42, z - 1.0]); blocks.push({ m, x, d: Math.abs(x) < 1.3 ? 0.05 + Math.abs(x) * 0.35 + (k % 2) * 0.08 : 99, spin: (k % 3 - 1) * 2 }); }
  // ten diplomats walk across before it breaks
  const walkers = [], WB = [];
  for (let i = 0; i < 10; i++) { const b = box(0.18, 0.4, 0.14, mat([0x2a2f45, 0x3a3446, 0x1d2d63][i % 3]), g, [0, 0, 0]); const h = box(0.16, 0.16, 0.16, mat(0xc68a5e), g, [0, 0, 0]); walkers.push({ b, h, i }); }
  const drones = []; for (let i = 0; i < 3; i++) { const d = new THREE.Group(); const DB = makeBatch(); DB.box(0.3, 0.08, 0.3, mat(0x23262e), [0, 0, 0]); for (const [x, z] of [[-0.2, -0.2], [0.2, -0.2], [-0.2, 0.2], [0.2, 0.2]]) DB.box(0.16, 0.02, 0.04, mat(0x5a606c), [x, 0.06, z]); DB.box(0.04, 0.04, 0.04, glow(0xff3b4a, 1.2), [0, -0.05, 0.16]); DB.flush(d); g.add(d); drones.push(d); }
  const BREAK = 12.2;
  return {
    g, env: 'dusk', box: 14, shake: [[BREAK, 0.6, 0.05]],
    fx: () => [FX.puff(BREAK, V(0, 0.1, 1.0), 550, 0.5, 1.6, [0xc9c4b8, 0xa9a49a], 10, 1.2), FX.puff(BREAK + 0.3, V(0.6, -0.6, 1.0), 551, 0.4, 1.4, [0xc9c4b8, 0xa9a49a], 8, 1.0)],
    update(lt, now) {
      blocks.forEach(b => { const s = lt - BREAK - b.d; if (s > 0) { b.m.position.y = -0.25 - 4.9 * s * s; b.m.rotation.set(s * b.spin, 0, s * b.spin * 0.6); b.m.visible = s < 2.5; } else { b.m.position.y = -0.25; b.m.rotation.set(0, 0, 0); b.m.visible = true; } });
      walkers.forEach(w => { const s = clamp((lt - 4.4 - w.i * 0.32) / 3.6), x = lerp(-5.2, 5.4, s), bob = Math.abs(Math.sin(lt * 9 + w.i)) * 0.04 * (s > 0 && s < 1 ? 1 : 0), z = 0.75 + (w.i % 2) * 0.5; w.b.position.set(x, 0.2 + bob, z); w.h.position.set(x, 0.49 + bob, z); });
      drones.forEach((d, i) => { const s = (lt - 4.5) * 1.6 - i * 2.2; d.position.set(-8 + i * 1.6 + Math.sin(now * 2 + i) * 0.1, 4.2 + i * 0.4, -14 + s); d.visible = s > 0 && s < 26; });
    },
    cam(lt, v) {
      if (lt < 11) return shot(V(0, v ? 4.6 : 3.4, v ? 15 : 11.5), V(0, 0.8, 0), v ? 54 : 42, V(0, 0, 0), 14);
      return shot(V(v ? 0.8 : 1.2, v ? 1.6 : 1.2, v ? 7.6 : 6.0), V(0, -0.2, 1.0), v ? 52 : 42, V(0, 0, 0), 12);
    }
  };
})();

// ---------- diorama 5: Brazil queues up to vote ----------
D[5] = (function vote() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 12, 1, 551));
  const B = makeBatch(), wallM = mat(0xf2e6c8), trim = mat(0x2a6ad8);
  B.box(7, 2.6, 3, wallM, [0, 1.3, -4]); B.box(7.4, 0.3, 3.4, mat(0xc25a3a), [0, 2.75, -4]); B.box(7.1, 0.2, 3.1, trim, [0, 0.1, -4]);
  for (const x of [-2.6, -1.2, 1.6, 2.9]) { B.box(0.8, 0.7, 0.06, mat(0x9ec4e8), [x, 1.6, -2.48]); B.box(0.9, 0.08, 0.08, trim, [x, 1.2, -2.47]); }
  B.box(1.1, 1.7, 0.06, mat(0x5a3a22), [0.2, 0.85, -2.48]);
  // a table with an electronic ballot box
  B.box(1.1, 0.06, 0.6, tmat('planks'), [1.6, 0.62, -1.4]); for (const [x, z] of [[1.1, -1.65], [2.1, -1.65], [1.1, -1.15], [2.1, -1.15]]) B.box(0.06, 0.6, 0.06, tmat('planks'), [x, 0.3, z]);
  B.box(0.42, 0.2, 0.3, mat(0xe8dcc0), [1.75, 0.75, -1.45]); B.box(0.18, 0.12, 0.02, glow(0x9ef0ff, 0.6), [1.68, 0.8, -1.29]); B.box(0.1, 0.1, 0.02, mat(0x2a2f45), [1.86, 0.78, -1.29]);
  // green and yellow bunting between poles
  for (const x of [-5, 5]) B.box(0.1, 3.2, 0.1, tmat('iron'), [x, 1.6, -0.6]);
  for (let k = 0; k < 18; k++) { const x = -4.6 + k * 0.54, y = 3.0 - Math.sin(k / 17 * PI) * 0.6; B.box(0.26, 0.3, 0.02, mat(k % 2 ? 0xffd23f : 0x2f9a4a), [x, y - 0.12, -0.6]); }
  B.flush(g);
  label(g, 'ELECCIONES · 4 DE OCTUBRE', 0x2f9a4a, '#ffd23f', V(0, 3.35, -2.25), 0, 5.2, 1.3);
  tree(-8, -3, 1.3, 552); tree(8.5, 2, 1.4, 553); tree(-7, 6, 1.2, 554); treesInto(g);
  return {
    g, env: 'day', box: 14,
    update(lt) {
      const step = Math.floor(clamp((lt - 4.5) / 3.2, 0, 3));
      EXTRAS.slice(0, 7).forEach((c, i) => {
        const slot = i - step;
        if (slot < 0) { stand(c, V(1.75 + (i % 2) * 0.1, 0, -0.95), PI, 'vote', 'serio', lt); return; }
        const p = V(0.6 + slot * 0.75, 0, 0.2 + slot * 0.55);
        stand(c, p, face(p, V(1.75, 0, -1.4)), Math.abs((lt - 4.5) % 3.2) < 0.5 && lt > 4.5 ? 'walk' : 'idle', 'serio', lt);
      });
    },
    cam(lt, v) {
      if (lt < 11) { const k = ease(clamp((lt - 4.5) / 6.5)); return shot(V(lerp(8, 6, k), v ? 3.4 : 2.6, v ? 10 : 8), V(0.5, 1.1, -1.5), v ? 52 : 40, V(0, 0, -1), 14); }
      return shot(V(v ? 3.9 : 3.5, v ? 1.35 : 1.2, v ? -0.4 : -0.7), V(1.7, 0.74, -1.35), v ? 48 : 36, V(1.5, 0, -1), 8);
    }
  };
})();

// ---------- diorama 6: a virtual set with the IMF forecast as block bars ----------
D[6] = (function chart() {
  const g = newGroup();
  const floor = cel({ map: T.floorN, emissiveMap: T.floorGlow, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.55) });
  box(12, 0.4, 8, floor, g, [0, -0.2, 0], { tile: 1, receive: true });
  const H = 1.6, bars = [];
  [[-1.6, 1.6, 1.5, 0xff6a1f, '2026'], [1.6, 2.2, 1.8, 0x3e7bff, '2027']].forEach(([x, oldV, newV, col, yr]) => {
    const ghost = new THREE.Mesh(new THREE.BoxGeometry(1.0, 1, 1.0), new THREE.MeshBasicMaterial({ color: lin(col), transparent: true, opacity: 0.22, depthWrite: false }));
    ghost.scale.y = oldV * H; ghost.position.set(x - 0.6, oldV * H / 2, 0); g.add(ghost);
    const solid = new THREE.Group(); solid.position.set(x + 0.6, 0, 0); g.add(solid);
    const sb = box(1.0, 1, 1.0, glow(col, 0.35), solid, [0, 0.5, 0]);
    bars.push({ solid, oldV, newV });
    label(g, yr, 0x13204a, '#ffffff', V(x, 0.3, 0.75), 0, 1.4, 0.35, 0.4);
    label(g, `${oldV.toFixed(1)}% → ${newV.toFixed(1)}%`, 0x13204a, '#ffb27a', V(x, oldV * H + 0.6, 0), 0, 2.2, 0.55, 0.5);
  });
  label(g, 'PIB DE MÉXICO · PRONÓSTICO DEL FMI', 0x13204a, '#ffffff', V(0.6, 5.5, -3), 0, 5.6, 1.05, 0.5);
  const arrow = new THREE.Group(); arrow.position.set(5.0, 2.6, -0.8); g.add(arrow);
  const AB = makeBatch(); AB.box(0.4, 1.4, 0.4, glow(0xe5383b, 0.6), [0, 0.3, 0]); for (let k = 0; k < 3; k++) AB.box(1.3 - k * 0.45, 0.25, 0.4, glow(0xe5383b, 0.6), [0, -0.5 - k * 0.25, 0]); AB.flush(arrow);
  return {
    g, env: 'studio', box: 10,
    update(lt) {
      const k = ease(clamp((lt - 6.5) / 2));
      bars.forEach(b => { b.solid.scale.y = lerp(b.oldV, b.newV, k) * H; });
      const a = ease(clamp((lt - 8.4) / 0.6)); arrow.scale.setScalar(Math.max(0.001, a)); arrow.position.y = 2.6 + Math.sin(lt * 3) * 0.08;
      stand(anchor, V(3.7, 0, 1.7), face(V(3.7, 0, 1.7), V(0, 0, 3)), [[0, 'present'], [6.4, 'point'], [9, 'talk']], [[0, 'serio']], lt);
    },
    cam(lt, v) {
      if (lt < 11) return shot(V(v ? 1.6 : 0.9, v ? 1.6 : 1.35, v ? 12.5 : 8.6), V(v ? 1.4 : 1.0, v ? 2.0 : 1.75, 0), v ? 62 : 50, V(0, 0, 0), 10);
      return shot(V(v ? 5.2 : 4.8, v ? 2.8 : 2.5, v ? 7.4 : 6.4), V(1.0, 2.0, 0), v ? 54 : 44, V(0, 0, 0), 10);
    }
  };
})();

// ---------- diorama 7: the 2 October march crosses a plaza ----------
D[7] = (function march() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 13, 1, 571, STONE_MATS));
  const B = makeBatch();
  for (let k = 0; k < 4; k++) B.box(4.4 - k * 1.0, 0.7, 3.4 - k * 0.8, tmat('cobble'), [-6, 0.35 + k * 0.7, -7], { tile: 0.7 });
  B.box(3.2, 3.4, 2.4, mat(0xd8c4a0), [0.5, 1.7, -7.5]); B.box(1.0, 5.2, 1.0, mat(0xd8c4a0), [-0.9, 2.6, -6.6]); B.box(1.2, 0.3, 1.2, mat(0xb8a480), [-0.9, 5.35, -6.6]); B.box(1.4, 1.0, 1.4, mat(0xc8b490), [1.4, 3.9, -7.5]); B.box(0.9, 1.4, 0.06, mat(0x5a3a22), [0.5, 0.7, -6.28]);
  B.box(3.4, 9, 3.4, tmat('quartz'), [6.5, 4.5, -7.6], { tile: 1 });
  for (let y = 1; y < 9; y += 1.2) B.box(3.42, 0.5, 3.42, mat(0x5a8fd0), [6.5, y, -7.6]);
  B.flush(g);
  // the march: an instanced column of people walking left to right
  const N = 72, body = new THREE.InstancedMesh(BOX1, mat(0xffffff, { marchers: 1 }), N), head = new THREE.InstancedMesh(BOX1, mat(0xffffff, { marchers: 2 }), N), r = rng(572);
  const P = []; for (let i = 0; i < N; i++) { body.setColorAt(i, lin([0xf4f4f4, 0x2a2f45, 0xe5383b, 0x3a5a8a, 0x4a4a54, 0xffd23f, 0xff9f43][Math.floor(r() * 7)])); head.setColorAt(i, lin([0xf1c8a3, 0xe0a87c, 0xc68a5e, 0x8d5a3b][Math.floor(r() * 4)])); P.push({ row: Math.floor(i / 4), col: i % 4, ph: r() * TAU, jx: (r() - 0.5) * 0.25 }); }
  for (const m of [body, head]) { m.instanceMatrix.setUsage(THREE.DynamicDrawUsage); m.frustumCulled = false; g.add(m); }
  body.castShadow = true;
  const o = new THREE.Object3D();
  const banner = (text, a, b, w) => { const tex = CANVAS_SIGN(text, 0xf4f1ea, '#141a2e', 256, 40); const p = new THREE.Mesh(new THREE.PlaneGeometry(w, 0.42), cel({ map: tex, side: THREE.DoubleSide })); g.add(p); return { p, a, b }; };
  const banners = [banner('2 DE OCTUBRE NO SE OLVIDA', 7, 8, 1.6), banner('1968 · NI PERDÓN NI OLVIDO', 9, 10, 1.6)];
  const xAt = (lt, row) => -12 + ((row * 0.85 + 1.1 * lt) % 24);
  return {
    g, env: 'day', box: 16,
    update(lt) {
      for (let i = 0; i < N; i++) {
        const q = P[i], x = xAt(lt, q.row) + q.jx, z = 1.4 + q.col * 0.6, h = Math.abs(Math.sin(lt * 7 + q.ph)) * 0.05;
        o.position.set(x, 0.3 + h, z); o.scale.set(0.3, 0.42, 0.2); o.rotation.set(0, PI / 2, 0); o.updateMatrix(); body.setMatrixAt(i, o.matrix);
        o.position.y += 0.33; o.scale.setScalar(0.24); o.updateMatrix(); head.setMatrixAt(i, o.matrix);
      }
      body.instanceMatrix.needsUpdate = true; head.instanceMatrix.needsUpdate = true;
      // banner carriers lead the march
      const lead = -7 + (lt - 4.5) * 1.1;
      EXTRAS.slice(7, 11).forEach((c, i) => { const x = lead - Math.floor(i / 2) * 2.6, z = i % 2 ? 2.8 : 1.4; stand(c, V(x, 0, z + 0.0), PI / 2, 'march', 'serio', lt); });
      banners.forEach((bn, k) => { const x = lead - k * 2.6; bn.p.position.set(x + 0.1, 1.15, 2.1); bn.p.rotation.y = PI / 2; });
    },
    cam(lt, v) {
      const lead = -7 + (lt - 4.5) * 1.1;
      if (lt < 11) return shot(V(lead + (v ? 9 : 8), v ? 3.2 : 2.4, v ? 9 : 7.5), V(lead - 1, 0.9, 1.6), v ? 52 : 40, V(lead, 0, 2), 14);
      return shot(V(lead + (v ? 4.6 : 3.8), v ? 1.3 : 1.15, v ? 3.4 : 2.9), V(lead - 0.6, 1.0, 2.0), v ? 50 : 38, V(lead, 0, 2), 10);
    }
  };
})();

// ---------- diorama 8: a glacier melts off a mountain ----------
D[8] = (function glacier() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 13, 1, 581));
  const stone = [], ice = [], r = rng(582);
  for (let i = -8; i <= 8; i++) for (let j = -8; j <= 8; j++) {
    const d = Math.hypot(i * 1.0, j * 1.15); if (d > 8.5) continue;
    const h = Math.max(0, Math.round(8.5 - d * 1.05 + (r() - 0.5) * 1.2));
    for (let k = 0; k < h; k++) stone.push({ p: [i, k + 0.5, j - 1], s: 1 });
    if (h >= 3) { const cap = h >= 6 ? 2 : 1; for (let k = 0; k < cap; k++) ice.push({ p: [i, h + k + 0.5, j - 1], s: 1, y: h + k, d }); }
  }
  ice.sort((a, b) => (b.y - b.d * 0.5) - (a.y - a.d * 0.5));
  let iceMesh = null;
  inGroup(g, () => { instanced(BOX1, tmat('stone'), stone); iceMesh = instanced(BOX1, tmat('ice'), ice); });
  const lake = box(1, 0.12, 1, cel({ map: T.water }), g, [6.5, 0.06, 4.5], { cast: false, receive: true });
  const thermo = new THREE.Group(); thermo.position.set(-7.5, 0, 6); g.add(thermo);
  const TB = makeBatch(); TB.box(0.5, 3.2, 0.5, mat(0xf4f4f4), [0, 1.6, 0]); TB.box(0.8, 0.8, 0.8, glow(0xe5383b, 0.6), [0, 0.4, 0]); TB.flush(thermo);
  const mercury = box(0.24, 1, 0.24, glow(0xe5383b, 0.8), thermo, [0, 0.8, 0.14], { cast: false });
  const melt = lt => ease(clamp((lt - 5) / 11));
  return {
    g, env: 'day', box: 16,
    fx: () => { const out = []; for (let t = 5.5; t < 18; t += 0.12) { const k = (t * 7.3) % 1, a = k * TAU; out.push(FX.puff(t, V(Math.cos(a) * 3.2, 2.2, Math.sin(a) * 3.2 - 1), 700 + Math.round(t * 100), 0.18, 1.2, [0x8fd4ff, 0x5fb0f0], 2, 0.4)); } return out; },
    update(lt, now) {
      const k = melt(lt); iceMesh.count = Math.max(1, Math.round(ice.length * (1 - 0.8 * k)));
      lake.scale.set(1 + 5 * k, 1, 1 + 3.5 * k); T.water.offset.set((now * 0.02) % 1, (now * 0.04) % 1);
      mercury.scale.y = 0.8 + 1.8 * k; mercury.position.y = 0.6 + (0.8 + 1.8 * k) / 2;
    },
    cam(lt, v) { const a = 0.4 + (lt - 4.5) * 0.09, rr = v ? 27 : 21; return shot(V(Math.sin(a) * rr, v ? 11 : 9, Math.cos(a) * rr), V(0, 4.2, -0.5), v ? 56 : 44, V(0, 0, 0), 16); }
  };
})();

// ---------- diorama 9: liftoff, then docking with a space station ----------
const ORBIT = V(0, 600, 0);
D[9] = (function crew() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 11, 1, 591, CONC_MATS));
  const B = makeBatch(), iron = tmat('iron');
  B.box(2, 0.3, 2, mat(0x2a2f3a), [0, 0.15, 0]);
  for (const [x, z] of [[2.0, -0.7], [2.0, 0.7], [3.4, -0.7], [3.4, 0.7]]) B.box(0.2, 11, 0.2, iron, [x, 5.5, z]);
  for (let y = 1; y < 11; y += 1.2) B.box(1.6, 0.12, 1.6, iron, [2.7, y, 0]);
  B.box(1.4, 0.2, 0.4, iron, [1.3, 9.0, 0]);
  B.flush(g);
  const rocket = new THREE.Group(); g.add(rocket);
  const RB = makeBatch(), white = mat(0xf4f6fa), black = mat(0x1d1f26);
  RB.box(0.62, 6, 0.62, white, [0, 3.6, 0]); RB.box(0.64, 0.5, 0.64, black, [0, 6.6, 0]); RB.box(0.62, 1.8, 0.62, white, [0, 7.75, 0]);
  RB.box(0.7, 0.5, 0.7, white, [0, 8.9, 0]); RB.box(0.5, 0.3, 0.5, white, [0, 9.3, 0]); RB.box(0.3, 0.25, 0.3, black, [0, 9.55, 0]);
  for (const [x, z] of [[0.4, 0], [-0.4, 0], [0, 0.4], [0, -0.4]]) RB.box(0.12, 0.5, 0.12, black, [x, 0.75, z]);
  RB.flush(rocket);
  // orbit: a blocky station, the capsule and a sliver of Earth
  const st = new THREE.Group(); st.position.copy(ORBIT); g.add(st);
  const SB = makeBatch();
  SB.box(9, 0.3, 0.3, mat(0xc9ccd8), [0, 0, 0]);
  for (const x of [-3.6, -2.2, 2.2, 3.6]) for (const s of [-1, 1]) SB.box(1.1, 0.04, 2.6, tmat('solar'), [x, 0, s * 1.6], { tile: 0.5 });
  SB.box(0.8, 0.8, 3.4, mat(0xf4f6fa), [0, 0, 0]); SB.box(2.6, 0.7, 0.7, mat(0xe8ecf0), [0, 0, -1.2]); SB.box(0.5, 0.5, 0.4, mat(0xc9ccd8), [0, 0, 1.9]);
  SB.box(0.08, 0.08, 0.08, glow(0x7cf08b, 1.4), [4.5, 0.2, 0]); SB.box(0.08, 0.08, 0.08, glow(0xff3b4a, 1.4), [-4.5, 0.2, 0]);
  SB.flush(st);
  const capsule = new THREE.Group(); g.add(capsule);
  const CB = makeBatch(); CB.box(0.7, 0.7, 0.7, white, [0, 0, 0]); CB.box(0.5, 0.5, 0.3, white, [0, 0, -0.45]); CB.box(0.3, 0.3, 0.12, black, [0, 0, -0.62]); CB.box(0.72, 0.1, 0.72, black, [0, 0, 0.28]); CB.flush(capsule);
  const earth = new THREE.Mesh(new THREE.IcosahedronGeometry(60, 2), cel({ map: T.earth })); earth.material.flatShading = true; earth.position.copy(ORBIT).add(V(0, -72, -20)); g.add(earth);
  const LIFT = 6.0, rise = lt => lt < LIFT ? 0 : 0.5 * 5 * (lt - LIFT) ** 2;
  const capAt = lt => { const s = clamp((lt - 11.5) / 4); return ORBIT.clone().add(V(0, 0, 1.9 + 0.6 + (1 - easeOut(s)) * 7)); };
  return {
    g, env: lt => lt < 11.5 ? 'day' : 'space', box: lt => lt < 11.5 ? 14 : 10, shake: [[LIFT, 2.4, 0.05]], flash: [15.5],
    fx: () => { const out = [FX.ring(LIFT, V(0, 0.3, 0), 4, 0xffd9a0)]; for (let t = LIFT; t < 10.5; t += 0.1) { const a = t * 5.1; out.push(FX.puff(t, V(Math.cos(a) * 0.8, 0.3, Math.sin(a) * 0.8), 900 + Math.round(t * 10), 0.7, 2.6, [0xf4f1fa, 0xdcd8e8, 0xc4c0d2], 4, 1.4)); } out.push(FX.ring(15.5, ORBIT.clone().add(V(0, 0, 2.3)), 1.2, 0x9ef0ff, true)); return out; },
    update(lt, now) {
      rocket.position.y = rise(lt); rocket.visible = rise(lt) < 220;
      if (lt >= LIFT && rise(lt) < 220) EMIT.push(() => { const y = rocket.position.y + 0.45; for (let i = 0; i < 7; i++) PART.add('glow', Math.sin(now * 40 + i) * 0.05, y - i * 0.22, Math.cos(now * 33 + i) * 0.05, 0.42 - i * 0.045, [0xffffff, 0xfff1a0, 0xffc23a, 0xff7a2a][i % 4], GLOW_K * 1.5, 0); });
      capsule.position.copy(capAt(lt)); capsule.visible = st.visible = earth.visible = lt >= 11.5;
    },
    cam(lt, v) {
      if (lt < LIFT) return shot(V(v ? 8 : 7, v ? 2.6 : 2.0, v ? 11 : 9), V(0.8, 4.4, 0), v ? 56 : 44, V(0, 0, 0), 14);
      if (lt < 11.5) { const y = rise(lt); return shot(V(v ? 9 : 8, v ? 2.2 : 1.8, v ? 12 : 10), V(0, Math.min(y + 5, 90), 0), v ? 60 : 48, V(0, 0, 0), 14); }
      const c = capAt(lt), k = ease(clamp((lt - 11.5) / 6.5));
      return shot(ORBIT.clone().add(V(lerp(v ? 6 : 5.4, v ? 4 : 3.4, k), v ? 2.6 : 2.0, v ? 9 : 7.5)), c.clone().lerp(ORBIT, 0.5).add(V(0, 0, 0.6)), v ? 50 : 40, ORBIT, 10);
    }
  };
})();
