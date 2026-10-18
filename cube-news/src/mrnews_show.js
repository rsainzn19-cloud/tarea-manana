// ---------- the character sheet: Mr. News on a turntable, with his faces, poses and voice ----------
const ENVS = { studio: { bg: 'studio', fog: 0x0a1230, near: 30, far: 140, tint: [1, 1, 1], bloom: 0.55, vig: 0.3 } };
camera.far = 250;
(function setEnv() { const e = ENVS.studio; scene.background = SKY[e.bg]; scene.fog.color.copy(lin(e.fog)); scene.fog.near = e.near; scene.fog.far = e.far; SCENE_TINT.value.set(...e.tint); POST.U.uBloom.value = e.bloom; })();
const SHOW = newGroup();
(function showcase() {
  // a round blocky platform with a glowing rim, rays behind and the Earth cube floating over his shoulder
  const B = makeBatch(), navy = mat(0x13204a), rim = glow(0xff6a1f, 0.9);
  for (let x = -6; x <= 6; x++) for (let z = -6; z <= 6; z++) { const r = Math.hypot(x, z); if (r > 6.3) continue; B.box(0.2, 0.2, 0.2, r > 5.4 ? rim : navy, [x * 0.2, -0.1, z * 0.2]); }
  B.box(4, 0.2, 4, mat(0x0c1636), [0, -0.3, 0]); B.flush(SHOW);
  const rays = canvasTex(256, 256, (c, w, h) => {
    const cx = w / 2, cy = h / 2; c.fillStyle = '#13204a'; c.fillRect(0, 0, w, h); c.fillStyle = '#1d2d63';
    for (let i = 0; i < 18; i++) { c.beginPath(); c.moveTo(cx, cy); c.arc(cx, cy, w, i / 18 * TAU, (i + 0.5) / 18 * TAU); c.closePath(); c.fill(); }
    const gr = c.createRadialGradient(cx, cy, 0, cx, cy, w * 0.5); gr.addColorStop(0, 'rgba(255,140,60,.55)'); gr.addColorStop(0.45, 'rgba(255,106,31,.12)'); gr.addColorStop(1, 'rgba(255,106,31,0)'); c.fillStyle = gr; c.fillRect(0, 0, w, h);
  });
  const back = new THREE.Mesh(new THREE.CircleGeometry(9, 64), new THREE.MeshBasicMaterial({ map: rays, depthWrite: false })); back.position.set(0, 1.0, -3.5); SHOW.add(back); SHOW.userData.back = back;
  const mats = ['right', 'left', 'top', 'bottom', 'front', 'back'].map(k => { const t = new THREE.CanvasTexture(EARTH_CANVAS[k]); t.encoding = THREE.sRGBEncoding; t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.generateMipmaps = false; return cel({ map: t, emissiveMap: t, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.2) }); });
  const earth = new THREE.Mesh(new THREE.BoxGeometry(0.42, 0.42, 0.42), mats); earth.position.set(0.85, 1.75, -0.9); SHOW.add(earth); SHOW.userData.earth = earth;
})();
anchor.blinkNext = 2;

