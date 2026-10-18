// ---------- sound building blocks shared by the pages: synth voices, a small mixer and the channel jingle ----------
const b64buf = s => { const bin = atob(s), u = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i); return u.buffer; };
const fr = (root, semi) => root * Math.pow(2, semi / 12);
function mixer(oc) {
  const comp = oc.createDynamicsCompressor();
  comp.threshold.value = -16; comp.knee.value = 8; comp.ratio.value = 3.5; comp.attack.value = 0.004; comp.release.value = 0.2;
  const master = oc.createGain(); master.gain.value = 0.92; comp.connect(master); master.connect(oc.destination);
  const music = oc.createGain(); music.gain.value = 0.5; const mute = oc.createGain(), duck = oc.createGain(); music.connect(mute); mute.connect(duck); duck.connect(comp);
  const sfx = oc.createGain(); sfx.gain.value = 0.6; sfx.connect(comp);
  const hp = oc.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 85; const vo = oc.createGain(); vo.gain.value = 1.05; hp.connect(vo); vo.connect(comp);
  const noise = oc.createBuffer(1, oc.sampleRate * 2, oc.sampleRate), nd = noise.getChannelData(0), r = rng(4242);
  for (let i = 0; i < nd.length; i++) nd[i] = r() * 2 - 1;
  return { oc, music, mute, duck, sfx, voice: hp, noise, r: rng(77) };
}
const route = (M, node, dest, pan) => { if (pan == null || !M.oc.createStereoPanner) { node.connect(dest); return; } const p = M.oc.createStereoPanner(); p.pan.value = pan; node.connect(p); p.connect(dest); };
// one oscillator note with an attack, an optional hold and an exponential release
function tone(M, dest, type, f, t, dur, peak, o) {
  o = o || {}; if (t < 0 || dur <= 0) return;
  const osc = M.oc.createOscillator(); osc.type = type; osc.frequency.setValueAtTime(f, t);
  if (o.f2) osc.frequency.exponentialRampToValueAtTime(o.f2, t + (o.glide || dur));
  if (o.detune) osc.detune.value = o.detune;
  const g = M.oc.createGain(), a = o.a || 0.005, hold = t + Math.max(a, dur * (o.hold || 0));
  g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(peak, t + a);
  if (hold > t + a) g.gain.setValueAtTime(peak, hold);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  let node = osc;
  if (o.lp) { const fl = M.oc.createBiquadFilter(); fl.type = 'lowpass'; fl.frequency.value = o.lp; fl.Q.value = o.q || 0.7; osc.connect(fl); node = fl; }
  node.connect(g); route(M, g, dest, o.pan);
  osc.start(t); osc.stop(t + dur + 0.05);
}
// filtered noise: hats, whooshes, rumbles, water
function hiss(M, dest, t, dur, peak, type, freq, o) {
  o = o || {}; if (t < 0 || dur <= 0) return;
  const s = M.oc.createBufferSource(); s.buffer = M.noise; s.loop = true;
  const fl = M.oc.createBiquadFilter(); fl.type = type; fl.frequency.setValueAtTime(freq, t); fl.Q.value = o.q || 0.7;
  if (o.f2) fl.frequency.exponentialRampToValueAtTime(o.f2, t + dur);
  const g = M.oc.createGain(), a = o.a || 0.002, hold = t + Math.max(a, dur * (o.hold || 0));
  g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(peak, t + a);
  if (hold > t + a) g.gain.setValueAtTime(peak, hold);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(fl); fl.connect(g); route(M, g, dest, o.pan);
  s.start(t, M.r() * 1.5); s.stop(t + dur + 0.05);
}
const kick = (M, t, v) => { tone(M, M.music, 'sine', 150, t, 0.34, v, { f2: 42, glide: 0.12 }); hiss(M, M.music, t, 0.02, v * 0.25, 'highpass', 3000); };
const snare = (M, t, v) => { hiss(M, M.music, t, 0.16, v, 'bandpass', 1900, { q: 0.8 }); tone(M, M.music, 'triangle', 190, t, 0.08, v * 0.5); };
const hat = (M, t, v, pan) => hiss(M, M.music, t, 0.045, v, 'highpass', 7600, { pan });
const bell = (M, dest, t, f, v, dur) => { tone(M, dest, 'sine', f, t, dur || 1.2, v); tone(M, dest, 'sine', f * 2.76, t, (dur || 1.2) * 0.5, v * 0.3); };


