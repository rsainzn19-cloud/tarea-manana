
// ---------- an astronaut (generic suit, closed gold visor) ----------
const ASTRO = { style: 'helmet', noFace: true, skin: 0xe8ecf2, helmetHex: 0xf2f4f8, visorHex: 0xd9a62a, stripeHex: 0xff6a1f, hairHex: 0xf2f4f8, eye: '#000000', shirt: 0xeef1f6, sleeve: 10, hand: 0x8a909c, leg: 0xeef1f6, shoe: 0x8a909c, sole: 0x5a606c, expr: 'serio',
  paintBody: (g, B) => { const f = B.front; g.fillStyle = '#2a6ad8'; g.fillRect(f[0] + 1, f[1] + 2, 2, 2); g.fillStyle = '#ff6a1f'; for (const k of ['left', 'right']) { const q = B[k]; g.fillRect(q[0] + 1, q[1], 2, 12); } g.fillStyle = '#8a909c'; g.fillRect(f[0] + 4, f[1] + 3, 3, 4); },
  paintArm: (g, N) => { g.fillStyle = '#ff6a1f'; for (const k of ['left', 'right']) { const q = N[k]; g.fillRect(q[0] + 1, q[1], 2, 9); } } };
const astro = makeChar('astro', ASTRO, 0.6); world.add(astro.root); CHARS.push(astro);
Object.assign(ANIMS, {
  float(P, t) { ANIMS.idle(P, t); P.aLZ = 0.9 + Math.sin(t * 1.3) * 0.15; P.aRZ = -2.3 + Math.sin(t * 6) * 0.25; P.lLX = 0.25 + Math.sin(t * 1.1) * 0.1; P.lRX = -0.2; P.kL = 0.4; P.kR = 0.2; P.bodyY = Math.sin(t * 1.2) * 0.05; }
});

