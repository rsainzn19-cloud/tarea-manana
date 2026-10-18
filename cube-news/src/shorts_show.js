// ---------- three vertical Shorts: hook in the first line, one idea per beat, a question at the end ----------
// Words wrapped in *asterisks* are highlighted in the captions.
const DIVE_SIDE = u => { const s = D[10].state(u); return shot(s.p.clone().add(V(13.5, 1.6, 1.4)), s.p.clone().add(V(0, 1.0, 0.2)), 56, s.p, 10); };
const DIVE_LOW = u => { const s = D[10].state(u); return shot(s.p.clone().add(V(3.4, -2.0, 11.5)), s.p.clone().add(V(0, 0.2, 0.8)), 56, s.p, 10); };
const DIVE_TOP = u => { const s = D[10].state(u); return shot(s.p.clone().add(V(-6.5, 5.2, -7.5)), s.p.clone().add(V(0, -0.6, 1.5)), 54, s.p, 10); };
const HORMUZ_WIDE = () => shot(V(0, 26, 30), V(0, 0, -2), 56, V(0, 0, 0), 18);
const ASTRO_SPOT = () => ORBIT.clone().add(V(1.7, 0.9, 1.6));
const ASTRO_CAM = u => { const k = ease(clamp((u - 11.5) / 5)), a = ASTRO_SPOT(); return shot(a.clone().add(V(lerp(2.6, 2.0, k), 0.5, lerp(3.4, 2.7, k))), a.clone().add(V(-0.1, 0.65, 0)), 52, ORBIT, 10); };
const FAMILY_CAM = u => { const k = ease(clamp(u / 8)); return shot(V(lerp(1.6, 1.0, k), 2.4, lerp(6.4, 5.4, k)), V(-0.2, 1.35, -1.5), 50, V(0, 0, -1), 6); };
// how each beat is staged; the words for each language come from SCRIPT
const STAGE = {
  piloto: [
    { view: { d: 10, u: [6.0, 8.6], cam: DIVE_SIDE }, alarm: true, expr: 'sorpresa', zoom: 1.08, sfx: [['jet', 0.5], ['alarm'], ['boom', 6.05]] },
    { view: { d: 10, u: [0.5, 5.2] }, sfx: [['jet', 0.35]] },
    { view: { d: 10, u: [6.2, 8.8], cam: DIVE_LOW }, alarm: true, expr: 'sorpresa', zoom: 1.14, sfx: [['jet', 0.6], ['alarm']] },
    { view: { d: 10, u: [7.4, 8.6], cam: DIVE_TOP }, alarm: true, expr: 'sorpresa', sfx: [['jet', 0.6], ['alarm']] },
    { view: { studio: true }, anim: 'present', sfx: [['tick']] },
    { view: { d: 10, u: [8.6, 11.2] }, zoom: 1.1, sfx: [['jet', 0.4]] },
    { view: { d: 10, u: [12.4, 15.6] }, expr: 'feliz', sfx: [['jet', 0.25], ['touch', 15]] },
    { view: { d: 10, u: [15.2, 18.8] }, zoom: 1.08, sfx: [['siren']] },
    { view: { studio: true }, anim: 'point', expr: 'sorpresa', zoom: 1.1, sfx: [['tick']] }
  ],
  dolar: [
    { view: { d: 11, u: [5.6, 8.8] }, expr: 'sorpresa', zoom: 1.06, sfx: [['coins', 6, 8], ['kaching']] },
    { view: { d: 11, u: [0.6, 4.4] }, sfx: [['coins', 0.6, 4]] },
    { view: { d: 2, u: [4.6, 11] }, zoom: 1.1, sfx: [['water'], ['horn', 0.15]] },
    { view: { d: 2, u: [11, 17.5] }, sfx: [['water']] },
    { view: { studio: true }, anim: 'present', sfx: [['tick']] },
    { view: { d: 2, u: [6, 12], cam: HORMUZ_WIDE }, zoom: 1.08, sfx: [['water'], ['horn', 0.1]] },
    { view: { d: 11, u: [9, 12.5] }, sfx: [['kaching']] },
    { view: { studio: true }, anim: 'point', zoom: 1.1, sfx: [['tick']] }
  ],
  papa: [
    { view: { d: 9, u: [12, 15.2], cam: ASTRO_CAM, astro: true }, expr: 'sorpresa', zoom: 1.06, sfx: [['shimmer']] },
    { view: { d: 9, u: [5.0, 9.4] }, sfx: [['rocket', 6.0]] },
    { view: { d: 12, u: [0, 4], cam: FAMILY_CAM }, expr: 'feliz', sfx: [['heart']] },
    { view: { d: 9, u: [12.4, 15.2], cam: ASTRO_CAM, astro: true }, zoom: 1.1, sfx: [['shimmer']] },
    { view: { d: 12, u: [4, 8], cam: FAMILY_CAM }, sfx: [['heart']] },
    { view: { d: 9, u: [12.4, 17.2] }, zoom: 1.08, sfx: [['shimmer'], ['clunk', 15.5]] },
    { view: { studio: true }, anim: 'point', zoom: 1.1, sfx: [['tick']] }
  ]
};
const SRC = {
  piloto: [['AP / PBS', 'https://www.pbs.org/newshour/world/what-to-know-about-the-terrifying-flydubai-flight-forced-to-land-in-saudi-arabia'], ['CNN', 'https://www.cnn.com/2026/09/30/middleeast/flydubai-israel-plane-pilot-hijacking-incident-latam-hnk-intl'], ['CBS News', 'https://www.cbsnews.com/news/tel-aviv-israel-flydubai-flight-saudi-arabia-diverted-pilots/'], ['Al Jazeera', 'https://www.aljazeera.com/news/2026/9/30/what-happened-on-diverted-flydubai-flight-from-dubai-to-tel-aviv'], ['Euronews', 'https://www.euronews.com/2026/09/30/flydubai-plane-traveling-to-israel-diverted-to-saudi-arabia-after-alleged-dispute-between-']],
  dolar: [['AP / PBS', 'https://www.pbs.org/newshour/world/irans-currency-hits-a-new-record-low-as-war-erodes-the-countrys-economic-stability'], ['Washington Times (AP)', 'https://www.washingtontimes.com/news/2026/sep/29/irans-currency-hits-another-record-low-war-erodes-countrys-economic/'], ['Democracy Now!', 'https://www.democracynow.org/2026/9/30/headlines/iranian_rial_hits_record_low_against_the_dollar_as_war_takes_heavy_toll_on_economy'], ['EIA: estrechos petroleros', 'https://www.eia.gov/international/content/analysis/special_topics/World_Oil_Transit_Chokepoints/'], ['AAA, septiembre 2026', 'https://gasprices.aaa.com/2026/09/']],
  papa: [['AP vía KSAT', 'https://www.ksat.com/news/world/2026/10/01/spacex-launches-4-astronauts-on-a-fast-track-to-the-international-space-station/'], ['WSGW (AP)', 'https://www.wsgw.com/crew-docks-at-space-station-with-astronaut-wholl-miss-birth-of-3rd-child/'], ['Yahoo Noticias Canadá', 'https://ca.news.yahoo.com/astronaut-crew-begins-6-month-225618780.html'], ['Space.com', 'https://www.space.com/space-exploration/human-spaceflight/spacex-crew-13-astronaut-mission-iss-launch']]
};
const SHORTS = Object.keys(STAGE).map(id => ({ id, src: SRC[id], title: SCRIPT[id].title, hook: SCRIPT[id].hook, total: 0,
  beats: STAGE[id].map((st, i) => Object.assign({ index: i, L: SCRIPT[id].beats[i], wall: {} }, st)) }));

