
// ---------- batching: many static blocks become one mesh per material ----------
function mergeGeos(list) {
  let nv = 0, ni = 0;
  for (const g of list) { nv += g.attributes.position.count; ni += g.index.count; }
  const pos = new Float32Array(nv * 3), nor = new Float32Array(nv * 3), uv = new Float32Array(nv * 2), idx = nv > 65535 ? new Uint32Array(ni) : new Uint16Array(ni);
  let vo = 0, io = 0;
  for (const g of list) {
    const n = g.attributes.position.count, ix = g.index.array;
    pos.set(g.attributes.position.array, vo * 3); nor.set(g.attributes.normal.array, vo * 3); uv.set(g.attributes.uv.array, vo * 2);
    for (let i = 0; i < ix.length; i++) idx[io + i] = ix[i] + vo;
    vo += n; io += ix.length; g.dispose();
  }
  const out = new THREE.BufferGeometry();
  out.setAttribute('position', new THREE.BufferAttribute(pos, 3)); out.setAttribute('normal', new THREE.BufferAttribute(nor, 3)); out.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  out.setIndex(new THREE.BufferAttribute(idx, 1)); out.computeBoundingSphere();
  return out;
}
const _bm = new THREE.Matrix4(), _bq = new THREE.Quaternion(), _be = new THREE.Euler(), _bs = new THREE.Vector3(1, 1, 1), _bp = new THREE.Vector3();
function makeBatch() {
  const groups = new Map();
  const push = (m, geo) => { if (!groups.has(m)) groups.set(m, []); groups.get(m).push(geo); };
  return {
    // same arguments as box(), minus the parent; o.tile tiles the texture, o.rot rotates
    box(w, h, d, m, pos, o) {
      o = o || {};
      const geo = new THREE.BoxGeometry(w, h, d);
      if (o.tile) { const uv = geo.attributes.uv, dims = [[d, h], [d, h], [w, d], [w, d], [w, h], [w, h]]; for (let f = 0; f < 6; f++) for (let k = 0; k < 4; k++) { const i = f * 4 + k; uv.setXY(i, uv.getX(i) * dims[f][0] / o.tile, uv.getY(i) * dims[f][1] / o.tile); } }
      const r = o.rot || [0, 0, 0]; _be.set(r[0], r[1], r[2]); _bq.setFromEuler(_be);
      _bm.compose(_bp.set(pos[0], pos[1], pos[2]), _bq, _bs); geo.applyMatrix4(_bm);
      push(typeof m === 'number' ? mat(m) : m, geo);
    },
    geo(geo, m) { push(m, geo); },
    flush(parent, o) {
      o = o || {}; const meshes = [];
      for (const [m, list] of groups) { const mesh = new THREE.Mesh(mergeGeos(list), m); mesh.castShadow = o.cast !== false; mesh.receiveShadow = o.receive !== false; parent.add(mesh); meshes.push(mesh); }
      groups.clear(); return meshes;
    }
  };
}