// ---------- diorama 10: an airliner dives, recovers and lands at a desert airport ----------
function blockAirliner() {
  const root = new THREE.Group(), B = makeBatch(), white = mat(0xf4f6fa), belly = mat(0xc9ccd8), liv = mat(0x1f9a9a), acc = mat(0xff6a1f), dark = mat(0x1d2433);
  B.box(0.9, 0.95, 6.2, white, [0, 0.95, 0]); B.box(0.92, 0.3, 6.2, belly, [0, 0.55, 0]);
  B.box(0.75, 0.8, 0.5, white, [0, 0.95, 3.35]); B.box(0.5, 0.55, 0.35, white, [0, 0.9, 3.75]); B.box(0.55, 0.16, 0.06, dark, [0, 1.12, 3.62]);
  for (let z = -2.5; z <= 2.6; z += 0.36) for (const s of [-1, 1]) B.box(0.02, 0.12, 0.16, dark, [s * 0.455, 1.12, z]);
  B.box(0.93, 0.08, 6.0, liv, [0, 1.36, -0.1]); B.box(0.93, 0.05, 6.0, acc, [0, 1.27, -0.1]);
  for (const s of [-1, 1]) {
    B.box(3.3, 0.1, 1.1, white, [s * 1.9, 0.7, 0.2], { rot: [0, -s * 0.32, 0] });
    B.box(0.42, 0.42, 0.9, belly, [s * 1.45, 0.42, 0.7]); B.box(0.3, 0.3, 0.04, dark, [s * 1.45, 0.42, 1.16]);
    B.box(1.4, 0.07, 0.6, white, [s * 0.8, 1.05, -2.8], { rot: [0, -s * 0.35, 0] });
  }
  B.box(0.1, 1.3, 1.2, liv, [0, 1.85, -2.7]); B.box(0.12, 0.3, 1.0, acc, [0, 2.2, -2.75]);
  B.box(0.06, 0.06, 0.06, glow(0xff3b4a, 1.2), [-3.35, 0.72, -0.4]); B.box(0.06, 0.06, 0.06, glow(0x7cf08b, 1.2), [3.35, 0.72, -0.4]);
  B.flush(root);
  const gear = new THREE.Group(); root.add(gear);
  const GB = makeBatch(); for (const [x, z] of [[0, 2.6], [-0.6, 0], [0.6, 0]]) { GB.box(0.08, 0.35, 0.08, mat(0x5a606c), [x, 0.3, z]); GB.box(0.22, 0.22, 0.3, dark, [x, 0.11, z]); } GB.flush(gear);
  return { root, gear };
}
const AIRPORT = V(300, 0, 0), SKYPOS = V(0, 40, 0);
D[10] = (function flight() {
  const g = newGroup();
  const plane = blockAirliner(); g.add(plane.root);
  // a scrolling cloud deck under the cruise
  const clouds = [], r = rng(1001);
  for (let i = 0; i < 70; i++) clouds.push({ x: (r() - 0.5) * 140, z: (r() - 0.5) * 140, y: 30 + r() * 3, sx: 4 + r() * 8, sz: 3 + r() * 6 });
  const cm = new THREE.InstancedMesh(BOX1, cel({ color: lin(0xffffff), transparent: true, opacity: 0.9 }), clouds.length); cm.instanceMatrix.setUsage(THREE.DynamicDrawUsage); cm.frustumCulled = false; g.add(cm);
  const o = new THREE.Object3D();
  // the airport on its own island
  inGroup(g, () => voxelIsland(AIRPORT.x, AIRPORT.z, 16, 1, 1002, SAND_MATS));
  const B = makeBatch(), ap = (x, y, z) => [AIRPORT.x + x, y, AIRPORT.z + z];
  B.box(3.4, 0.06, 28, tmat('road'), ap(0, 0.03, 0), { tile: 3.4 });
  B.box(6, 1.6, 2.4, mat(0xe8e4dc), ap(-7, 0.8, 2)); B.box(6.2, 0.2, 2.6, mat(0x5a8fd0), ap(-7, 1.7, 2));
  B.box(1, 4, 1, mat(0xe8e4dc), ap(-6.5, 2, -5)); B.box(1.5, 0.9, 1.5, mat(0x4a8a8a), ap(-6.5, 4.4, -5));
  B.flush(g);
  const vehicles = [];
  [[4.2, 6, 0xd23a3a], [4.6, 9, 0xf4f4f4], [3.8, 12, 0xd23a3a], [-4, 8, 0xf4f4f4]].forEach(([x, z, c], k) => {
    const v = new THREE.Group(); v.position.set(...ap(x, 0, z)); v.rotation.y = 0.4 * (k % 2 ? 1 : -1); g.add(v);
    const VB = makeBatch(); VB.box(0.8, 0.7, 1.6, mat(c), [0, 0.45, 0]); VB.box(0.7, 0.2, 0.05, mat(0x9ec4e8), [0, 0.6, 0.81]); for (const zz of [0.5, -0.5]) for (const s of [-1, 1]) VB.box(0.1, 0.26, 0.26, mat(0x23262e), [s * 0.4, 0.13, zz]); VB.flush(v);
    const l1 = box(0.16, 0.1, 0.16, glow(0xff3b4a, 1.6), v, [-0.2, 0.86, 0.3], { cast: false }), l2 = box(0.16, 0.1, 0.16, glow(0x3e7bff, 1.6), v, [0.2, 0.86, 0.3], { cast: false });
    vehicles.push([l1, l2, k]);
  });
  // phases in diorama time: cruise, dive (6-8.5), recover (8.5-10.5), cruise, approach (12-15), roll (15-18)
  const state = u => {
    if (u < 11.5) {
      const dive = clamp((u - 6) / 2.5), rec = clamp((u - 8.5) / 2);
      const pitch = u < 8.5 ? 0.45 * Math.sin(dive * PI * 0.5) : 0.45 * (1 - ease(rec));
      const drop = 7 * ease(dive);
      return { air: true, p: SKYPOS.clone().add(V(0, -drop, 0)), pitch, roll: u > 6 && u < 9 ? Math.sin(u * 9) * 0.06 : 0, scroll: u * 22 + Math.max(0, u - 6) * 8, cloudY: drop };
    }
    // glide in at 8 u/s, touch down at 15 and brake to a stop over 3 s
    const a = clamp((u - 12) / 3), s = Math.min(Math.max(0, u - 15), 3);
    const z = u < 15 ? lerp(-34, -10, a) : -10 + 8 * s - (4 / 3) * s * s;
    const y = u < 15 ? 6 * (1 - a) * (1 - a * 0.3) : 0;
    return { air: false, p: AIRPORT.clone().add(V(0, y + 0.06, z)), pitch: u < 15 ? -0.08 : 0, roll: 0 };
  };
  return {
    g, env: 'day', box: u => u < 11.5 ? 14 : 22, shake: [[6, 2.6, 0.06], [15, 0.4, 0.04]], impact: [6.05],
    fx: () => { const out = []; for (let t = 15; t < 17.5; t += 0.12) { const st = state(t); out.push(FX.puff(t, st.p.clone().add(V(0, 0.15, -0.4)), 1100 + Math.round(t * 100), 0.3, 1.2, [0x4a4a54, 0x6a6a7a, 0xd8d4c8])); } return out; },
    state,
    update(u, now) {
      const st = state(u);
      plane.root.position.copy(st.p); plane.root.rotation.set(st.pitch, 0, st.roll); plane.gear.visible = !st.air;
      cm.visible = st.air;
      if (st.air) {
        clouds.forEach((c, i) => { let z = ((c.z - st.scroll) % 140 + 210) % 140 - 70; o.position.set(c.x, c.y + st.cloudY * 0.4, z); o.scale.set(c.sx, 1.4, c.sz); o.updateMatrix(); cm.setMatrixAt(i, o.matrix); });
        cm.instanceMatrix.needsUpdate = true;
      }
      vehicles.forEach(([l1, l2, k]) => { const on = Math.floor(now * 4 + k) % 2 === 0; l1.visible = on; l2.visible = !on; });
    },
    cam(u, v) {
      const st = state(u);
      if (st.air) {
        if (u < 6) return shot(st.p.clone().add(V(v ? 9.5 : 8, v ? 2.4 : 1.9, v ? 7 : 5.5)), st.p.clone().add(V(0, v ? 1.6 : 1.1, 0.4)), v ? 56 : 42, st.p, 10);
        // side-on during the dive so the nose-down pitch reads clearly
        return shot(st.p.clone().add(V(v ? 11.5 : 9.5, v ? 2.2 : 1.6, v ? 2.4 : 2)), st.p.clone().add(V(0, v ? 1.4 : 0.9, 0.2)), v ? 58 : 46, st.p, 10);
      }
      return shot(AIRPORT.clone().add(V(v ? 10 : 9, v ? 2.4 : 1.8, Math.min(st.p.z - AIRPORT.z, 6) + (v ? 2 : 1))), st.p.clone().add(V(0, 0.9, 1)), v ? 52 : 40, AIRPORT.clone().add(V(0, 0, 4)), 22);
    }
  };
})();