// ---------- timing: every line lasts as long as its narration; words share the line by length ----------
// which gestures suit each story: the serious one avoids the playful flaps and cheers
const TONE = { piloto: ['point', 'lean', 'shrug', 'count', 'think', 'present'], dolar: ['point', 'lean', 'shrug', 'count', 'think', 'hype', 'present'], papa: ['hype', 'flap', 'wow', 'point', 'shrug', 'count', 'lean', 'present'] };
// the big gestures carry their own face
const POSE_FACE = { wow: 'sorpresa', hype: 'feliz', flap: 'sorpresa' };
// while he talks, every gesture gets a nodding head on top
for (const k of new Set(Object.values(TONE).flat())) {
  const f = ANIMS[k];
  ANIMS['say_' + k] = (P, t, c) => { f(P, t, c); if (c && c.speaking) { P.headRX += Math.sin(t * 9.5) * 0.05; P.headRY += Math.sin(t * 2.3) * 0.09; } };
}
const STOP = new Set(['de', 'a', 'y', 'en', 'el', 'la', 'los', 'las', 'un', 'una', 'con', 'por', 'que', 'del', 'al', 'su', 'se', 'lo', 'es', 'mientras', 'para',
  'the', 'an', 'of', 'to', 'in', 'on', 'and', 'with', 'for', 'from', 'at', 'by', 'his', 'their', 'is', 'was', 'while', 'this', 'that']);