// ---------- post: HDR bloom, colour grade, vignette, sepia, aberration, impact frames ----------
const POST = (function () {
  const caps = renderer.capabilities, hdr = caps.isWebGL2 && renderer.extensions.has('EXT_color_buffer_float');
  const opts = d => ({ minFilter: THREE.LinearFilter, magFilter: THREE.LinearFilter, format: THREE.RGBAFormat, type: hdr ? THREE.HalfFloatType : THREE.UnsignedByteType, depthBuffer: d, stencilBuffer: false });
  const sceneRT = new THREE.WebGLRenderTarget(4, 4, opts(true));
  // a 24-bit depth texture: the default render-target depth buffer is 16-bit and makes distant surfaces fight
  if (caps.isWebGL2) { sceneRT.depthTexture = new THREE.DepthTexture(4, 4); sceneRT.depthTexture.type = THREE.UnsignedIntType; }
  const h0 = new THREE.WebGLRenderTarget(4, 4, opts(false)), h1 = h0.clone(), q0 = h0.clone(), q1 = h0.clone();
  const VS = 'varying vec2 vUv; void main(){ vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }';
  const bright = new THREE.ShaderMaterial({ uniforms: { tDiffuse: { value: null }, uTexel: { value: new THREE.Vector2() }, uThreshold: { value: 1 } }, vertexShader: VS, depthTest: false, depthWrite: false,
    fragmentShader: 'uniform sampler2D tDiffuse; uniform vec2 uTexel; uniform float uThreshold; varying vec2 vUv;\n' +
      'void main(){ vec3 c = 0.25 * (texture2D(tDiffuse, vUv + uTexel * vec2(-0.5,-0.5)).rgb + texture2D(tDiffuse, vUv + uTexel * vec2(0.5,-0.5)).rgb + texture2D(tDiffuse, vUv + uTexel * vec2(-0.5,0.5)).rgb + texture2D(tDiffuse, vUv + uTexel * vec2(0.5,0.5)).rgb);\n' +
      ' float l = max(c.r, max(c.g, c.b)); gl_FragColor = vec4(c * smoothstep(uThreshold, uThreshold + 0.6, l), 1.0); }' });
  const blur = new THREE.ShaderMaterial({ uniforms: { tDiffuse: { value: null }, uDir: { value: new THREE.Vector2() } }, vertexShader: VS, depthTest: false, depthWrite: false,
    fragmentShader: 'uniform sampler2D tDiffuse; uniform vec2 uDir; varying vec2 vUv;\n' +
      'void main(){ vec3 c = texture2D(tDiffuse, vUv).rgb * 0.2270270;\n' +
      ' c += (texture2D(tDiffuse, vUv + uDir * 1.3846154).rgb + texture2D(tDiffuse, vUv - uDir * 1.3846154).rgb) * 0.3162162;\n' +
      ' c += (texture2D(tDiffuse, vUv + uDir * 3.2307692).rgb + texture2D(tDiffuse, vUv - uDir * 3.2307692).rgb) * 0.0702703;\n' +
      ' gl_FragColor = vec4(c, 1.0); }' });
  const U = { tScene: { value: sceneRT.texture }, tBloomA: { value: h0.texture }, tBloomB: { value: q0.texture }, uBloom: { value: 0.4 }, uSat: { value: 1.12 }, uSepia: { value: 0 }, uVig: { value: 0.35 },
    uAberr: { value: 0 }, uImpact: { value: 0 }, uFlash: { value: 0 }, uWarm: { value: 0 }, uPS1: { value: 0 }, uLow: { value: new THREE.Vector2(1, 1) } };
  const comp = new THREE.ShaderMaterial({ uniforms: U, vertexShader: VS, depthTest: false, depthWrite: false,
    fragmentShader: 'uniform sampler2D tScene, tBloomA, tBloomB; uniform float uBloom, uSat, uSepia, uVig, uAberr, uImpact, uFlash, uWarm, uPS1; uniform vec2 uLow; varying vec2 vUv;\n' +
      'vec3 toSRGB(vec3 c){ c = clamp(c, 0.0, 1.0); return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c)); }\n' +
      'void main(){ vec2 uvS = uPS1 > 0.5 ? (floor(vUv * uLow) + 0.5) / uLow : vUv; vec2 d = uvS - 0.5; vec3 col;\n' +
      ' if (uAberr > 0.001) { vec2 o = d * uAberr * 0.018; col = vec3(texture2D(tScene, uvS + o).r, texture2D(tScene, uvS).g, texture2D(tScene, uvS - o).b); } else col = texture2D(tScene, uvS).rgb;\n' +
      ' col += (texture2D(tBloomA, uvS).rgb * 0.7 + texture2D(tBloomB, uvS).rgb * 0.9) * uBloom;\n' +
      ' vec3 over = max(col - 0.8, 0.0); col = min(col, 0.8) + 0.2 * (1.0 - exp(-over / 0.2));\n' +
      ' col = toSRGB(col);\n' +
      ' float L = dot(col, vec3(0.2126, 0.7152, 0.0722)); col = mix(vec3(L), col, uSat);\n' +
      ' col *= mix(vec3(1.0), vec3(1.06, 1.0, 0.9), uWarm);\n' +
      ' vec3 sep = vec3(dot(col, vec3(0.393, 0.769, 0.189)), dot(col, vec3(0.349, 0.686, 0.168)), dot(col, vec3(0.272, 0.534, 0.131)));\n' +
      ' col = mix(col, sep * vec3(0.98, 0.95, 0.88), uSepia);\n' +
      ' float v = smoothstep(0.9, 0.3, length(d * vec2(1.0, 0.8))); col *= mix(1.0, v, uVig);\n' +
      ' if (uImpact > 0.001) { float I = step(0.42, dot(col, vec3(0.3, 0.59, 0.11))); col = mix(col, vec3(1.0 - I) * vec3(1.0, 0.96, 0.92), uImpact); }\n' +
      ' if (uPS1 > 0.5) { vec2 cl = floor(gl_FragCoord.xy / uPS1), a = mod(cl, 2.0), b = mod(floor(cl / 2.0), 2.0); float t = (4.0 * mod(2.0 * a.x + 3.0 * a.y, 4.0) + mod(2.0 * b.x + 3.0 * b.y, 4.0)) / 16.0 - 0.47; col = floor(clamp(col, 0.0, 1.0) * 15.0 + 0.5 + t) / 15.0; }\n' +
      ' gl_FragColor = vec4(clamp(col + uFlash, 0.0, 1.0), 1.0); }' });
  const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), comp); quad.frustumCulled = false;
  const qs = new THREE.Scene(); qs.add(quad); const qc = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  const size = new THREE.Vector2();
  let W = 0, H = 0, K = 0, broken = false, checked = false;
  function pass(m, target) { quad.material = m; renderer.setRenderTarget(target); renderer.render(qs, qc); }
  function resize() {
    renderer.getDrawingBufferSize(size); const k = Math.max(1, U.uPS1.value); if (size.x === W && size.y === H && k === K) return;
    W = size.x; H = size.y; K = k;
    const lw = Math.ceil(W / k), lh = Math.ceil(H / k); sceneRT.setSize(lw, lh); U.uLow.value.set(lw, lh); PS1_SNAP.value.set(k > 1 ? 1 : 0, lw / 2, lh / 2);
    const hw = Math.max(1, W >> 1), hh = Math.max(1, H >> 1), qw = Math.max(1, W >> 2), qh = Math.max(1, H >> 2);
    h0.setSize(hw, hh); h1.setSize(hw, hh); q0.setSize(qw, qh); q1.setSize(qw, qh);
  }
  return {
    U, hdr,
    setPS1(k) { U.uPS1.value = k > 1 ? k : 0; },
    render() {
      if (broken) { renderer.setRenderTarget(null); renderer.render(scene, camera); return; }
      resize();
      renderer.setRenderTarget(sceneRT);
      if (!checked) { checked = true; const gl = renderer.getContext(); if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) { broken = true; renderer.setRenderTarget(null); renderer.render(scene, camera); return; } }
      renderer.render(scene, camera);
      bright.uniforms.tDiffuse.value = sceneRT.texture; bright.uniforms.uTexel.value.set(1 / W, 1 / H); bright.uniforms.uThreshold.value = hdr ? 0.95 : 0.78;
      pass(bright, h0);
      blur.uniforms.tDiffuse.value = h0.texture; blur.uniforms.uDir.value.set(1 / h0.width, 0); pass(blur, h1);
      blur.uniforms.tDiffuse.value = h1.texture; blur.uniforms.uDir.value.set(0, 1 / h0.height); pass(blur, h0);
      bright.uniforms.tDiffuse.value = h0.texture; bright.uniforms.uTexel.value.set(1 / h0.width, 1 / h0.height); bright.uniforms.uThreshold.value = -1; pass(bright, q0);
      blur.uniforms.tDiffuse.value = q0.texture; blur.uniforms.uDir.value.set(1 / q0.width, 0); pass(blur, q1);
      blur.uniforms.tDiffuse.value = q1.texture; blur.uniforms.uDir.value.set(0, 1 / q0.height); pass(blur, q0);
      pass(comp, null);
    },
    disable() { broken = true; }
  };
})();
// HDR emissive: lamps, neon and lights glow past white so only they bloom
const GLOW_K = POST.hdr ? 2.4 : 1;
const glow = (hex, k) => mat(hex, { emissive: hex, emissiveIntensity: (k == null ? 1 : k) * GLOW_K, plain: true });