// ---------- diorama 11: a tower of rial bundles against a single dollar ----------
D[11] = (function money() {
  const g = newGroup();
  const floor = cel({ map: T.floorN, emissiveMap: T.floorGlow, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.55) });
  box(14, 0.4, 9, floor, g, [0, -0.2, 0], { tile: 1, receive: true });
  const rialTex = tile(gg => { noise(gg, 0, 0, 16, 16, 0xb8c4a8, 0.1, 1110); gg.fillStyle = '#8a9a7a'; gg.fillRect(0, 7, 16, 2); gg.fillStyle = '#c9a865'; gg.fillRect(6, 6, 4, 4); });
  const N = 62, items = []; for (let i = 0; i < N; i++) items.push({ p: [-1.6 + ((i * 37) % 7 - 3) * 0.015, 0.06 + i * 0.12, ((i * 13) % 5 - 2) * 0.02], s: [1.0, 0.11, 0.5], q: new THREE.Quaternion().setFromAxisAngle(UP, ((i * 29) % 9 - 4) * 0.03) });
  let stack = null; inGroup(g, () => { stack = instanced(BOX1, cel({ map: rialTex }), items); });
  // the lone dollar sits on a tall column so it reads above the captions
  const ped = makeBatch(); ped.box(1.4, 2.4, 1.0, mat(0x13204a), [2.4, 1.2, 0]); ped.box(1.45, 0.06, 1.05, glow(0xff6a1f, 0.6), [2.4, 2.4, 0]); ped.box(1.45, 0.06, 1.05, glow(0xff6a1f, 0.4), [2.4, 0.8, 0]);
  const billTex = tile(gg => { noise(gg, 0, 0, 16, 16, 0x5a9a5a, 0.08, 1111); gg.fillStyle = '#3a7a4a'; gg.fillRect(0, 0, 16, 1); gg.fillRect(0, 15, 16, 1); gg.fillStyle = '#d8ecd0'; gg.fillRect(6, 5, 4, 6); });
  ped.box(1.0, 0.04, 0.45, cel({ map: billTex }), [2.4, 2.45, 0]); ped.flush(g);
  // a petrol pump for the "why should you care" beat
  const pumpG = new THREE.Group(); g.add(pumpG);
  const pump = makeBatch(); pump.box(0.9, 2.0, 0.6, mat(0xd23a3a), [5.4, 1.0, -1.5]); pump.box(0.7, 0.45, 0.04, glow(0x9ef0ff, 0.5), [5.4, 1.5, -1.19]); pump.box(0.1, 0.8, 0.1, mat(0x23262e), [5.95, 1.0, -1.4]); pump.box(1.2, 0.2, 0.9, mat(0xf4f4f4), [5.4, 2.1, -1.5]); pump.flush(pumpG);
  label(g, { es: '1 DÓLAR', en: '1 DOLLAR' }, 0x13204a, '#7cf08b', V(2.4, 3.15, 0.2), 0, 1.6, 0.4, 0.4);
  const big = label(g, { es: '2,500,000 RIALES', en: '2,500,000 RIALS' }, 0x13204a, '#ffb27a', V(-1.6, 8.4, 0), 0, 3.4, 0.85, 0.5);
  return {
    g, env: 'studio', box: 10,
    update(u) { const k = u < 4 ? ease(clamp((u - 0.5) / 3.5)) * (53 / N) : 53 / N + ease(clamp((u - 6) / 2)) * (9 / N); stack.count = Math.max(1, Math.round(N * k)); big.position.y = 0.4 + stack.count * 0.12 + 0.6; big.visible = u > 6.5; pumpG.visible = u >= 9; },
    cam(u, v) {
      // a slow push on the whole tower and the lone dollar, then a cut to the petrol pump
      if (u < 9) { const k = ease(clamp(u / 9)); return shot(V(v ? 0.4 : 0.4, v ? 3.9 : 3.6, lerp(v ? 15.5 : 13.5, v ? 13 : 11.5, k)), V(v ? 0.2 : 0.4, v ? 3.9 : 3.4, 0), v ? 58 : 46, V(0, 0, 0), 10); }
      return shot(V(v ? 8.6 : 9.2, v ? 2.3 : 2.1, v ? 4.2 : 3.4), V(5.4, 1.35, -1.5), v ? 50 : 42, V(4, 0, 0), 8);
    }
  };
})();