// the Cube News jingle (original): a fall, a thump, a bouncy marimba line, a brassy "ta-da" and the name
const mf = m => 440 * Math.pow(2, (m - 69) / 12);
function jingle(M, t0, brand) {
  // 0–0.45 the fall, 0.45 the thump, a marimba line while the letters pop, then Mr. News says the name over a soft pad
  // and the brassy "ta-da" lands right on "News!"
  const mar = (t, m, v) => { const f = mf(m); tone(M, M.sfx, 'sine', f, t, 0.5, v); tone(M, M.sfx, 'sine', f * 4, t, 0.07, v * 0.3); tone(M, M.sfx, 'triangle', f * 2, t, 0.16, v * 0.18); };
  hiss(M, M.sfx, t0, 0.45, 0.3, 'bandpass', 3800, { f2: 380, a: 0.32, q: 1.3 });
  tone(M, M.sfx, 'sine', 125, t0 + 0.45, 0.55, 0.95, { f2: 52 }); hiss(M, M.sfx, t0 + 0.45, 0.22, 0.28, 'lowpass', 1400);
  [[0.55, 76], [0.70, 79], [0.85, 84], [1.0, 81], [1.15, 88]].forEach(([t, m]) => mar(t0 + t, m, 0.22));
  [[0.55, 48], [0.85, 43], [1.15, 41]].forEach(([t, m]) => tone(M, M.sfx, 'triangle', mf(m), t0 + t, 0.26, 0.38, { lp: 900 }));
  for (let i = 0; i < 8; i++) tone(M, M.sfx, 'sine', mf(96 + [0, 2, 4, 5, 7, 9, 11, 12][i]), t0 + 0.85 + i * 0.07, 0.08, 0.035, { pan: (i - 3.5) * 0.12 });
  [55, 60, 64].forEach(m => tone(M, M.sfx, 'sine', mf(m), t0 + 1.25, 1.35, 0.045, { a: 0.15, hold: 0.8 }));
  const land = t0 + (brand ? 1.25 + brand.duration + 0.03 : 1.75);
  [60, 64, 67, 72, 76].forEach((m, k) => { for (const det of [-7, 7]) tone(M, M.sfx, 'sawtooth', mf(m), land, 0.7, 0.035, { a: 0.02, hold: 0.45, lp: 2400, detune: det, pan: (k - 2) * 0.25 }); });
  tone(M, M.sfx, 'triangle', mf(36), land, 0.9, 0.38, { lp: 900 }); mar(land, 84, 0.2);
  hiss(M, M.sfx, land - 0.03, 1.2, 0.12, 'highpass', 6500, { a: 0.04 });
  [88, 91, 96, 100].forEach((m, k) => bell(M, M.sfx, land + 0.2 + k * 0.07, mf(m), 0.045, 0.9));
  if (brand) { const s = M.oc.createBufferSource(), g = M.oc.createGain(); g.gain.value = 1.25; s.buffer = brand; s.connect(g); g.connect(M.voice); s.start(t0 + 1.25); }
}
function decodeB64(ctx, b64) { return ctx.decodeAudioData(b64buf(b64)); }
async function renderJingle(sr, brandB64) {
  const oc = new OfflineAudioContext(2, Math.ceil(4.6 * sr), sr), M = mixer(oc);
  jingle(M, 0, brandB64 ? await decodeB64(oc, brandB64) : null);
  return oc.startRendering();
}
function wavBase64(buf) {
  const ch = buf.numberOfChannels, n = buf.length, out = new DataView(new ArrayBuffer(44 + n * ch * 2)), w = (o, s) => { for (let i = 0; i < s.length; i++) out.setUint8(o + i, s.charCodeAt(i)); };
  w(0, 'RIFF'); out.setUint32(4, 36 + n * ch * 2, true); w(8, 'WAVEfmt '); out.setUint32(16, 16, true); out.setUint16(20, 1, true); out.setUint16(22, ch, true);
  out.setUint32(24, buf.sampleRate, true); out.setUint32(28, buf.sampleRate * ch * 2, true); out.setUint16(32, ch * 2, true); out.setUint16(34, 16, true); w(36, 'data'); out.setUint32(40, n * ch * 2, true);
  const data = [...Array(ch)].map((_, c) => buf.getChannelData(c));
  for (let i = 0, o = 44; i < n; i++) for (let c = 0; c < ch; c++, o += 2) out.setInt16(o, Math.max(-1, Math.min(1, data[c][i])) * 32767, true);
  const bytes = new Uint8Array(out.buffer); let s = '';
  for (let i = 0; i < bytes.length; i += 32768) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));
  return btoa(s);
}