// ---------- particles: two instanced pools, solid (smoke, dust, debris) and glowing (sparks, fire, fireworks) ----------
const PART = (function () {
  const N = 1200;
  const mk = additive => {
    const m = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: additive, blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending, depthWrite: !additive }), N);
    m.instanceMatrix.setUsage(THREE.DynamicDrawUsage); m.setColorAt(0, new THREE.Color()); m.instanceColor.setUsage(THREE.DynamicDrawUsage);
    m.frustumCulled = false; m.count = 0; scene.add(m); return { m, n: 0 };
  };
  const pools = { solid: mk(false), glow: mk(true) };
  const o = new THREE.Object3D(), col = new THREE.Color(), Z = V(0, 0, 1), dir = V(0, 0, 0);
  return {
    begin() { pools.solid.n = 0; pools.glow.n = 0; },
    // one cube: position, size (number or [x,y,z]), linear colour multiplied by k, optional spin or a direction to stretch along
    add(kind, x, y, z, s, hex, k, spin, along) {
      const p = pools[kind]; if (p.n >= N) return;
      o.position.set(x, y, z);
      if (along) { dir.copy(along).normalize(); o.quaternion.setFromUnitVectors(Z, dir); o.scale.set(s, s, s * along.length()); }
      else { if (spin) o.rotation.set(spin, spin * 1.3, spin * 0.7); else o.rotation.set(0, 0, 0); if (Array.isArray(s)) o.scale.set(s[0], s[1], s[2]); else o.scale.setScalar(s); }
      o.updateMatrix(); p.m.setMatrixAt(p.n, o.matrix);
      col.setHex(hex).convertSRGBToLinear().multiplyScalar(k == null ? 1 : k); p.m.setColorAt(p.n, col); p.n++;
    },
    end() { for (const k in pools) { const p = pools[k]; p.m.count = p.n; p.m.instanceMatrix.needsUpdate = true; if (p.m.instanceColor) p.m.instanceColor.needsUpdate = true; } }
  };
})();
// shockwave rings
const RINGS = [];
for (let i = 0; i < 6; i++) {
  const m = new THREE.Mesh(new THREE.RingGeometry(0.8, 1, 40), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide }));
  m.visible = false; scene.add(m); RINGS.push(m);
}
const _v = V(0, 0, 0), _w = V(0, 0, 0);
// deterministic effects: every event is a pure function of scene time, so scrubbing replays them
function drawFx2(list, now) {
  let ri = 0;
  for (const f of list) {
    const age = now - f.t0; if (age < 0 || age > f.life) continue;
    const u = age / f.life;
    if (f.kind === 'ring') {
      if (ri >= RINGS.length) continue;
      const m = RINGS[ri++]; m.visible = true; m.position.set(f.o[0], f.o[1], f.o[2]);
      if (f.face) m.quaternion.copy(camera.quaternion); else m.rotation.set(-PI / 2, 0, 0);
      m.scale.setScalar(0.2 + f.size * easeOut(u)); m.material.color.setHex(f.color || 0xffffff).convertSRGBToLinear().multiplyScalar((1 - u) * (POST.hdr ? 2 : 1)); m.updateMatrixWorld();
      continue;
    }
    const r = rng(f.seed), kind = f.glow ? 'glow' : 'solid', K = f.glow ? GLOW_K * (f.kind === 'burst' ? 1 - u * 0.6 : 1) : 1;
    for (let i = 0; i < f.n; i++) {
      const a = r() * TAU, b = r(), spd = f.speed * (0.6 + r() * 0.7), sp = r();
      let dx, dy, dz;
      if (f.kind === 'puff') { dx = Math.cos(a); dz = Math.sin(a); dy = 0.25 + b * 0.5; }
      else { const zz = b * 2 - 1, q = Math.sqrt(1 - zz * zz); dx = Math.cos(a) * q; dz = Math.sin(a) * q; dy = zz * 0.8 + 0.35; }
      const travel = spd * f.life * easeOut(u) * 0.6, g = f.kind === 'burst' ? (f.gravity == null ? 2.2 : f.gravity) : 0;
      const x = f.o[0] + dx * travel, y = f.o[1] + dy * travel - g * age * age + (f.rise || 0) * age, z = f.o[2] + dz * travel;
      const sc = f.kind === 'puff' ? f.size * (0.7 + 0.8 * Math.sin(Math.min(1, u * 1.4) * PI * 0.5)) * (1 - u * 0.75) : f.size * (1 - u * 0.85);
      if (sc < 0.002) continue;
      const hex = f.colors[i % f.colors.length];
      if (f.streak) {
        const vel = spd * f.life * 0.6 * 3 * Math.pow(1 - u, 2) / f.life;
        _v.set(dx * vel, dy * vel - 2 * g * age, dz * vel).multiplyScalar(0.06);
        if (_v.lengthSq() < 1e-6) _v.set(0, 0.001, 0);
        PART.add(kind, x, y, z, sc, hex, K, 0, _w.copy(_v).setLength(Math.max(1, _v.length() / sc)));
      } else PART.add(kind, x, y, z, sc, hex, K, f.spin ? sp * TAU + age * f.spin : 0);
    }
  }
  for (; ri < RINGS.length; ri++) RINGS[ri].visible = false;
}

