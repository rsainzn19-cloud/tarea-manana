// ---------- channel art: the avatar, the YouTube banner and the Facebook cover, one still frame each ----------
const K = location.hash.slice(1) || 'avatar';
const SIZES = { avatar: [800, 800], yt: [2560, 1440], fb: [1702, 630] };
const [W, H] = SIZES[K];
stage.style.width = W + 'px'; stage.style.height = H + 'px';
if (/guides/.test(location.search)) stage.classList.add('show-guides');
document.querySelectorAll('.ov').forEach(o => o.classList.toggle('on', o.dataset.k === K));
renderer.setPixelRatio(1); renderer.setSize(W, H, false); camera.aspect = W / H; camera.far = 400;
anchor.root.traverse(o => { if (o.isMesh) o.castShadow = false; });
// a still frame: nobody blinks
anchor.blinkNext = 1e9; anchor.blinkUntil = 0;

// a fixed camera; everything is placed by where it should land in the picture, in pixels
const FOV = 30, CAM = V(0, 1.2, 12), _f = V(0, 0, -1), _u = V(0, 1, 0), _r = V(1, 0, 0);
const TH = Math.tan(FOV * PI / 360);
const at = (px, py, d) => CAM.clone().addScaledVector(_f, d).addScaledVector(_r, (px / W * 2 - 1) * d * TH * camera.aspect).addScaledVector(_u, (1 - py / H * 2) * d * TH);
const unitPx = d => H / (2 * d * TH);

const ART = newGroup();
// a flat sky the engine can treat like its other sky textures
let BG = 0x8fd0ff;
function flatSky(css) { const cv = document.createElement('canvas'); cv.width = cv.height = 4; const c = cv.getContext('2d'); c.fillStyle = css; c.fillRect(0, 0, 4, 4); const t = new THREE.CanvasTexture(cv); t.encoding = THREE.sRGBEncoding; return t; }
function rays(colA, colB, glowCol) {
  return canvasTex(512, 512, (c, w, h) => {
    const cx = w / 2, cy = h / 2; c.fillStyle = colA; c.fillRect(0, 0, w, h); c.fillStyle = colB;
    for (let i = 0; i < 22; i++) { c.beginPath(); c.moveTo(cx, cy); c.arc(cx, cy, w, i / 22 * TAU, (i + 0.5) / 22 * TAU); c.closePath(); c.fill(); }
    const gr = c.createRadialGradient(cx, cy, 0, cx, cy, w * 0.5); gr.addColorStop(0, glowCol); gr.addColorStop(0.35, glowCol.replace(/[\d.]+\)$/, '0.25)')); gr.addColorStop(1, glowCol.replace(/[\d.]+\)$/, '0)'));
    c.fillStyle = gr; c.fillRect(0, 0, w, h);
  });
}
// the sunburst behind everything, centred where the eye should go
function burst(px, py, tex) {
  const d = 120, m = new THREE.Mesh(new THREE.CircleGeometry(d * TH * Math.max(camera.aspect, 1) * 2.4, 72), new THREE.MeshBasicMaterial({ map: tex, depthWrite: false }));
  m.position.copy(at(px, py, d)); m.lookAt(CAM); ART.add(m); return m;
}
// the Earth cube with its ring of orange blocks; size is its on-screen height in pixels
function earth(px, py, size) {
  const d = 14, s = size / (1.62 * unitPx(d));
  const mats = ['right', 'left', 'top', 'bottom', 'front', 'back'].map(k => { const t = new THREE.CanvasTexture(EARTH_CANVAS[k]); t.encoding = THREE.sRGBEncoding; t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.generateMipmaps = false; return cel({ map: t, emissiveMap: t, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.22) }); });
  const g = new THREE.Group(), cube = new THREE.Mesh(new THREE.BoxGeometry(s, s, s), mats);
  cube.rotation.set(0.42, -0.62, 0); g.add(cube);
  const ring = new THREE.Group(), RB = makeBatch();
  for (let i = 0; i < 30; i++) { const a = i / 30 * TAU; RB.box(s * 0.055, s * 0.055, s * 0.055, glow(0xff6a1f, 0.9), [Math.cos(a) * s * 1.12, 0, Math.sin(a) * s * 1.12]); }
  RB.box(s * 0.13, s * 0.09, s * 0.09, mat(0xe8ecf6), [0, 0, s * 1.12]); RB.box(s * 0.32, s * 0.012, s * 0.08, tmat('solar'), [0, 0, s * 1.12]); RB.flush(ring);
  ring.rotation.set(0.75, 2.1, 0.28); g.add(ring);
  g.position.copy(at(px, py, d)); ART.add(g); return g;
}
// little floating blocks in the brand colours, scattered where they will not cover anything
function confetti(seed, n, keepOut) {
  const r = rng(seed), cols = [0xff6a1f, 0xffd23f, 0x3ec7ff, 0x7cf08b, 0xffffff], B = makeBatch();
  for (let i = 0, tries = 0; i < n && tries < n * 40; tries++) {
    const px = r() * W, py = r() * H; if (keepOut.some(([x0, y0, x1, y1]) => px > x0 && px < x1 && py > y0 && py < y1)) continue;
    const d = 18 + r() * 10, s = (14 + r() * 26) / unitPx(d), p = at(px, py, d);
    B.box(s, s, s, mat(cols[i % cols.length]), [p.x, p.y, p.z], { rot: [r() * 3, r() * 3, r() * 3] }); i++;
  }
  B.flush(ART);
}
// Mr. News: the top of his head lands at (px, py) and his head is `size` pixels tall
const EX = new URLSearchParams(location.search).get('e');
function host(px, py, size, yaw, anim, expr, t) {
  expr = EX || expr;
  const d = 0.31 * H / (2 * TH * size), p = at(px, py, d).addScaledVector(_u, -1.24);
  stand(anchor, p, 0, anim, expr, t); anchor.root.rotation.set(0, yaw, 0);
}