// right after the hook, the logo sting: the Earth cube drops in with the jingle
const STING = 3.0;
const PAUSE = w => /…$/.test(w) ? 0.3 : /[.?!:]$/.test(w) ? 0.2 : /,$/.test(w) ? 0.12 : 0;
function layout(sh, lang) {
  let t = 0, prev = null; const last = sh.beats.length - 1;
  sh.beats.forEach((b, bi) => {
    const L = b.L[lang], vd = VOICE[lang][sh.id][bi].d, lead = bi ? 0.14 : 0.06;
    b.a = t; b.va = t + lead; b.vb = b.va + vd; b.dur = lead + vd + (bi === last ? 1.6 : 0.2); b.b = t + b.dur; b.text = L.text;
    const words = []; let em = false;
    L.text.split(/\s+/).forEach(w => { if (w.startsWith('*') || w.startsWith('…*')) em = true; const clean = w.replace(/\*/g, ''); words.push({ w: clean, em }); if (w.replace(/[,.…:?!]+$/, '').endsWith('*')) em = false; });
    const pauses = words.map((w, k) => k < words.length - 1 ? PAUSE(w.w) : 0), P = pauses.reduce((s, x) => s + x, 0);
    const speak = Math.max(vd * 0.6, vd - P), W = words.reduce((s, w) => s + w.w.replace(/[^\p{L}\p{N}]/gu, '').length + 1.6, 0);
    let wt = b.va; const chunks = []; let cur = null;
    words.forEach((w, k) => {
      // up to three words (four inside a highlighted phrase); never strand a little word at the end of a chunk
      const n = cur ? cur.words.length : 0, inEm = w.em && n && cur.words[n - 1].em;
      if (!cur || n >= (inEm ? 4 : 3) || (n >= 2 && STOP.has(w.w.toLowerCase()) && k < words.length - 1)) { cur = { a: wt, words: [] }; chunks.push(cur); }
      w.a = wt; wt += speak * (w.w.replace(/[^\p{L}\p{N}]/gu, '').length + 1.6) / W; w.b = wt; cur.words.push(w); cur.b = w.b; wt += pauses[k];
      if (pauses[k] && k < words.length - 1) cur = null;
    });
    b.chunks = chunks;
    // jump cuts: every time the narration resumes after a pause, the host lands somewhere new in a new pose
    // (a cut needs at least a second of the previous take, so quick commas do not strobe)
    const starts = [b.a]; words.forEach((w, k) => { if (k && pauses[k - 1] && w.a - starts[starts.length - 1] >= 1.0 && b.vb - w.a >= 0.6) starts.push(w.a - 0.04); });
    const r = rng(hashStr(sh.id + lang) + bi * 31), pool = TONE[sh.id] || TONE.dolar;
    b.segs = starts.map((a, si) => {
      // the sign-off always ends pointing at the viewer, so nothing before it in that beat points
      let pose = bi === last && si === starts.length - 1 ? 'point' : null;
      while (!pose) { const q = pool[Math.floor(r() * pool.length)]; if ((!prev || q !== prev.pose) && !(bi === last && q === 'point')) pose = q; }
      const xs = [-0.3, 0, 0.3].filter(x => !prev || x !== prev.x), x = bi === 0 && si === 0 ? 0 : xs[Math.floor(r() * xs.length)];
      const seg = { a, pose, x, size: prev && prev.size > 1 ? 0.94 : [0.94, 1, 1.1][Math.floor(r() * 3)], yaw: -x * 0.9 + (r() - 0.5) * 0.25, punch: prev && !prev.punch ? 0.05 : 0, first: si === 0 };
      prev = seg; return seg;
    });
    b.card = L.card ? { big: L.card[0], small: L.card[1], at: lead + L.card[2] * vd } : null;
    if (b.view.studio) b.view.tex = b.wall[lang] || (b.wall[lang] = wallTex({ cat: L.wall[0], big: L.wall[1], small: L.wall[2] }));
    t = b.b;
    if (bi === 0) { sh.sting = { sting: true, index: -1, a: t, dur: STING, b: t + STING, va: -1, vb: -1, chunks: [], card: null, view: { logo: true } }; t += STING; }
  });
  sh.total = t;
}
for (const k of [2, 9, 10, 11, 12]) D[k].fxList = D[k].fx ? D[k].fx() : [];
function setWall(tex) { if (WALL_MAT.map === tex) return; WALL_MAT.map = tex; WALL_MAT.emissiveMap = tex; WALL_MAT.needsUpdate = true; }
const val = (x, lt, d) => x == null ? d : typeof x === 'function' ? x(lt) : x;