// ---------- continuous emitters, drawn fresh every frame ----------
const _ex = V(0, 0, 0), _fw = V(0, 0, 0);
// exhaust flames: k is 0 to 1 (1 is a full boost)
function flames(car, k, now) {
  if (!car.root.visible || !car.exhausts || k <= 0) return;
  _fw.set(0, 0, -1).applyQuaternion(car.root.quaternion);
  car.exhausts.forEach((e, j) => {
    _ex.copy(e); car.body.localToWorld(_ex);
    for (let i = 0; i < 4; i++) {
      const fl = 0.75 + 0.25 * Math.sin(now * 53 + i * 2.1 + j * 1.7) * Math.sin(now * 31 + i);
      const d = (0.05 + i * 0.09) * (0.6 + 0.6 * k) * fl, s = (0.1 - i * 0.018) * (0.7 + 0.5 * k) * fl;
      PART.add('glow', _ex.x + _fw.x * d, _ex.y + _fw.y * d, _ex.z + _fw.z * d, s, [0xfff6c8, 0xffc23a, 0xff7a2a, 0xff3b2a][i], GLOW_K * (0.9 + k * 0.5), 0);
    }
  });
}
// rising embers above a point
function embers(p, now, n, color) {
  for (let i = 0; i < n; i++) {
    const ph = (now * 0.55 + i / n) % 1, a = i * 2.39996;
    PART.add('glow', p.x + Math.sin(a + now * 1.3) * 0.25 * ph, p.y + 0.3 + ph * 1.9, p.z + Math.cos(a + now) * 0.25 * ph, 0.035 * (1 - ph) + 0.01, color || 0xffa23a, GLOW_K * (1 - ph), 0);
  }
}
// a spinning column of dust
function dustDevil(p, now, h) {
  for (let i = 0; i < 26; i++) {
    const ph = (now * 0.35 + i / 26) % 1, a = now * 5 + i * 1.7, rr = 0.25 + ph * 1.1;
    PART.add('solid', p.x + Math.cos(a) * rr, p.y + ph * h, p.z + Math.sin(a) * rr, 0.18 + ph * 0.25, [0xe3c88c, 0xd2ae6e, 0xf2e2b8][i % 3], 1, a);
  }
}

