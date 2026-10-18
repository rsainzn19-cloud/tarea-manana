// ---------- a still frame per thumbnail: one scene, the host's reaction and big text ----------
const ENVS = {
  studio: { bg: 'studio', fog: 0x0a1230, near: 30, far: 140, tint: [1, 1, 1], bloom: 0.6, vig: 0.3 },
  day: { bg: 'day', fog: 0xc9dcff, near: 90, far: 260, tint: [1, 1, 1], bloom: 0.35, vig: 0.3 },
  space: { bg: 'night', fog: 0x000000, near: 400, far: 900, tint: [1.05, 1.05, 1.1], bloom: 0.8, vig: 0.35 }
};
camera.far = 250;
function setEnv(k) { const e = ENVS[k]; scene.background = SKY[e.bg]; scene.fog.color.copy(lin(e.fog)); scene.fog.near = e.near; scene.fog.far = e.far; SCENE_TINT.value.set(...e.tint); POST.U.uBloom.value = e.bloom; POST.U.uVig.value = e.vig; }
anchor.root.traverse(o => { if (o.isMesh) o.castShadow = false; });
// a still frame: nobody blinks
for (const c of [anchor, astro]) { c.blinkNext = 1e9; c.blinkUntil = 0; }
const K = location.hash.slice(1) || 'piloto';
document.querySelectorAll('.ov').forEach(o => o.classList.toggle('on', o.dataset.k === K));
const _f = V(0, 0, 0), _u = V(0, 0, 0), _r = V(0, 0, 0), _p = V(0, 0, 0);
// host at a screen spot: x, y are where the top of the head lands (-1..1), size is the head height as a share of the frame
function host(x, y, size, yaw, expr) {
  const th = Math.tan(camera.fov * PI / 360), d = 0.31 / (2 * th * size);
  _f.set(0, 0, -1).applyQuaternion(camera.quaternion); _u.set(0, 1, 0).applyQuaternion(camera.quaternion); _r.set(1, 0, 0).applyQuaternion(camera.quaternion);
  _p.copy(camera.position).addScaledVector(_f, d).addScaledVector(_r, x * d * th * camera.aspect).addScaledVector(_u, y * d * th - 1.24);
  stand(anchor, _p, 0, 'idle', expr, 0.4);
  anchor.root.quaternion.copy(camera.quaternion); anchor.root.rotateY(yaw);
}
const THUMBS = {
  avatar: now => { RAYS.visible = true; stand(anchor, V(0, 0, 0), 0, 'present', 'guino', 0.4); return { env: 'studio', c: shot(V(0, 1.12, 1.62), V(0, 1.15, 0), 36, V(0, 0, 0), 4), host: null }; },
  piloto: now => { const dio = D[10], u = 7.6; dio.g.visible = true; dio.update(u, now); const s = dio.state(u);
    dio.g.children.forEach(o => { if (o.position.distanceTo(s.p) < 0.01) o.rotation.x = 0.62; if (o.isInstancedMesh && o.count === 70) o.visible = false; });
    return { env: 'day', c: shot(s.p.clone().add(V(12.5, 2.6, -0.6)), s.p.clone().add(V(0, -0.5, 3.1)), 40, s.p, 10), host: [0.7, 0.02, 0.27, -0.35, 'sorpresa'], aberr: 0.15 }; },
  dolar: now => { const dio = D[11], u = 8.6; dio.g.visible = true; dio.update(u, now);
    return { env: 'studio', c: shot(V(3.0, 1.2, 8.5), V(3.4, 3.6, 0), 52, V(0, 0, 0), 10), host: [-0.6, -0.12, 0.22, 0.4, 'sorpresa'] }; },
  papa: now => { const dio = D[9], u = 14.2; dio.g.visible = true; dio.update(u, now);
    const a = ORBIT.clone().add(V(1.7, 0.9, 1.6)); stand(astro, a, -0.5, 'float', 'serio', 0.8); astro.root.rotation.z = 0.2; astro.root.rotation.x = -0.15;
    return { env: 'space', c: shot(a.clone().add(V(-1.6, 0.5, 3.0)), a.clone().add(V(-0.5, 0.65, -0.1)), 46, ORBIT, 10), host: [0.7, 0.02, 0.27, -0.4, 'feliz'] }; }
};
let frames = 0;
scene.autoUpdate = false;
const SQUARE = K === 'avatar';
if (SQUARE) { stage.classList.add('square'); renderer.setPixelRatio(1); renderer.setSize(800, 800, false); camera.aspect = 1; }
else { renderer.setPixelRatio(1); renderer.setSize(1280, 720, false); camera.aspect = 16 / 9; }
// the channel avatar: Mr. News winking in front of orange rays
const RAYS = (function () {
  const tex = canvasTex(256, 256, (c, w, h) => { const cx = w / 2, cy = h / 2; c.fillStyle = '#ff6a1f'; c.fillRect(0, 0, w, h); c.fillStyle = '#ff8a3d'; for (let i = 0; i < 18; i++) { c.beginPath(); c.moveTo(cx, cy); c.arc(cx, cy, w, i / 18 * TAU, (i + 0.5) / 18 * TAU); c.closePath(); c.fill(); } const gr = c.createRadialGradient(cx, cy, 0, cx, cy, w * 0.5); gr.addColorStop(0, 'rgba(255,240,200,.85)'); gr.addColorStop(1, 'rgba(255,240,200,0)'); c.fillStyle = gr; c.fillRect(0, 0, w, h); });
  const m = new THREE.Mesh(new THREE.CircleGeometry(6, 64), new THREE.MeshBasicMaterial({ map: tex, depthWrite: false })); m.position.set(0, 1.15, -2.2); const g = newGroup(); g.add(m); return g;
})();
function frame(ms) {
  const now = 2.0;
  for (const g of GROUPS) g.visible = false; for (const c of CHARS) c.root.visible = false; ACTIVE.length = 0; EMIT.length = 0;
  const T = THUMBS[K](now);
  setEnv(T.env);
  camera.position.copy(T.c.p); camera.fov = T.c.f; camera.updateProjectionMatrix(); camera.up.copy(UP); camera.lookAt(T.c.l);
  if (T.host) host(...T.host);
  for (const ch of ACTIVE) updateChar(ch, now);
  aimSun(T.c.focus, T.c.box); scene.updateMatrixWorld();
  PART.begin(); for (const e of EMIT) e(now, 0); PART.end();
  POST.U.uImpact.value = 0; POST.U.uFlash.value = 0; POST.U.uAberr.value = T.aberr || 0; POST.U.uSat.value = 1.22; POST.U.uWarm.value = 0; POST.U.uSepia.value = 0;
  POST.render();
  if (++frames > 20) { document.title = 'READY'; return; }
  requestAnimationFrame(frame);
}
TRAILS.draw([], 0);
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { FONT_REDRAW.forEach(f => f()); requestAnimationFrame(frame); }); else requestAnimationFrame(frame);
})();
</script>
