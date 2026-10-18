// ---------- the casting stage: six candidates on podiums; pick one to see it up close and hear it ----------
(function setEnv() { scene.background = SKY.studio; scene.fog.color.copy(lin(0x0a1230)); scene.fog.near = 30; scene.fog.far = 140; SCENE_TINT.value.set(1, 1, 1); POST.U.uBloom.value = 0.5; })();
camera.far = 250;
const STAGE_G = newGroup();
const GAP = 1.0, SPOTS = MASCOTS.map((m, i) => V((i - (MASCOTS.length - 1) / 2) * GAP, 0.2, 0));
(function set() {
  const B = makeBatch(), navy = mat(0x13204a), rim = glow(0xff6a1f, 0.35);
  B.box(9, 0.2, 3, mat(0x0c1636), [0, -0.1, 0]);
  SPOTS.forEach(p => { B.box(0.8, 0.2, 0.8, navy, [p.x, 0.1, 0]); B.box(0.84, 0.04, 0.84, rim, [p.x, 0.21, 0]); });
  B.flush(STAGE_G);
  const rays = canvasTex(256, 256, (c, w, h) => {
    const cx = w / 2, cy = h / 2; c.fillStyle = '#13204a'; c.fillRect(0, 0, w, h); c.fillStyle = '#1d2d63';
    for (let i = 0; i < 24; i++) { c.beginPath(); c.moveTo(cx, cy); c.arc(cx, cy, w, i / 24 * TAU, (i + 0.5) / 24 * TAU); c.closePath(); c.fill(); }
    const gr = c.createRadialGradient(cx, cy, 0, cx, cy, w * 0.5); gr.addColorStop(0, 'rgba(255,140,60,.45)'); gr.addColorStop(1, 'rgba(255,106,31,0)'); c.fillStyle = gr; c.fillRect(0, 0, w, h);
  });
  const back = new THREE.Mesh(new THREE.CircleGeometry(12, 64), new THREE.MeshBasicMaterial({ map: rays, depthWrite: false })); back.position.set(0, 1, -3.5); STAGE_G.add(back); STAGE_G.userData.back = back;
})();
const CAST = MASCOTS.map(m => { const c = makeChar('m_' + m.id, m.def, 0.62); world.add(c.root); CHARS.push(c); c.blinkNext = 1 + Math.random() * 3; return c; });

// ---------- controls ----------
const S = { sel: -1, view: 'all', expr: 'serio', lang: 'es', say: null, yaw: 0 };
function seg(id, key, after) {
  const box = $(id);
  box.querySelectorAll('button').forEach(b => b.addEventListener('click', () => { S[key] = b.dataset.v; box.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b)); if (after) after(); }));
}
seg('exprSeg', 'expr'); seg('langSeg', 'lang'); seg('viewSeg', 'view', () => { if (S.view === 'all') select(-1); else if (S.sel < 0) select(0, S.view); });
const ICON = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 2.5v11l9-5.5z"/></svg>';
const tagBtns = MASCOTS.map((m, i) => { const b = document.createElement('button'); b.textContent = m.name; b.addEventListener('click', () => { select(i); speak(i); }); $('tags').appendChild(b); return b; });
const cards = MASCOTS.map((m, i) => {
  const li = document.createElement('li'); li.className = 'cand';
  li.innerHTML = '<h3></h3><span class="acts"><button class="btn see">Ver</button><button class="btn primary hear">' + ICON + 'Oír</button></span><p></p>';
  li.querySelector('h3').textContent = m.name; li.querySelector('p').textContent = m.pitch;
  li.querySelector('.see').addEventListener('click', () => select(i));
  li.querySelector('.hear').addEventListener('click', () => { select(i); speak(i); });
  $('cands').appendChild(li); return li;
});
function select(i, view) {
  S.sel = i; stage.classList.toggle('focus', i >= 0);
  S.view = i < 0 ? 'all' : view || (S.view === 'spin' ? 'spin' : 'front');
  $('viewSeg').querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x.dataset.v === S.view));
  tagBtns.forEach((b, j) => b.setAttribute('aria-current', j === i)); cards.forEach((c, j) => c.setAttribute('aria-current', j === i)); S.yaw = 0;
}
const AUD = { ctx: null, bufs: {}, src: null };
async function speak(i) {
  if (!AUD.ctx) { const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return; AUD.ctx = new AC(); }
  AUD.ctx.resume();
  const m = MASCOTS[i], key = m.id + '/' + S.lang;
  try { if (!AUD.bufs[key]) AUD.bufs[key] = await decodeB64(AUD.ctx, MASCOT_VOICE[m.id][S.lang]); } catch (e) { return; }
  if (AUD.src) { try { AUD.src.stop(); } catch (e) { /* already ended */ } }
  const s = AUD.ctx.createBufferSource(); s.buffer = AUD.bufs[key]; s.connect(AUD.ctx.destination); s.start();
  AUD.src = s; S.say = { i, t0: performance.now() / 1000, dur: s.buffer.duration };
  $('say').textContent = m.name + ': «' + MASCOT_LINE[S.lang] + '»'; $('say').classList.add('on');
  s.onended = () => { if (AUD.src === s) { AUD.src = null; $('say').classList.remove('on'); } };
}