// ---------- looks for each place ----------
const ENVS = {
  studio: { bg: 'studio', fog: 0x0a1230, near: 30, far: 140, tint: [1, 1, 1], bloom: 0.6, vig: 0.3 },
  day: { bg: 'day', fog: 0xc9dcff, near: 90, far: 260, tint: [1, 1, 1], bloom: 0.35, vig: 0.28 },
  dusk: { bg: 'dusk', fog: 0xd8907a, near: 50, far: 200, tint: [1.02, 0.84, 0.74], bloom: 0.55, vig: 0.4, warm: 0.35 },
  space: { bg: 'night', fog: 0x000000, near: 400, far: 900, tint: [1.05, 1.05, 1.1], bloom: 0.8, vig: 0.4 },
  logo: { bg: 'logo', fog: 0x8fd0ff, near: 300, far: 600, tint: [1, 1, 1], bloom: 0.45, vig: 0.12 }
};
let envCur = '';
camera.far = 250; camera.updateProjectionMatrix();
function setEnv(k) {
  if (k === envCur) return; envCur = k; const e = ENVS[k];
  scene.background = SKY[e.bg]; scene.fog.color.copy(lin(e.fog)); scene.fog.near = e.near; scene.fog.far = e.far;
  SCENE_TINT.value.set(e.tint[0], e.tint[1], e.tint[2]); POST.U.uBloom.value = e.bloom;
}
function hideAll() {
  for (const g of GROUPS) g.visible = false;
  for (const c of CHARS) c.root.visible = false;
  ACTIVE.length = 0; EMIT.length = 0;
}
TRAILS.draw([], 0);

// ---------- the presenter: composited over the footage like a green-screen host ----------
// The lens is shifted so the footage sits in the upper half and the host fills the lower half.
const LENS = -0.36, HOST_HEAD = -0.24, HOST_TAN = Math.tan(56 * PI / 360);
const _hf = V(0, 0, 0), _hu = V(0, 0, 0), _hr = V(0, 0, 0), _hp = V(0, 0, 0);
// the host floats just in front of the lens, so it must not throw shadows into the footage
anchor.root.traverse(o => { if (o.isMesh) o.castShadow = false; });
// each take (seg) puts him at its own spot across the frame, nearer or farther, turned toward the middle
const HOST_HOME = { x: 0, size: 1, yaw: 0, a: 0 };
function composite(anim, expr, t, seg) {
  seg = seg || HOST_HOME;
  const th = Math.tan(camera.fov * PI / 360), d = 2.35 * HOST_TAN / th / seg.size, h = 1.24;
  _hf.set(0, 0, -1).applyQuaternion(camera.quaternion); _hu.set(0, 1, 0).applyQuaternion(camera.quaternion); _hr.set(1, 0, 0).applyQuaternion(camera.quaternion);
  _hp.copy(camera.position).addScaledVector(_hf, d).addScaledVector(_hu, (HOST_HEAD + LENS) * d * th - h).addScaledVector(_hr, seg.x * d * th * camera.aspect);
  stand(anchor, _hp, 0, anim, expr, t);
  anchor.root.quaternion.copy(camera.quaternion); anchor.root.rotateY(seg.yaw);
  // he lands in each new take with a little springy pop
  const k = (t - seg.a) / 0.22;
  if (!REDUCED && k >= 0 && k < 1) anchor.root.scale.multiplyScalar(1 + Math.sin(k * PI) * (1 - k) * 0.12);
}
const studioShort = (lt, dur) => { const k = ease(clamp(lt / Math.max(dur, 1))); return shot(V(1.1, 1.95, lerp(3.5, 3.0, k)), V(1.1, 1.95, -3.0), 56, V(0, 0, -1), 8); };