// ---------- diorama 12: a calendar and a crib waiting at home ----------
D[12] = (function family() {
  const g = newGroup(); inGroup(g, () => voxelIsland(0, 0, 8, 1, 1201));
  const B = makeBatch();
  B.box(5.5, 0.2, 4, tmat('planks'), [0, 0.1, -0.5], { tile: 1 });
  B.box(5.5, 3, 0.2, mat(0xf2e6c8), [0, 1.6, -2.4]);
  // the crib
  B.box(1.6, 0.12, 0.9, tmat('planks'), [-1.2, 0.6, -1.2]); B.box(1.5, 0.12, 0.8, mat(0x9ec4e8), [-1.2, 0.68, -1.2]);
  for (let k = 0; k <= 6; k++) for (const z of [-1.62, -0.78]) B.box(0.06, 0.6, 0.06, tmat('planks'), [-1.95 + k * 0.25, 0.9, z]);
  for (const x of [-2.0, -0.4]) for (const z of [-1.62, -0.78]) B.box(0.1, 1.3, 0.1, tmat('planks'), [x, 0.65, z]);
  // a hanging mobile of little stars
  B.box(0.04, 0.8, 0.04, mat(0xd8c4a0), [-1.2, 1.9, -1.2]);
  for (let k = 0; k < 4; k++) { const a = k * PI / 2; B.box(0.14, 0.14, 0.04, glow(0xffd23f, 0.8), [-1.2 + Math.cos(a) * 0.35, 1.5, -1.2 + Math.sin(a) * 0.35]); }
  B.flush(g);
  const calTex = month => canvasTex(96, 96, (c, w, h) => { c.fillStyle = '#ffffff'; c.fillRect(0, 0, w, h); c.fillStyle = '#e5383b'; c.fillRect(0, 0, w, 22); c.fillStyle = '#ffffff'; c.textAlign = 'center'; c.textBaseline = 'middle'; fitFont(c, month, w - 10, 10); c.fillText(month, w / 2, 12); c.fillStyle = '#c9ccd8'; for (let r = 0; r < 5; r++) for (let k = 0; k < 7; k++) c.fillRect(6 + k * 12.5, 28 + r * 13, 10, 10); c.fillStyle = '#ff6a1f'; c.fillRect(6 + 4 * 12.5 - 2, 28 + 3 * 13 - 2, 14, 14); }, true);
  const cal = calTex('NOVIEMBRE');
  const cp = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 1.6), cel({ map: cal })); cp.position.set(1.3, 1.9, -2.28); g.add(cp);
  SCENE_TEXT.push([cp.material, { es: cal, en: calTex('NOVEMBER') }]);
  const heart = new THREE.Group(); heart.position.set(0.2, 2.4, -1.6); g.add(heart);
  const HB = makeBatch(); [[-1, 1], [1, 1], [-2, 0], [-1, 0], [0, 0], [1, 0], [2, 0], [-1, -1], [0, -1], [1, -1], [0, -2], [-2, 1], [2, 1]].forEach(([x, y]) => HB.box(0.12, 0.12, 0.12, glow(0xff3b6a, 1.0), [x * 0.12, y * 0.12, 0])); HB.flush(heart);
  return {
    g, env: 'day', box: 8,
    update(u, now) { heart.scale.setScalar(1 + Math.max(0, Math.sin(now * 6)) * 0.12); heart.position.y = 2.4 + Math.sin(now * 1.5) * 0.06; },
    cam(u, v) { const k = ease(clamp(u / 6)); return shot(V(lerp(v ? 2.2 : 3.2, v ? 1.4 : 2.2, k), v ? 1.9 : 1.6, v ? 5.6 : 4.4), V(0, 1.3, -1.6), v ? 52 : 40, V(0, 0, -1), 6); }
  };
})();

