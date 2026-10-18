// ---------- sound: original music, effects and the narration, mixed offline so the preview and the export match ----------
const MUSIC = {
  piloto: { bpm: 126, root: 146.83, prog: [[0, 'm'], [8, 'M'], [3, 'M'], [10, 'M']], mood: 'tense' },
  dolar: { bpm: 112, root: 220.0, prog: [[0, 'm'], [8, 'M'], [3, 'M'], [10, 'M']], mood: 'drive' },
  papa: { bpm: 96, root: 174.61, prog: [[0, 'M'], [7, 'M'], [9, 'm'], [5, 'M']], mood: 'wonder' }
};
const VOICE_BUF = {};
function voiceBuffers(ctx, lang, id) {
  const key = lang + '/' + id;
  if (!VOICE_BUF[key]) VOICE_BUF[key] = Promise.all(VOICE[lang][id].map(v => ctx.decodeAudioData(b64buf(v.src))));
  return VOICE_BUF[key];
}
function music(M, sh) {
  const P = MUSIC[sh.id], spb = 60 / P.bpm, bar = spb * 4, end = sh.total, q = sh.beats[sh.beats.length - 1].a, soft = P.mood === 'wonder';
  for (let i = 0; i * bar < end; i++) {
    const t0 = i * bar, [semi, qual] = P.prog[i % P.prog.length], triad = (qual === 'm' ? [0, 3, 7] : [0, 4, 7]).map(x => x + semi);
    const inQ = t0 >= q - 0.01, len = Math.min(bar, end - t0) + 0.25;
    triad.forEach((s, k) => { for (const det of [-8, 8]) tone(M, M.music, 'sawtooth', fr(P.root, s), t0, len, soft ? 0.03 : 0.024, { a: 0.3, hold: 0.7, lp: soft ? 1400 : 1000, detune: det, pan: (k - 1) * 0.45 }); });
    for (let e = 0; e < 8; e++) {
      const t = t0 + e * spb / 2; if (t >= Math.min(end, q)) break;
      if (soft && e % 2) continue;
      tone(M, M.music, 'sawtooth', fr(P.root / 2, semi), t, spb * (soft ? 0.9 : 0.45), e % 2 ? 0.09 : 0.13, { lp: 330, q: 3 });
    }
    if (inQ) continue;
    for (let s = 0; s < 16; s++) {
      const t = t0 + s * spb / 4; if (t >= q) break;
      if (s % 4 === 0 && (P.mood === 'tense' || s % 8 === 0)) kick(M, t, soft ? 0.5 : 0.85);
      if (i > 0 && (s === 4 || s === 12)) snare(M, t, soft ? 0.12 : 0.3);
      if (!soft || s % 2 === 0) hat(M, t, s % 4 === 2 ? 0.1 : 0.05, s % 2 ? 0.3 : -0.3);
      const n = triad[[0, 1, 2, 1][s % 4]] + (soft ? 24 : 12);
      if (soft) { if (s % 2 === 0) tone(M, M.music, 'sine', fr(P.root, n), t, spb * 0.6, 0.045, { pan: s % 4 ? 0.4 : -0.4 }); }
      else tone(M, M.music, 'square', fr(P.root, n), t, spb * 0.2, 0.018, { lp: 2400, pan: s % 2 ? 0.35 : -0.35 });
    }
  }
  // an opening hit, a riser into the closing question and a bell when the question lands
  kick(M, 0.0, 1); hiss(M, M.music, 0.0, 1.4, 0.22, 'highpass', 5000);
  hiss(M, M.music, Math.max(0, q - 1.3), 1.3, 0.12, 'bandpass', 400, { f2: 6000, a: 1.1, q: 1.2 });
  const last = sh.beats[sh.beats.length - 1];
  bell(M, M.music, last.vb + 0.12, fr(P.root, P.mood === 'wonder' ? 24 : 12), 0.12, 2.0); bell(M, M.music, last.vb + 0.2, fr(P.root, (P.mood === 'wonder' ? 28 : 15) + 12), 0.08, 1.8);
}