const HOSTS = { avatar: () => host(400, 196, 330, 0, 'present', 'guino', 0.4), yt: () => host(1912, 612, 150, -0.4, 'wave', 'serio', 0.55), fb: () => host(1395, 214, 122, -0.4, 'wave', 'serio', 0.55) };
const LAYOUT = {
  avatar() {
    scene.background = flatSky('#ff6a1f'); BG = 0xff6a1f;
    burst(400, 400, rays('#ff6a1f', '#ff8f45', 'rgba(255,214,150,.9)'));
    HOSTS.avatar();
  },
  yt() {
    scene.background = flatSky('#8fd0ff'); BG = 0x8fd0ff;
    burst(1230, 700, rays('#8fd0ff', '#b9e6ff', 'rgba(255,255,255,.95)'));
    earth(640, 716, 240);
    HOSTS.yt();
    confetti(2601, 70, [[480, 470, 2080, 960]]);
  },
  fb() {
    scene.background = flatSky('#8fd0ff'); BG = 0x8fd0ff;
    burst(820, 300, rays('#8fd0ff', '#b9e6ff', 'rgba(255,255,255,.95)'));
    earth(360, 300, 190);
    HOSTS.fb();
    confetti(1702, 34, [[170, 120, 1540, 520]]);
  }
};

let frames = 0;
scene.autoUpdate = false;
function frame() {
  const now = 2.0;
  for (const g of GROUPS) g.visible = false; for (const c of CHARS) c.root.visible = false; ACTIVE.length = 0; EMIT.length = 0;
  ART.visible = true;
  scene.fog.color.copy(lin(BG)); scene.fog.near = 300; scene.fog.far = 600; SCENE_TINT.value.set(1, 1, 1); POST.U.uBloom.value = 0.35; POST.U.uVig.value = K === 'avatar' ? 0.2 : 0.12;
  camera.position.copy(CAM); camera.fov = FOV; camera.updateProjectionMatrix(); camera.up.copy(UP); camera.lookAt(CAM.clone().add(_f));
  if (frames === 0) LAYOUT[K]();
  else LAYOUT_HOST();
  for (const ch of ACTIVE) updateChar(ch, now);
  aimSun(V(0, 1.2, 0), 8); scene.updateMatrixWorld();
  PART.begin(); PART.end();
  POST.U.uImpact.value = 0; POST.U.uFlash.value = 0; POST.U.uAberr.value = 0; POST.U.uSat.value = 1.15; POST.U.uWarm.value = 0; POST.U.uSepia.value = 0;
  POST.render();
  if (++frames > 20) { document.title = 'READY'; return; }
  requestAnimationFrame(frame);
}
// the scene is built once; the host is posed again every frame so his pose settles
const LAYOUT_HOST = () => HOSTS[K]();
TRAILS.draw([], 0);
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { FONT_REDRAW.forEach(f => f()); requestAnimationFrame(frame); }); else requestAnimationFrame(frame);
})();
</script>
