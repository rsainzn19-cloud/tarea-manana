// ---------- mascot casting: six animals in the Cube News suit ----------
// Every candidate shares the orange plaid suit; the head, ears, snout, tail and paws make the animal.
const MASCOTS = [
  { id: 'gato', name: 'Gato', pitch: 'Curioso y con actitud. Los gatos mandan en internet y se comparten solos.',
    voice: { en: ['af_heart', 1.5], es: ['ef_dora', 1.5] },
    fur: 0xa9afbb, dark: 0x5b6170, skin: 0xa9afbb, eye: '#1f5f2c', paw: 0xe5e7eb, foot: 0xe5e7eb, legLow: 0xa9afbb,
    headPaint: (g, H) => {
      for (const k of ['top', 'back', 'left', 'right']) { const q = H[k]; g.fillStyle = 'rgba(70,74,88,.6)'; [[1, 0, 4], [4, 1, 3], [6, 0, 5]].forEach(([x, y, h]) => g.fillRect(q[0] + x, q[1] + y, 1, h)); }
      const f = H.front; g.fillStyle = 'rgba(70,74,88,.7)'; [1, 3, 4, 6].forEach(x => g.fillRect(f[0] + x, f[1], 1, 2));
      paintRows(g, f, 5, 8, 1, 7, 0xf3f4f6, 530);
    },
    snout: { w: 3, h: 1.6, d: 1.2, y: 2.4, color: 0xf3f4f6, nose: [1.4, 0.9], noseColor: 0xf472b6, jaw: 0xe5e7eb },
    build: B => {
      for (const s of [-1, 1]) {
        // pointy ears with pink insides
        B.box(2.6 * PX, 1.6 * PX, 1.2 * PX, mat(0xa9afbb), PXB(s * 2.5, 8.8, 0.5)); B.box(1.6 * PX, 1.4 * PX, 1.2 * PX, mat(0xa9afbb), PXB(s * 2.6, 10.2, 0.5)); B.box(0.8 * PX, 1 * PX, 1.2 * PX, mat(0x5b6170), PXB(s * 2.7, 11.3, 0.5));
        B.box(1.2 * PX, 1.2 * PX, 0.3 * PX, mat(0xf9a8d4), PXB(s * 2.5, 9.3, 1.2));
        // whiskers
        for (const dy of [-0.4, 0.5]) B.box(3 * PX, 0.25 * PX, 0.25 * PX, mat(0x374151), PXB(s * 3.4, 2.4 + dy, 5.2), { rot: [0, 0, s * dy * 0.4] });
      }
    },
    tail: TB => { for (let i = 0; i < 7; i++) TB.box(1.2 * PX, 1.2 * PX, 1.4 * PX, mat(i % 2 ? 0x5b6170 : 0xa9afbb), PXB(0, -1.5 + i * 1.1 + (i > 3 ? (i - 3) * 0.6 : 0), -3 - Math.min(i, 3) * 1.1 + (i > 4 ? (i - 4) * 0.8 : 0))); } },

  { id: 'dalmata', name: 'Dálmata', pitch: 'Leal y emocionado con cada noticia. Sus manchas lo hacen reconocible al instante.',
    voice: { en: ['am_puck', 1.3], es: ['em_alex', 1.3] },
    fur: 0xf5f5f4, dark: 0x1f2937, skin: 0xf5f5f4, eye: '#111827', paw: 0xf5f5f4, foot: 0x1f2937, legLow: 0xf5f5f4,
    headPaint: (g, H) => {
      const r = rng(541); g.fillStyle = '#1f2937';
      for (const k of ['top', 'back', 'left', 'right']) for (let i = 0; i < 5; i++) g.fillRect(H[k][0] + Math.floor(r() * 7), H[k][1] + Math.floor(r() * 7), 1 + (r() < 0.5 ? 1 : 0), 1);
      const f = H.front; g.fillRect(f[0] + 5, f[1] + 2, 3, 1); g.fillRect(f[0] + 6, f[1] + 3, 2, 1); g.fillRect(f[0] + 1, f[1], 2, 1);
    },
    snout: { w: 3.6, h: 2, d: 2.2, y: 2.3, color: 0xf5f5f4, nose: [1.8, 1], noseColor: 0x111827, jaw: 0xfda4af },
    build: B => { for (const s of [-1, 1]) { B.box(1.2 * PX, 5 * PX, 2.6 * PX, mat(0x1f2937), PXB(s * 4.6, 5.4, -0.3), { rot: [0, 0, s * 0.12] }); } },
    tail: TB => { for (let i = 0; i < 5; i++) TB.box(0.9 * PX, 0.9 * PX, 1.4 * PX, mat(i === 2 ? 0x1f2937 : 0xf5f5f4), PXB(0, -1 + i * 0.9, -2.8 - i * 0.9)); } },

  { id: 'mapache', name: 'Mapache', pitch: 'El «ladrón de noticias»: siempre trae la exclusiva. Antifaz negro y cola rayada.',
    voice: { en: ['am_fenrir', 1.35], es: ['em_santa', 1.35] },
    fur: 0x9ca3af, dark: 0x111827, skin: 0x111827, eye: '#0b0b10', paw: 0x374151, foot: 0x374151, legLow: 0x6b7280,
    headPaint: (g, H) => {
      const f = H.front; paintRows(g, f, 0, 2, 0, 8, 0xf3f4f6, 551); paintRows(g, f, 2, 5, 0, 8, 0x111827, 552); paintRows(g, f, 5, 8, 2, 6, 0xf3f4f6, 553);
      for (const s of [H.left, H.right]) paintRows(g, s, 2, 5, 0, 8, 0x111827, 554);
    },
    snout: { w: 2.8, h: 1.6, d: 1.8, y: 2.2, color: 0xf3f4f6, nose: [1.4, 0.9], noseColor: 0x111827, jaw: 0xe5e7eb },
    build: B => { for (const s of [-1, 1]) { B.box(2.2 * PX, 2.2 * PX, 1 * PX, mat(0x6b7280), PXB(s * 2.8, 9, 0)); B.box(1.2 * PX, 1.2 * PX, 0.3 * PX, mat(0xf3f4f6), PXB(s * 2.8, 9, 0.6)); } },
    tail: TB => { for (let i = 0; i < 7; i++) TB.box(1.8 * PX, 1.8 * PX, 1.3 * PX, mat(i % 2 ? 0x111827 : 0x9ca3af), PXB(0, -2 + i * 0.4, -3 - i * 1.15)); } },

  { id: 'panda', name: 'Panda', pitch: 'Tranquilo y adorable. Al ser blanco y negro, resalta sobre cualquier fondo.',
    voice: { en: ['am_michael', 1.3], es: ['em_santa', 1.25] },
    fur: 0xf8fafc, dark: 0x111827, skin: 0x111827, eye: '#0b0b10', paw: 0x111827, foot: 0x111827, legLow: 0x111827,
    over: { mouthPx: '#111827' },
    headPaint: (g, H) => {
      const f = H.front, P = (x, y) => { g.fillStyle = '#111827'; g.fillRect(f[0] + x, f[1] + y, 1, 1); };
      for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) { g.fillStyle = shade(0xf8fafc, 0.97 + ((x * 7 + y * 3) % 5) * 0.01); g.fillRect(f[0] + x, f[1] + y, 1, 1); }
      [[0, 3], [1, 3], [2, 3], [0, 4], [1, 4], [2, 4], [1, 5], [0, 2], [1, 2], [5, 3], [6, 3], [7, 3], [5, 4], [6, 4], [7, 4], [6, 5], [6, 2], [7, 2]].forEach(([x, y]) => P(x, y));
      P(3, 5); P(4, 5);
    },
    build: B => { for (const s of [-1, 1]) B.box(2.4 * PX, 2.4 * PX, 1.2 * PX, mat(0x111827), PXB(s * 3.3, 8.7, 0)); B.box(2 * PX, 1 * PX, 0.6 * PX, mat(0x111827), PXB(0, 3.2, 4.3)); },
    tail: TB => TB.box(1.6 * PX, 1.6 * PX, 1.2 * PX, mat(0xf8fafc), PXB(0, -1, -2.6)) },

  { id: 'ajolote', name: 'Ajolote', pitch: 'Muy mexicano y muy viral. Branquias de colores y una sonrisa que no se le quita.',
    voice: { en: ['af_heart', 1.85], es: ['ef_dora', 1.8] },
    fur: 0xf9a8d4, dark: 0xdb2777, skin: 0xf9a8d4, eye: '#1f1022', paw: 0xf9a8d4, foot: 0xf472b6, legLow: 0xf9a8d4,
    headPaint: (g, H) => { const r = rng(561); g.fillStyle = 'rgba(219,39,119,.35)'; for (const k of ['top', 'back', 'left', 'right']) for (let i = 0; i < 4; i++) g.fillRect(H[k][0] + Math.floor(r() * 8), H[k][1] + Math.floor(r() * 8), 1, 1); },
    build: B => {
      // three feathery gills on each side, the axolotl's crown
      for (const s of [-1, 1]) [[7.4, 0.55], [5.4, 0.15], [3.4, -0.25]].forEach(([y, a], k) => {
        B.box(1 * PX, 4.2 * PX, 1 * PX, mat(0xf43f5e), PXB(s * (4.9 + 1.2 * Math.cos(a)), y + 1.2, -0.5 - k * 0.4), { rot: [0, 0, -s * (0.9 + a)] });
        for (let j = 0; j < 3; j++) B.box(0.7 * PX, 0.7 * PX, 0.7 * PX, mat(0xfb7185), PXB(s * (5.6 + j * 0.8), y + 1.4 + j * 0.25, 0.2 - k * 0.4));
      });
    },
    tail: TB => { for (let i = 0; i < 4; i++) TB.box(0.8 * PX, (3.6 - i * 0.5) * PX, 1.6 * PX, mat(i % 2 ? 0xf472b6 : 0xf9a8d4), PXB(0, -1 - i * 0.3, -3 - i * 1.4)); } },

  PENGUIN
];
MASCOTS.forEach(m => { m.def = mascotDef(m); });