// ---------- the intro: the Earth cube drops in, squashes, spins, and the name bounces in ----------
const backOut = x => x <= 0 ? 0 : 1 + 2.70158 * Math.pow(x - 1, 3) + 1.70158 * Math.pow(x - 1, 2);
(function logoSky() { const cv = document.createElement('canvas'); cv.width = cv.height = 4; const c = cv.getContext('2d'); c.fillStyle = '#8fd0ff'; c.fillRect(0, 0, 4, 4); SKY.logo = new THREE.CanvasTexture(cv); SKY.logo.encoding = THREE.sRGBEncoding; })();
const LOGO = (function () {
  const g = newGroup(), pivot = new THREE.Group(); g.add(pivot);
  const mats = ['right', 'left', 'top', 'bottom', 'front', 'back'].map(k => {
    const t = new THREE.CanvasTexture(EARTH_CANVAS[k]); t.encoding = THREE.sRGBEncoding; t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.generateMipmaps = false;
    return cel({ map: t, emissiveMap: t, emissive: new THREE.Color(1, 1, 1).multiplyScalar(0.18) });
  });
  const cube = new THREE.Mesh(new THREE.BoxGeometry(1.6, 1.6, 1.6), mats); cube.castShadow = true; pivot.add(cube);
  const halo = new THREE.Mesh(new THREE.BoxGeometry(1.8, 1.8, 1.8), new THREE.MeshBasicMaterial({ color: lin(0xbfe8ff), transparent: true, opacity: 0.18, depthWrite: false, blending: THREE.AdditiveBlending })); pivot.add(halo);
  // a ring of little orange blocks and a satellite orbiting the planet
  const ring = new THREE.Group(); g.add(ring);
  const RB = makeBatch(); for (let i = 0; i < 28; i++) { const a = i / 28 * TAU; RB.box(0.08, 0.08, 0.08, glow(0xff6a1f, 0.9), [Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8]); }
  RB.box(0.2, 0.14, 0.14, mat(0xe8ecf6), [0, 0, 1.8]); RB.box(0.5, 0.02, 0.12, tmat('solar'), [0, 0, 1.8]); RB.flush(ring);
  // a bright sunburst behind everything
  const burstTex = canvasTex(256, 256, (c, w, h) => {
    const cx = w / 2, cy = h / 2; c.fillStyle = '#8fd0ff'; c.fillRect(0, 0, w, h); c.fillStyle = '#c4ebff';
    for (let i = 0; i < 20; i++) { c.beginPath(); c.moveTo(cx, cy); c.arc(cx, cy, w, i / 20 * TAU, (i + 0.5) / 20 * TAU); c.closePath(); c.fill(); }
    const gr = c.createRadialGradient(cx, cy, 0, cx, cy, w * 0.5); gr.addColorStop(0, 'rgba(255,255,255,.95)'); gr.addColorStop(0.3, 'rgba(255,255,255,.4)'); gr.addColorStop(1, 'rgba(255,255,255,0)');
    c.fillStyle = gr; c.fillRect(0, 0, w, h);
  });
  const burst = new THREE.Mesh(new THREE.CircleGeometry(16, 64), new THREE.MeshBasicMaterial({ map: burstTex, depthWrite: false })); burst.position.set(0, 1.6, -6); g.add(burst);
  const LAND = 0.45;
  return {
    g, env: 'logo', box: 6,
    fx: [FX.ring(LAND, V(0, 0.85, 0), 2.4, 0xffffff), FX.sparks(LAND, V(0, 0.85, 0.3), 1304, 30), FX.confetti(2.6, V(0, 1.9, 0.6), 1301), FX.confetti(2.66, V(-1.4, 1.4, 0.4), 1302), FX.confetti(2.72, V(1.4, 1.4, 0.4), 1303)],
    update(lt) {
      let y = 1.6, sy = 1, sxz = 1;
      if (lt < LAND) { const k = lt / LAND; y = 1.6 + 7 * (1 - k * k); }
      else { const d = lt - LAND, damp = Math.exp(-d * 6) * Math.cos(d * 20); sy = 1 - 0.3 * damp; sxz = 1 + 0.2 * damp; }
      const idle = Math.max(0, lt - 1.7);
      pivot.position.set(0, y + Math.sin(idle * 2.2) * 0.06, 0);
      cube.scale.set(sxz, sy, sxz); halo.scale.copy(cube.scale);
      pivot.rotation.set(0.42, -0.62 + TAU * easeOut(clamp((lt - LAND) / 1.2)) + idle * 0.35, 0);
      const rs = backOut(clamp((lt - 0.5) / 0.35)); ring.scale.setScalar(Math.max(0.001, rs)); ring.position.copy(pivot.position); ring.rotation.set(0.75, lt * 1.3, 0.28);
      burst.rotation.z = -lt * 0.3; burst.scale.setScalar(0.7 + 0.3 * easeOut(clamp(lt / 0.35)));
    },
    cam(lt, v) {
      const k = ease(clamp(lt / 2.4));
      return v ? shot(V(0, 1.7, lerp(10.2, 9.2, k)), V(0, 1.6, 0), 50, V(0, 0, 0), 6) : shot(V(0, 2.0, lerp(7.4, 6.6, k)), V(0, 1.15, 0), 36, V(0, 0, 0), 6);
    }
  };
})();
// the name under the logo: each letter pops in on its own beat, then the word does a little wave
function logoText(el, lt, on) {
  el.classList.toggle('on', on); if (!on) return;
  el.querySelectorAll('.lt').forEach((s, i) => {
    const p = clamp((lt - (0.85 + i * 0.07)) / 0.2), wave = lt > 1.75 ? Math.sin((lt - 1.75) * 11 - i * 0.7) * Math.max(0, 1 - (lt - 1.75) * 1.4) * 0.18 : 0;
    s.style.transform = 'translateY(' + ((1 - p) * 0.6 - wave).toFixed(3) + 'em) scale(' + backOut(p).toFixed(3) + ')'; s.style.opacity = p > 0 ? '1' : '0';
  });
  const tag = el.querySelector('.tag'); if (tag) { const p = clamp((lt - 1.75) / 0.22); tag.style.transform = 'scaleX(' + easeOut(p).toFixed(3) + ')'; tag.style.opacity = p > 0 ? '1' : '0'; }
}