function resize() {
  const w = stage.clientWidth, h = stage.clientHeight; if (!w || !h) return;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2)); renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
}
new ResizeObserver(resize).observe(stage); resize();
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => FONT_REDRAW.forEach(f => f()));

const _p = V(0, 0, 0), camPos = V(0, 1.1, 7.6), camLook = V(0, 0.95, 0);
let last = performance.now();
scene.autoUpdate = false;
function frame(ms) {
  const now = ms / 1000, dt = clamp((ms - last) / 1000, 0, 0.05); last = Math.max(last, ms);
  for (const g of GROUPS) g.visible = false; for (const c of CHARS) c.root.visible = false; ACTIVE.length = 0; EMIT.length = 0;
  STAGE_G.visible = true; STAGE_G.userData.back.rotation.z = -now * 0.06;
  if (S.view === 'spin' && !REDUCED) S.yaw += dt * 0.7; else S.yaw += (0 - (((S.yaw + PI) % TAU + TAU) % TAU - PI)) * Math.min(1, dt * 5);
  const talking = S.say && now - S.say.t0 < S.say.dur;
  CAST.forEach((c, i) => {
    if (S.sel >= 0 && i !== S.sel) return;
    const me = talking && S.say.i === i; c.speaking = me;
    const pose = me ? 'talk' : i === S.sel ? 'present' : 'idle';
    stand(c, SPOTS[i], i === S.sel ? S.yaw : 0, pose, me ? 'feliz' : S.expr, now + i * 0.7);
  });
  for (const ch of ACTIVE) updateChar(ch, now);
  // the camera glides between the lineup and the chosen candidate
  const tp = S.sel >= 0 ? V(SPOTS[S.sel].x, 1.08, 2.55) : V(0, 1.0, 7.4), tl = S.sel >= 0 ? V(SPOTS[S.sel].x, 0.98, 0) : V(0, 0.88, 0);
  const k = Math.min(1, dt * 4); camPos.lerp(tp, k); camLook.lerp(tl, k);
  camera.position.copy(camPos); camera.fov = S.sel >= 0 ? 32 : 28; camera.updateProjectionMatrix(); camera.up.copy(UP); camera.lookAt(camLook);
  aimSun(camLook, 6); scene.updateMatrixWorld();
  PART.begin(); PART.end();
  POST.U.uImpact.value = 0; POST.U.uFlash.value = 0; POST.U.uAberr.value = 0; POST.U.uSat.value = 1.12; POST.U.uVig.value = 0.3; POST.U.uWarm.value = 0; POST.U.uSepia.value = 0;
  POST.render();
  // name tags follow each podium on screen
  const w = stage.clientWidth, h = stage.clientHeight;
  SPOTS.forEach((p, i) => { _p.set(p.x, 0.05, 0.45).project(camera); tagBtns[i].style.left = ((_p.x + 1) / 2 * w).toFixed(1) + 'px'; tagBtns[i].style.top = ((1 - _p.y) / 2 * h).toFixed(1) + 'px'; });
  requestAnimationFrame(frame);
}
TRAILS.draw([], 0);
for (const c of CHARS) if (!CAST.includes(c)) c.root.visible = false;
requestAnimationFrame(frame);
})();
</script>