// ---------- light trails behind tail lights (and the record ghost) ----------
const TRAILS = (function () {
  const MAXV = 4000, pos = new Float32Array(MAXV * 3), colA = new Float32Array(MAXV * 3), idx = new Uint16Array(MAXV * 3);
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3).setUsage(THREE.DynamicDrawUsage));
  geo.setAttribute('color', new THREE.BufferAttribute(colA, 3).setUsage(THREE.DynamicDrawUsage));
  geo.setIndex(new THREE.BufferAttribute(idx, 1).setUsage(THREE.DynamicDrawUsage));
  const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ vertexColors: true, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide }));
  mesh.frustumCulled = false; scene.add(mesh);
  const hist = new Map(), tmp = V(0, 0, 0), seg = V(0, 0, 0), view = V(0, 0, 0), side = V(0, 0, 0), c = new THREE.Color();
  return {
    // record the tail lights of every visible moving car
    record(cars, t, on) {
      for (const car of cars) {
        let h = hist.get(car);
        if (!on.has(car) || !car.root.visible || !car.tails) { if (h) h.length = 0; continue; }
        if (!h) { h = []; hist.set(car, h); }
        const pts = car.tails.map(tl => car.body.localToWorld(tmp.copy(tl)).clone());
        const last = h[h.length - 1];
        if (last && (t < last.t || t - last.t > 0.3 || last.p[0].distanceTo(pts[0]) > 8)) h.length = 0;
        if (!last || t > last.t + 0.004) h.push({ t, p: pts });
        while (h.length && h[0].t < t - (car.trailLife || 0.45)) h.shift();
      }
    },
    draw(cars, t) {
      let nv = 0, ni = 0;
      for (const car of cars) {
        const h = hist.get(car); if (!h || h.length < 2 || !car.root.visible) continue;
        c.setHex(car.trailColor || 0xff2a3a).convertSRGBToLinear().multiplyScalar(GLOW_K * 1.1);
        const life = car.trailLife || 0.45, wdt = car.trailWidth || 0.075;
        for (let l = 0; l < h[0].p.length; l++) {
          for (let i = 0; i < h.length && nv < MAXV - 2; i++) {
            const p = h[i].p[l], q = h[Math.min(h.length - 1, i + 1)].p[l], pr = h[Math.max(0, i - 1)].p[l];
            seg.copy(q).sub(pr); view.copy(p).sub(camera.position); side.crossVectors(seg, view);
            if (side.lengthSq() < 1e-8) side.set(0, wdt, 0); else side.setLength(wdt);
            const fade = clamp(1 - (t - h[i].t) / life);
            pos.set([p.x + side.x, p.y + side.y, p.z + side.z, p.x - side.x, p.y - side.y, p.z - side.z], nv * 3);
            colA.set([c.r * fade, c.g * fade, c.b * fade, c.r * fade, c.g * fade, c.b * fade], nv * 3);
            if (i > 0) { const a = nv - 2; idx.set([a, a + 1, a + 2, a + 1, a + 3, a + 2], ni); ni += 6; }
            nv += 2;
          }
        }
      }
      geo.setDrawRange(0, ni); geo.attributes.position.needsUpdate = true; geo.attributes.color.needsUpdate = true; geo.index.needsUpdate = true;
      mesh.visible = ni > 0;
    }
  };
})();

// ---------- camera shake ----------
function shakeAt(list, lt) {
  let a = 0;
  for (const [t0, dur, amp] of list || []) { const s = lt - t0; if (s >= 0 && s < dur) a = Math.max(a, amp * (1 - s / dur)); }
  return a;
}