// ---------- UI ----------
const S = { cur: 0, t: REDUCED ? 0.6 : 0, playing: !REDUCED, host: true, caps: true, lang: 'es' };
const ICON_PLAY = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 2.5v11l9-5.5z"/></svg>';
const ICON_PAUSE = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3.5 2.5h3v11h-3zM9.5 2.5h3v11h-3z"/></svg>';
const ICON_SOUND = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M2 6h3l4-3.5v11L5 10H2zM11 5.2a4 4 0 0 1 0 5.6l-.9-.9a2.7 2.7 0 0 0 0-3.8zM12.8 3.4a6.5 6.5 0 0 1 0 9.2l-.9-.9a5.2 5.2 0 0 0 0-7.4z"/></svg>';
const el = { tc: $('tc'), tcPill: $('tcPill'), cap: $('cap'), card: $('card'), cBig: $('cBig'), cSmall: $('cSmall'), alarm: $('alarm'), bar: $('bar'), play: $('play'), scrub: $('scrub'), script: $('script'), srcs: $('sources'), sound: $('soundBtn'), logo: $('logoTxt'), logoTag: $('logoTag') };
const fmtT = s => Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0');
for (const sh of SHORTS) layout(sh, S.lang);
const pickBtns = SHORTS.map((sh, k) => {
  const li = document.createElement('li'), btn = document.createElement('button');
  btn.innerHTML = '<span class="n">' + String.fromCharCode(65 + k) + '</span><span><span class="k"></span><span class="hk"></span></span><span class="tm"></span>';
  btn.addEventListener('click', () => { pick(k); audioSync(); });
  li.appendChild(btn); $('picker').appendChild(li); return btn;
});
function labelPicker() {
  SHORTS.forEach((sh, k) => { const b = pickBtns[k]; b.querySelector('.k').textContent = sh.title[S.lang]; b.querySelector('.hk').textContent = '«' + sh.hook[S.lang] + '»'; b.querySelector('.tm').textContent = fmtT(sh.total); });
}
let scriptItems = [];
function pick(k) {
  S.cur = k; S.t = 0; lastBeat = -1; lastChunk = null; lastCard = null; el.card.classList.remove('on'); el.card.style.opacity = '0';
  const sh = SHORTS[k];
  pickBtns.forEach((b, j) => b.setAttribute('aria-current', j === k ? 'true' : 'false'));
  el.script.textContent = ''; scriptItems = sh.beats.map(b => {
    const li = document.createElement('li'), btn = document.createElement('button');
    const tm = document.createElement('span'); tm.className = 'tm'; tm.textContent = fmtT(b.a);
    const tx = document.createElement('span'); tx.className = 'tx'; tx.lang = S.lang;
    b.text.split(/(\*[^*]+\*)/).forEach(part => { if (!part) return; if (part.startsWith('*')) { const m = document.createElement('mark'); m.textContent = part.slice(1, -1); tx.appendChild(m); } else tx.appendChild(document.createTextNode(part)); });
    btn.appendChild(tm); btn.appendChild(tx);
    btn.addEventListener('click', () => { S.t = b.a + 0.001; lastBeat = -1; audioSync(); });
    li.appendChild(btn); el.script.appendChild(li); return btn;
  });
  el.srcs.textContent = '';
  sh.src.forEach(([name, url]) => { const li = document.createElement('li'), a = document.createElement('a'); a.href = url; a.target = '_blank'; a.rel = 'noopener'; a.textContent = name; li.appendChild(a); el.srcs.appendChild(li); });
  $('srcHead').textContent = 'Fuentes · ' + sh.title.es;
  el.scrub.max = sh.total; syncUI();
}
function setLang(l) { S.lang = l; setSceneLang(l); el.logoTag.textContent = l === 'en' ? 'NEWS IN BLOCKS' : 'NOTICIAS EN BLOQUES'; for (const sh of SHORTS) layout(sh, l); labelPicker(); stage.lang = l; pick(S.cur); }
function syncUI() {
  el.play.innerHTML = S.playing ? ICON_PAUSE + 'Pausar' : ICON_PLAY + 'Reproducir';
  el.tcPill.classList.toggle('live', S.playing);
  $('hostBtn').setAttribute('aria-pressed', S.host); $('capBtn').setAttribute('aria-pressed', S.caps);
  $('langEs').setAttribute('aria-pressed', S.lang === 'es'); $('langEn').setAttribute('aria-pressed', S.lang === 'en');
  el.sound.setAttribute('aria-pressed', AUD.on);
  el.sound.innerHTML = ICON_SOUND + (AUD.busy ? 'Preparando sonido…' : AUD.on ? 'Sonido activado' : 'Activar sonido');
}