// ---------- controls ----------
const S = { expr: 'feliz', pose: 'present', cam: REDUCED ? 'front' : 'spin', lang: 'es', yaw: 0, say: null };
function seg(id, key, after) {
  const box = $(id);
  box.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
    S[key] = b.dataset.v; box.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b)); if (after) after();
  }));
}
seg('exprSeg', 'expr'); seg('poseSeg', 'pose'); seg('camSeg', 'cam'); seg('langSeg', 'lang', labelPhrases);
const ICON = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 2.5v11l9-5.5z"/></svg>';
const phrBtns = PHRASES.map(p => {
  const b = document.createElement('button'); b.setAttribute('aria-pressed', 'false');
  b.innerHTML = '<span class="ic">' + ICON + '</span><span><span class="tx"></span><small></small></span>';
  b.addEventListener('click', () => say(p, b)); $('phrases').appendChild(b); return b;
});
const ROLE = { hola: 'Saludo', bloque: 'Entre noticias', adios: 'Despedida' };
function labelPhrases() { PHRASES.forEach((p, k) => { phrBtns[k].querySelector('.tx').textContent = p[S.lang]; phrBtns[k].querySelector('small').textContent = ROLE[p.id]; }); }
labelPhrases();
// voice: each phrase plays once; while it plays he talks, and the sign-off ends on a wink
const AUD = { ctx: null, bufs: {}, src: null };
async function say(p, btn) {
  if (!AUD.ctx) { const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return; AUD.ctx = new AC(); }
  AUD.ctx.resume();
  const key = p.id + '/' + S.lang;
  try { if (!AUD.bufs[key]) AUD.bufs[key] = await decodeB64(AUD.ctx, p.clip[S.lang]); } catch (e) { return; }
  if (AUD.src) { try { AUD.src.stop(); } catch (e) { /* already ended */ } }
  const buf = AUD.bufs[key], s = AUD.ctx.createBufferSource(); s.buffer = buf; s.connect(AUD.ctx.destination); s.start();
  AUD.src = s; S.say = { id: p.id, t0: performance.now() / 1000, dur: buf.duration };
  phrBtns.forEach(b => b.setAttribute('aria-pressed', b === btn));
  s.onended = () => { if (AUD.src === s) { AUD.src = null; btn.setAttribute('aria-pressed', 'false'); } };
}

function resize() {
  const w = stage.clientWidth, h = stage.clientHeight; if (!w || !h) return;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe(stage); resize();
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => FONT_REDRAW.forEach(f => f()));

const YAW = { front: 0, side: PI / 2, close: 0.15 };
let last = performance.now();
scene.autoUpdate = false;
function frame(ms) {
  const now = ms / 1000, dt = clamp((ms - last) / 1000, 0, 0.05); last = Math.max(last, ms);
  for (const g of GROUPS) g.visible = false; for (const c of CHARS) c.root.visible = false; ACTIVE.length = 0; EMIT.length = 0;
  SHOW.visible = true;
  // the turntable eases to the chosen angle, or keeps turning
  if (S.cam === 'spin') S.yaw += dt * 0.6; else { const target = YAW[S.cam]; let d = ((target - S.yaw) % TAU + TAU * 1.5) % TAU - PI; S.yaw += d * Math.min(1, dt * 6); }
  const talking = S.say && now - S.say.t0 < S.say.dur, ending = S.say && S.say.id === 'adios' && now - S.say.t0 > S.say.dur - 0.7 && now - S.say.t0 < S.say.dur + 1.5;
  anchor.speaking = !!talking;
  const pose = talking ? (S.say.id === 'adios' ? 'point' : S.say.id === 'hola' ? 'wave' : 'present') : S.pose;
  const expr = ending ? 'guino' : talking ? 'feliz' : S.expr;
  stand(anchor, V(0, 0, 0), S.yaw, pose, expr, now);
  const e = SHOW.userData.earth; e.rotation.set(0.4, now * 0.8, 0); e.position.y = 1.75 + Math.sin(now * 1.6) * 0.05;
  SHOW.userData.back.rotation.z = -now * 0.08;
  for (const ch of ACTIVE) updateChar(ch, now);
  const close = S.cam === 'close';
  const c = close ? shot(V(0, 1.1, 1.85), V(0, 1.16, 0), 34, V(0, 0, 0), 4) : shot(V(0, 0.95, 3.9), V(0, 0.78, 0), 32, V(0, 0, 0), 4);
  camera.position.copy(c.p); camera.fov = c.f; camera.updateProjectionMatrix(); camera.up.copy(UP); camera.lookAt(c.l);
  aimSun(c.focus, c.box); scene.updateMatrixWorld();
  PART.begin(); PART.end();
  POST.U.uImpact.value = 0; POST.U.uFlash.value = 0; POST.U.uAberr.value = 0; POST.U.uSat.value = 1.12; POST.U.uVig.value = 0.3; POST.U.uWarm.value = 0; POST.U.uSepia.value = 0;
  POST.render();
  stage.classList.toggle('closeup', close);
  $('role').textContent = talking ? '«' + PHRASES.find(p => p.id === S.say.id)[S.lang] + '»' : 'Locutor';
  requestAnimationFrame(frame);
}
TRAILS.draw([], 0);
requestAnimationFrame(frame);
})();
</script>