// effects tied to what is on screen in each beat
const tOfU = (b, u) => b.view.u ? b.a + clamp((u - b.view.u[0]) / (b.view.u[1] - b.view.u[0])) * b.dur : b.a;
const SFX = {
  // Mr. News's signature: a penguin's two-note honk after his sign-off
  squawk: (M, t) => { [[0, 560, 470], [0.24, 640, 520]].forEach(([dt, f1, f2]) => { tone(M, M.sfx, 'sawtooth', f1, t + dt, 0.2, 0.14, { f2, glide: 0.18, lp: 1500, q: 4 }); tone(M, M.sfx, 'square', f1 * 2, t + dt, 0.12, 0.03, { f2: f2 * 2, lp: 2200 }); hiss(M, M.sfx, t + dt, 0.08, 0.04, 'bandpass', 1300, { q: 2 }); }); },
  whoosh: (M, t) => hiss(M, M.sfx, t, 0.34, 0.4, 'bandpass', 300, { f2: 4200, a: 0.18, q: 1.4 }),
  pop: (M, t) => { tone(M, M.sfx, 'sine', 1300, t, 0.1, 0.28, { f2: 520 }); tone(M, M.sfx, 'triangle', 2600, t, 0.03, 0.06); },
  tick: (M, b) => { hiss(M, M.sfx, b.a, 0.03, 0.25, 'highpass', 4000); tone(M, M.sfx, 'sine', 1800, b.a, 0.06, 0.08); },
  alarm: (M, b) => { for (let t = b.a + 0.08; t < b.b - 0.12; t += 0.44) { tone(M, M.sfx, 'square', 988, t, 0.15, 0.045, { lp: 2400 }); tone(M, M.sfx, 'square', 740, t + 0.2, 0.15, 0.045, { lp: 2400 }); } },
  boom: (M, b, u) => { const t = tOfU(b, u); tone(M, M.sfx, 'sine', 95, t, 1.1, 0.9, { f2: 30 }); hiss(M, M.sfx, t, 1.3, 0.45, 'lowpass', 500, { f2: 70 }); },
  jet: (M, b, v) => { hiss(M, M.sfx, b.a, b.dur + 0.15, v * 0.5, 'lowpass', 520, { a: 0.25, hold: 0.8 }); hiss(M, M.sfx, b.a, b.dur + 0.15, v * 0.07, 'bandpass', 2300, { a: 0.3, hold: 0.8, q: 4 }); },
  touch: (M, b, u) => { const t = tOfU(b, u); tone(M, M.sfx, 'sine', 80, t, 0.5, 0.5, { f2: 38 }); hiss(M, M.sfx, t + 0.03, 0.55, 0.09, 'bandpass', 2600, { q: 6 }); },
  siren: (M, b) => { const o = M.oc.createOscillator(), g = M.oc.createGain(); o.type = 'triangle'; o.frequency.setValueAtTime(780, b.a); for (let t = b.a, k = 0; t < b.b; t += 0.5, k++) o.frequency.linearRampToValueAtTime(k % 2 ? 640 : 920, t + 0.5); g.gain.setValueAtTime(0.0001, b.a); g.gain.exponentialRampToValueAtTime(0.03, b.a + 0.4); g.gain.setValueAtTime(0.03, b.b - 0.3); g.gain.exponentialRampToValueAtTime(0.0001, b.b); o.connect(g); route(M, g, M.sfx, 0.5); o.start(b.a); o.stop(b.b + 0.05); },
  coins: (M, b, u0, u1) => { const t0 = tOfU(b, u0), t1 = tOfU(b, u1); for (let t = t0; t < t1; t += 0.07 + M.r() * 0.07) { const f = 2400 + M.r() * 2600; tone(M, M.sfx, 'sine', f, t, 0.12, 0.05, { pan: M.r() - 0.5 }); tone(M, M.sfx, 'sine', f * 1.5, t, 0.07, 0.025); } },
  kaching: (M, b) => { const t = b.a + (b.card ? b.card.at : 0.2); hiss(M, M.sfx, t, 0.07, 0.25, 'bandpass', 3200); bell(M, M.sfx, t + 0.02, 1318.5, 0.1, 0.6); bell(M, M.sfx, t + 0.1, 1760, 0.1, 0.8); },
  horn: (M, b, k) => { const t = b.a + b.dur * k; tone(M, M.sfx, 'sawtooth', 98, t, 1.7, 0.16, { a: 0.15, hold: 0.7, lp: 520 }); tone(M, M.sfx, 'sawtooth', 146.8, t, 1.7, 0.08, { a: 0.15, hold: 0.7, lp: 520 }); },
  water: (M, b) => hiss(M, M.sfx, b.a, b.dur + 0.2, 0.08, 'bandpass', 520, { a: 0.3, hold: 0.8, q: 0.5 }),
  rocket: (M, b, u) => { const t = tOfU(b, u); tone(M, M.sfx, 'sine', 70, t, 1.4, 0.8, { f2: 28 }); hiss(M, M.sfx, t, Math.max(1, b.b - t + 0.3), 0.5, 'lowpass', 380, { a: 0.4, hold: 0.6 }); hiss(M, M.sfx, t, Math.max(1, b.b - t + 0.3), 0.07, 'bandpass', 1500, { a: 0.5, hold: 0.6 }); },
  heart: (M, b) => { for (let t = b.a + 0.15; t < b.b - 0.2; t += 0.86) { tone(M, M.sfx, 'sine', 64, t, 0.17, 0.55, { f2: 44 }); tone(M, M.sfx, 'sine', 60, t + 0.24, 0.15, 0.38, { f2: 42 }); } },
  shimmer: (M, b) => [1568, 2093, 2637].forEach((f, k) => tone(M, M.sfx, 'sine', f, b.a + k * 0.15, b.dur, 0.012, { a: 0.6, hold: 0.7, pan: (k - 1) * 0.6 })),
  clunk: (M, b, u) => { const t = tOfU(b, u); hiss(M, M.sfx, t, 0.25, 0.4, 'bandpass', 700, { q: 2 }); tone(M, M.sfx, 'square', 120, t, 0.15, 0.12, { lp: 900 }); bell(M, M.sfx, t + 0.12, 1046.5, 0.07, 1.2); bell(M, M.sfx, t + 0.2, 1318.5, 0.05, 1.2); }
};
function effects(M, sh) {
  const last = sh.beats[sh.beats.length - 1]; SFX.squawk(M, last.vb + 0.1);
  sh.beats.forEach((b, i) => {
    if (i) SFX.whoosh(M, Math.max(0, b.a - 0.14));
    if (b.card) SFX.pop(M, b.a + b.card.at);
    for (const [k, ...args] of b.sfx || []) SFX[k](M, b, ...args);
  });
}
// the music ducks under every spoken line
function duckAndVoice(M, sh, bufs) {
  // short gaps between lines stay ducked so the bed does not pump
  const d = M.duck.gain, B = sh.beats, LOW = 0.36; d.setValueAtTime(1, 0);
  B.forEach((b, i) => {
    if (!i || b.va - B[i - 1].vb > 0.6) { d.setValueAtTime(1, Math.max(0, b.va - 0.12)); d.linearRampToValueAtTime(LOW, b.va); }
    if (i === B.length - 1 || B[i + 1].va - b.vb > 0.6) { d.setValueAtTime(LOW, b.vb); d.linearRampToValueAtTime(1, b.vb + 0.25); }
    const s = M.oc.createBufferSource(); s.buffer = bufs[i]; s.connect(M.voice); s.start(b.va);
  });
}
async function renderMix(sh, lang, sr) {
  const oc = new OfflineAudioContext(2, Math.ceil((sh.total + 0.25) * sr), sr);
  const bufs = await voiceBuffers(oc, lang, sh.id), M = mixer(oc);
  music(M, sh); effects(M, sh); duckAndVoice(M, sh, bufs);
  // the music steps aside while the jingle plays after the hook
  if (sh.sting) {
    const m = M.mute.gain, s = sh.sting; m.setValueAtTime(1, 0); m.setValueAtTime(1, Math.max(0, s.a - 0.06)); m.linearRampToValueAtTime(0, s.a + 0.04);
    m.setValueAtTime(0, s.b - 0.25); m.linearRampToValueAtTime(1, s.b + 0.35);
    jingle(M, s.a, await decodeB64(oc, BRAND_VOICE));
  }
  return oc.startRendering();
}