// ---------- live sound: the whole mix is rendered once per Short and language, then played in sync ----------
const AUD = { ctx: null, on: false, busy: false, src: null, startCtx: 0, startT: 0, token: 0, mixes: {} };
function stopSound() { if (AUD.src) { try { AUD.src.stop(); } catch (e) { /* already stopped */ } AUD.src.disconnect(); AUD.src = null; } }
// the published page carries each finished mix as an MP3; a local build can still mix from the parts
function mixFor(sh, lang) { const key = sh.id + '/' + lang; if (!AUD.mixes[key]) AUD.mixes[key] = typeof MIX !== 'undefined' ? AUD.ctx.decodeAudioData(b64buf(MIX[lang][sh.id])) : renderMix(sh, lang, AUD.ctx.sampleRate); return AUD.mixes[key]; }
async function audioSync() {
  stopSound(); const token = ++AUD.token;
  if (!AUD.on || !S.playing) { AUD.busy = false; syncUI(); return; }
  const sh = SHORTS[S.cur]; AUD.busy = true; syncUI();
  let buf;
  try { buf = await mixFor(sh, S.lang); } catch (e) { AUD.busy = false; AUD.on = false; syncUI(); fail('No se pudo preparar el sonido en este navegador.'); return; }
  if (token !== AUD.token) return;
  AUD.busy = false; syncUI();
  if (!AUD.on || !S.playing) return;
  const src = AUD.ctx.createBufferSource(); src.buffer = buf; src.loop = true; src.loopStart = 0; src.loopEnd = sh.total; src.connect(AUD.ctx.destination);
  const off = clamp(S.t, 0, sh.total - 0.01); src.start(0, off); AUD.src = src; AUD.startCtx = AUD.ctx.currentTime; AUD.startT = off;
}
el.sound.addEventListener('click', () => {
  if (!AUD.ctx) { const AC = window.AudioContext || window.webkitAudioContext; if (!AC) { el.sound.disabled = true; return; } AUD.ctx = new AC(); }
  AUD.ctx.resume(); AUD.on = !AUD.on; if (AUD.on) S.playing = true; audioSync();
});
el.play.addEventListener('click', () => { S.playing = !S.playing; syncUI(); audioSync(); });
$('restart').addEventListener('click', () => { S.t = 0; S.playing = true; syncUI(); audioSync(); });
$('hostBtn').addEventListener('click', () => { S.host = !S.host; syncUI(); });
$('capBtn').addEventListener('click', () => { S.caps = !S.caps; lastChunk = null; syncUI(); });
$('langEs').addEventListener('click', () => { if (S.lang !== 'es') { setLang('es'); audioSync(); } });
$('langEn').addEventListener('click', () => { if (S.lang !== 'en') { setLang('en'); audioSync(); } });
let scrubbing = false;
el.scrub.addEventListener('pointerdown', () => { scrubbing = true; });
window.addEventListener('pointerup', () => { scrubbing = false; });
el.scrub.addEventListener('input', () => { stopSound(); S.t = clamp(+el.scrub.value, 0, SHORTS[S.cur].total - 0.001); });
el.scrub.addEventListener('change', () => audioSync());
const fsBtn = $('fs'), monitor = $('monitor');
if (!document.fullscreenEnabled || !monitor.requestFullscreen) fsBtn.hidden = true;
fsBtn.addEventListener('click', () => { if (document.fullscreenElement) document.exitFullscreen(); else monitor.requestFullscreen().catch(() => { fsBtn.hidden = true; }); });
document.addEventListener('keydown', e => {
  if (e.code !== 'Space' || /^(INPUT|BUTTON|TEXTAREA|A)$/.test(e.target.tagName)) return;
  e.preventDefault(); S.playing = !S.playing; syncUI(); audioSync();
});
function resize() {
  const w = stage.clientWidth, h = stage.clientHeight; if (!w || !h) return;
  renderer.setPixelRatio(EXPORTING ? 1 : Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
}
let EXPORTING = false;
new ResizeObserver(resize).observe(stage); resize();
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => FONT_REDRAW.forEach(f => f()));

// captions, number cards, alarm and progress, all driven by the video clock
let lastBeat = -1, lastChunk = null, lastWord = -1, lastCard = null, lastTc = '';
function hud(sh, b, lt) {
  const t = S.t;
  stage.classList.toggle('intro', !!b.sting); logoText(el.logo, lt, !!b.sting);
  if (b.index !== lastBeat) { lastBeat = b.index; scriptItems.forEach((s, j) => s.setAttribute('aria-current', j === b.index ? 'true' : 'false')); }
  let ch = null; if (S.caps) for (const c of b.chunks) if (t >= c.a - 0.02 && t < (c === b.chunks[b.chunks.length - 1] ? b.b : c.b + 0.25)) ch = c;
  if (ch !== lastChunk) {
    lastChunk = ch; lastWord = -1; el.cap.textContent = '';
    if (ch) ch.words.forEach(w => { const s = document.createElement('span'); s.textContent = w.w.replace(/^…/, ''); if (w.em) s.className = 'em'; el.cap.appendChild(s); el.cap.appendChild(document.createTextNode(' ')); });
  }
  if (ch) {
    let wi = -1; ch.words.forEach((w, k) => { if (t >= w.a) wi = k; });
    if (wi !== lastWord) { lastWord = wi; [...el.cap.querySelectorAll('span')].forEach((s, k) => s.classList.toggle('on', k <= wi)); }
    const p = clamp((t - ch.a) / 0.14), s = REDUCED ? 1 : 0.82 + 0.18 * easeOut(p) + Math.sin(p * PI) * 0.06;
    el.cap.style.transform = 'translateX(-50%) scale(' + s.toFixed(3) + ')';
  }
  el.cap.classList.toggle('on', !!ch);
  const cd = b.card && lt >= b.card.at ? b.card : null;
  if (cd !== lastCard) { lastCard = cd; if (cd) { el.cBig.textContent = cd.big; el.cSmall.textContent = cd.small; } else el.card.style.opacity = '0'; el.card.classList.toggle('on', !!cd); }
  if (cd) { const p = clamp((lt - cd.at) / 0.3), out = clamp((b.dur - lt) / 0.2), s = REDUCED ? 1 : (0.6 + 0.4 * easeOut(p) + Math.sin(p * PI) * 0.12) * (0.85 + 0.15 * out); el.card.style.transform = 'translateX(-50%) rotate(' + ((1 - p) * -6).toFixed(2) + 'deg) scale(' + s.toFixed(3) + ')'; el.card.style.opacity = Math.min(1, out * 1.5).toFixed(2); }
  el.alarm.style.opacity = b.alarm && !REDUCED ? (0.45 + 0.4 * Math.abs(Math.sin(t * 5))).toFixed(2) : b.alarm ? '0.5' : '0';
  el.bar.style.transform = 'scaleX(' + (t / sh.total).toFixed(4) + ')';
  const tc = fmtT(t) + ' / ' + fmtT(sh.total);
  if (tc !== lastTc) { lastTc = tc; el.tc.textContent = tc; }
  if (!scrubbing) el.scrub.value = t;
}
const beatAt = (sh, t) => { if (sh.sting && t >= sh.sting.a && t < sh.sting.b) return sh.sting; for (let i = sh.beats.length - 1; i >= 0; i--) if (t >= sh.beats[i].a) return sh.beats[i]; return sh.beats[0]; };
const _look = V(0, 0, 0), _shk = V(0, 0, 0);
scene.autoUpdate = false;
// draws the frame at the current S.t; `now` drives idle motion that is not tied to the story clock
function draw(now) {
  const sh = SHORTS[S.cur], b = beatAt(sh, S.t), lt = S.t - b.a, v = b.view;
  hideAll();
  let c, env, u = lt, dio = null;
  if (v.logo) {
    env = 'logo'; LOGO.g.visible = true; LOGO.update(lt); c = LOGO.cam(lt, true);
  } else if (v.studio) {
    env = 'studio'; STUDIO.visible = true; setWall(v.tex);
    c = studioShort(lt, b.dur);
  } else {
    dio = D[v.d]; u = lerp(v.u[0], v.u[1], clamp(lt / b.dur));
    env = val(dio.env, u, 'day'); dio.g.visible = true; dio.update(u, now);
    c = v.cam ? v.cam(u) : dio.cam(u, true); c.box = val(dio.box, u, 14);
    if (v.astro) { stand(astro, ASTRO_SPOT(), -0.6 + Math.sin(now * 0.4) * 0.3, 'float', 'serio', S.t); astro.root.rotation.z = Math.sin(now * 0.5) * 0.25; astro.root.rotation.x = -0.2; }
  }
  setEnv(env);
  const sh2 = REDUCED || !dio ? 0 : shakeAt(dio.shake, u);
  camera.position.copy(c.p); _look.copy(c.l);
  if (sh2 > 0) { _shk.set(Math.sin(now * 61) + Math.sin(now * 37.3), Math.sin(now * 53.7 + 1) + Math.sin(now * 29.1), Math.sin(now * 47.9 + 2)).multiplyScalar(sh2 * 0.5); camera.position.add(_shk); _look.add(_shk.multiplyScalar(0.6)); }
  camera.fov = c.f; camera.updateProjectionMatrix();
  camera.projectionMatrix.elements[9] = LENS; camera.projectionMatrixInverse.copy(camera.projectionMatrix).invert();
  camera.up.copy(c.up || UP); camera.lookAt(_look);
  // the host's mouth moves only while the narration is speaking
  const speaking = S.t >= b.va && S.t < b.vb; anchor.speaking = speaking;
  // the current take: a new pose and spot every time he picks up after a pause
  const segs = b.segs || [], seg = segs.reduce((m, x) => x.a <= S.t ? x : m, segs[0]);
  const anim = seg ? 'say_' + seg.pose : 'talk';
  // Mr. News winks on his sign-off
  const wink = b.index === sh.beats.length - 1 && S.t > b.vb - 0.85;
  const mood = wink ? 'guino' : b.expr || (seg && POSE_FACE[seg.pose]) || 'serio';
  if (S.host && !b.sting) composite(anim, mood, S.t, seg);
  for (const ch of ACTIVE) updateChar(ch, now);
  aimSun(c.focus, c.box);
  scene.updateMatrixWorld();
  PART.begin(); if (dio) drawFx2(dio.fxList, u); else if (b.sting) drawFx2(LOGO.fx, lt); for (const e of EMIT) e(now, u); PART.end();
  let imp = 0; if (!REDUCED && dio) for (const t0 of dio.impact || []) { const d = u - t0; if (d >= 0 && d < 0.05) imp = 1; else if (d >= 0.1 && d < 0.14) imp = 0.85; }
  let fl = 0; if (!REDUCED && dio) for (const t0 of dio.flash || []) { const d = u - t0; if (d >= 0 && d < 0.4) fl = Math.max(fl, 0.8 * (1 - d / 0.4)); }
  POST.U.uImpact.value = imp; POST.U.uFlash.value = fl;
  POST.U.uAberr.value = sh2 * 4 + imp * 1.5;
  POST.U.uSat.value = 1.12; POST.U.uVig.value = ENVS[env].vig + (b.alarm ? 0.25 : 0); POST.U.uWarm.value = ENVS[env].warm || 0; POST.U.uSepia.value = 0;
  POST.render();
  // punch-in: a snap zoom on every cut, then a slow push for the rest of the beat
  // and a smaller snap on every jump cut inside the beat, alternating a tight and a wide framing
  const st = seg && !seg.first ? S.t - seg.a : 1, snap = REDUCED ? 0 : Math.max(Math.max(0, 1 - lt / 0.18) * 0.06, Math.max(0, 1 - st / 0.15) * 0.045), push = REDUCED ? 0 : 0.035 * clamp(lt / b.dur);
  canvas.style.transform = 'scale(' + ((b.zoom || 1) + (seg && !REDUCED ? seg.punch : 0) + snap + push).toFixed(4) + ')';
  hud(sh, b, lt);
}
let last = performance.now();
function frame(ms) {
  if (EXPORTING) return;
  const dt = clamp((ms - last) / 1000, 0, 0.05); last = Math.max(last, ms);
  const sh = SHORTS[S.cur];
  if (S.playing) {
    // with sound on, the audio clock leads so voice and picture never drift apart
    if (AUD.src) S.t = (AUD.startT + (AUD.ctx.currentTime - AUD.startCtx)) % sh.total;
    else { S.t += dt; if (S.t >= sh.total) S.t = 0; }
  }
  draw(ms / 1000);
  requestAnimationFrame(frame);
}
// a hook for rendering the Shorts to video files offline, one exact frame at a time
window.CUBE_EXPORT = {
  load(k, lang) { EXPORTING = true; S.playing = false; stopSound(); setLang(lang); pick(k); S.host = true; S.caps = true; resize(); return SHORTS[k].total; },
  frame(t) { S.t = t; draw(8 + t); },
  // try-out switches: the PS1 render (pixel size) and Mr. News's low-poly feathers
  look(o) { if (o.ps1 != null) POST.setPS1(o.ps1); if (o.poly != null && anchor.polyBits) anchor.polyBits.forEach(g => { g.visible = o.poly; }); },
  async jingleWav(sr) { return wavBase64(await renderJingle(sr || 48000, BRAND_VOICE)); },
  async wav(sr) { if (typeof renderMix !== 'function') throw new Error('mixing needs the local build'); const buf = await renderMix(SHORTS[S.cur], S.lang, sr || 48000); return wavBase64(buf); }
};
labelPicker(); pick(0);
requestAnimationFrame(frame);
})();
</script>
