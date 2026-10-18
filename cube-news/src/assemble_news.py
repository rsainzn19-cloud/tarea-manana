import os, re
SP = os.path.dirname(os.path.abspath(__file__))
# this file lives in <repo>/cube-news/src
REPO = os.path.abspath(os.path.join(SP, '..', '..'))
src = open(os.path.join(REPO, 'meteoro-thunderhead/index.html')).read()
a = src.index('const TAU = Math.PI * 2, PI = Math.PI;')
b = src.index('// ---------- cast ----------')
eng = src[a:b]

def rep(old, new, count=1):
    global eng
    n = eng.count(old)
    assert n == count, (old[:80], n)
    eng = eng.replace(old, new)

def rep_between(start, end, new, keep_end=True):
    global eng
    i = eng.index(start); j = eng.index(end, i)
    eng = eng[:i] + new + (eng[j:] if keep_end else eng[j + len(end):])

# sky: a day gradient and a pixel night sky with stars
rep_between('(function sky() {', 'scene.fog = new THREE.Fog', '''const SKY = {};
let SUN_SPRITE = null, CLOUDS = null;
(function sky() {
  const cv = document.createElement('canvas'); cv.width = 4; cv.height = 512;
  const g = cv.getContext('2d'), gr = g.createLinearGradient(0, 0, 0, 512);
  gr.addColorStop(0, '#5a8dff'); gr.addColorStop(0.55, '#9cc2ff'); gr.addColorStop(1, '#dbe9ff');
  g.fillStyle = gr; g.fillRect(0, 0, 4, 512);
  SKY.day = new THREE.CanvasTexture(cv); SKY.day.encoding = THREE.sRGBEncoding;
  const nv = document.createElement('canvas'); nv.width = 256; nv.height = 256;
  const n = nv.getContext('2d'), ng = n.createLinearGradient(0, 0, 0, 256);
  ng.addColorStop(0, '#070a22'); ng.addColorStop(0.6, '#141a40'); ng.addColorStop(1, '#262d5c');
  n.fillStyle = ng; n.fillRect(0, 0, 256, 256);
  const r = rng(5150);
  for (let i = 0; i < 140; i++) { const y = Math.floor(Math.pow(r(), 1.6) * 190), x = Math.floor(r() * 256); n.globalAlpha = 0.45 + r() * 0.55; n.fillStyle = r() < 0.2 ? '#fff3c4' : '#ffffff'; n.fillRect(x, y, 1, 1); }
  n.globalAlpha = 1;
  SKY.night = new THREE.CanvasTexture(nv); SKY.night.encoding = THREE.sRGBEncoding;
  SKY.night.magFilter = THREE.NearestFilter; SKY.night.minFilter = THREE.NearestFilter; SKY.night.generateMipmaps = false;
  scene.background = SKY.day;
})();
''')

# shading: one shared tint uniform turns every block material into night without touching emissive glow
rep('function cel(params) {', 'const SCENE_TINT = { value: new THREE.Vector3(1, 1, 1) };\nfunction cel(params) {')
rep('''  m.onBeforeCompile = sh => {
    sh.fragmentShader = sh.fragmentShader.replace(''', '''  m.onBeforeCompile = sh => {
    sh.uniforms.uTint = SCENE_TINT;
    sh.fragmentShader = 'uniform vec3 uTint;\\n' + sh.fragmentShader.replace(''')
rep("vec3( 1.0, 0.99, 0.96 ), lit ) + totalEmissiveRadiance;');", "vec3( 1.0, 0.99, 0.96 ), lit ) * uTint + totalEmissiveRadiance;');")

# instanced sets live far from the origin, so never cull them by the shared unit box
rep('m.count = items.length; m.castShadow = cast !== false;', 'm.count = items.length; m.frustumCulled = false; m.castShadow = cast !== false;')

# new hair styles: a full-face racing helmet and a fedora
rep('''  chimp(g, H, O, d) {''', '''  helmet(g, H, O, d) {
    for (const k in H) noise(g, ...H[k], d.helmetHex, 0.08, 90);
    const f = H.front; noise(g, f[0], f[1] + 3, 8, 3, d.visorHex, 0.05, 91);
    noise(g, H.left[0], H.left[1] + 3, 3, 3, d.visorHex, 0.05, 92); noise(g, H.right[0] + 5, H.right[1] + 3, 3, 3, d.visorHex, 0.05, 93);
    g.fillStyle = 'rgba(255,255,255,.5)'; g.fillRect(f[0] + 1, f[1] + 3, 2, 1);
    g.fillStyle = hexCss(d.stripeHex); g.fillRect(H.top[0] + 3, H.top[1], 2, 8); g.fillRect(f[0] + 3, f[1], 2, 3); g.fillRect(H.back[0] + 3, H.back[1], 2, 8);
  },
  fedora(g, H, O, d) {
    HAIR.short(g, H, O, d);
    noise(g, ...O.top, d.hatHex, 0.08, 94);
    for (const k of ['front', 'back', 'left', 'right']) { noise(g, O[k][0], O[k][1], 8, 1, d.hatHex, 0.08, 95); noise(g, O[k][0], O[k][1] + 1, 8, 1, d.bandHex, 0.08, 96); }
  },
  chimp(g, H, O, d) {''')

# faces: a hidden face under a helmet, glasses, beard and mustache
rep('  g.putImageData(c.faceBase, F[0], F[1]);\n', '  g.putImageData(c.faceBase, F[0], F[1]);\n  if (d.noFace) { c.tex.needsUpdate = true; return; }\n')
rep('  if (d.blush) { P(1, 5, d.blush); P(6, 5, d.blush); }\n', '''  if (d.blush) { P(1, 5, d.blush); P(6, 5, d.blush); }
  if (d.beard) { const bh = hexCss(d.beardHex); for (let x = 0; x < 8; x++) { P(x, 7, bh); if (x < 2 || x > 5) P(x, 6, bh); } P(0, 5, bh); P(7, 5, bh); }
  if (d.mustache) { const mh = hexCss(d.hairHex); [2, 3, 4, 5].forEach(x => P(x, 5, mh)); }
''')
rep('  else eyesOpen(false);\n', "  else eyesOpen(!!d.bigEyes);\n  if (ex === 'guino') { P(1, 3, white); P(2, 3, white); P(1, 4, white); P(2, 4, pupil); P(5, 4, dark); P(6, 4, dark); P(5, 3, dark); }\n  if (d.glasses) { const fr = '#2a2733'; [0, 3, 4, 7].forEach(x => P(x, 4, fr)); [1, 2, 5, 6].forEach(x => P(x, 3, fr)); }\n")
rep("const white = '#f6f6f6', pupil = d.eye", "const white = d.eyeWhite || '#f6f6f6', pupil = d.eye")
# an optional PS1 look: low-res render with big pixels, vertices snapped to that grid, 15-bit colour with ordered dither
rep("function cel(params) {", "const PS1_SNAP = { value: new THREE.Vector3(0, 160, 120) };\nfunction cel(params) {")
rep("  m.onBeforeCompile = sh => {\n    sh.uniforms.uTint = SCENE_TINT;\n", "  m.onBeforeCompile = sh => {\n    sh.uniforms.uTint = SCENE_TINT; sh.uniforms.uSnap = PS1_SNAP;\n    sh.vertexShader = 'uniform vec3 uSnap;\\n' + sh.vertexShader.replace('#include <project_vertex>', '#include <project_vertex>\\n  if (uSnap.x > 0.5) { vec4 sp = gl_Position; sp.xy = floor(sp.xy / sp.w * uSnap.yz + 0.5) / uSnap.yz * sp.w; gl_Position = sp; }');\n")
# characters with decal eyes skip the face texture's eyes and brows
rep("  if (ex === 'feliz') { closed(); P(2, 3, dark); P(5, 3, dark); }\n", "  if (!d.eyeDecal) {\n  if (ex === 'feliz') { closed(); P(2, 3, dark); P(5, 3, dark); }\n")
rep("  if (ex === 'sorpresa') { [1, 2, 5, 6].forEach(x => P(x, 2, brow)); }\n", "  if (ex === 'sorpresa') { [1, 2, 5, 6].forEach(x => P(x, 2, brow)); }\n  }\n")
rep("  if (mo) { [2, 3, 4, 5].forEach(x => P(x, 6, '#6e1d27'));", "  if (d.noMouth) { /* the snout does the talking */ }\n  else if (mo) { [2, 3, 4, 5].forEach(x => P(x, 6, '#6e1d27'));")
rep("  const key = c.expr + (mo ? '1' : '0') + (blink ? 'b' : '');", "  c.mo = mo; if (c.d.tick) c.d.tick(c, now);\n  const key = c.expr + (mo ? '1' : '0') + (blink ? 'b' : '');")
rep("  else if (ex === 'feliz') { P(2, 5, mouth);", "  else if (ex === 'feliz' || ex === 'guino') { P(2, 5, mouth);")
rep("const mo = c.anim === 'talk' && (", "const mo = (c.anim === 'talk' || c.anim === 'rant' || c.speaking) && (")
rep('function makeChar(id, def, scale) { const c = buildCharacter(id, def); c.root.scale.setScalar(scale); return c; }',
    'function makeChar(id, def, scale) { const c = buildCharacter(id, def); c.root.scale.setScalar(scale); c.baseScale = scale; return c; }')

# the old demo's cast definitions are replaced by the channel's own cast
rep_between('const SKIN = 0xf0c09a;', '// ---------- poses', '')

# more particles for fireworks
rep('for (let i = 0; i < 90; i++) { const m = new THREE.Mesh(FX_GEO', 'for (let i = 0; i < 0; i++) { const m = new THREE.Mesh(FX_GEO')

# islands can use other block sets (sand for the desert)
rep('function voxelIsland(cx, cz, R, B, seed) {', 'function voxelIsland(cx, cz, R, B, seed, M) {')
rep("  instanced(BOX1, GRASS_MATS, grass); instanced(BOX1, tmat('dirt'), dirt); instanced(BOX1, tmat('stone'), stone);",
    "  instanced(BOX1, M ? M.top : GRASS_MATS, grass); instanced(BOX1, M ? M.mid : tmat('dirt'), dirt); instanced(BOX1, M ? M.low : tmat('stone'), stone);")

# crowd excitement comes from the scene
rep('function updateCrowd(t) {\n  const e = t < 24.5 ? 0.35 : t < 27.4 ? 0.9 : t < 30.4 ? 0.15 : t < 31.4 ? 0.4 : 1;\n', 'function updateCrowd(t, e) {\n')

# keep trees off the hill and the light towers
rep('(Math.abs(x) < 8 && z > 5 && z < 12)) continue;',
    '(Math.abs(x) < 8 && z > 5 && z < 12) || Math.hypot(x + 36, z - 27) < 9 || [[-17, 22], [17, 22], [-34, -16], [34, -6]].some(([a, b]) => Math.hypot(x - a, z - b) < 3)) continue;')

# the big screen can switch between THUNDERHEAD, GRAN PREMIO and TRAMPA
rep_between('(function sign() {', '(function scenery() {', '''const SIGN = { tex: {}, mat: null, cur: '' };
(function sign() {
  const mk = (bg, fg, shadow, text, bolt) => canvasTex(512, 96, (g, w, h) => {
    noise(g, 0, 0, w, h, bg, 0.05, 120);
    if (bolt) { g.fillStyle = '#ffe14a'; [[22, 10, 14, 10], [16, 20, 14, 10], [10, 30, 30, 8], [24, 38, 12, 10], [18, 48, 12, 10], [12, 58, 12, 10], [8, 68, 10, 10]].forEach(q => g.fillRect(...q)); }
    const x0 = bolt ? 60 : w / 2;
    g.textAlign = bolt ? 'left' : 'center'; g.textBaseline = 'middle'; fitFont(g, text, w - (bolt ? 80 : 40), bolt ? 34 : 44);
    g.fillStyle = shadow; g.fillText(text, x0 + 4, h / 2 + 4); g.fillStyle = fg; g.fillText(text, x0, h / 2);
  }, true);
  SIGN.tex.thunder = mk(0x4a2a9a, '#ffffff', '#2a1460', 'THUNDERHEAD', true);
  SIGN.tex.gp = mk(0x1a3a7a, '#ffe14a', '#0a1a40', 'GRAN PREMIO', false);
  SIGN.tex.trampa = mk(0xb01a2a, '#ffffff', '#4a0a10', 'TRAMPA', false);
  for (const x of [-5, 5]) box(0.45, 4.4, 0.45, [tmat('logSide'), tmat('logSide'), tmat('logTop'), tmat('logTop'), tmat('logSide'), tmat('logSide')], world, [x, 2.2, 8.4], { tile: 0.45 });
  box(12.6, 3.3, 0.3, tmat('planks'), world, [0, 4.4, 8.4], { tile: 1 });
  SIGN.mat = cel({ map: SIGN.tex.thunder, emissive: lin(0x222222), emissiveMap: SIGN.tex.thunder });
  const face = new THREE.Mesh(new THREE.PlaneGeometry(12, 2.8), SIGN.mat);
  face.position.set(0, 4.4, 8.56); world.add(face);
})();
function setSign(k) { if (SIGN.cur === k) return; SIGN.cur = k; SIGN.mat.map = SIGN.tex[k]; SIGN.mat.emissiveMap = SIGN.tex[k]; SIGN.mat.needsUpdate = true; }
''')
rep('sunSprite.position.set(300, 330, -60); sunSprite.scale.set(46, 46, 1); world.add(sunSprite);',
    'sunSprite.position.set(300, 330, -60); sunSprite.scale.set(46, 46, 1); world.add(sunSprite); SUN_SPRITE = sunSprite;')

rep('  instanced(BOX1, cel({ color: lin(0xffffff), transparent: true, opacity: 0.88 }), clouds, false);', '  CLOUDS = instanced(BOX1, cel({ color: lin(0xffffff), transparent: true, opacity: 0.88 }), clouds, false);')


# Cube News keeps the engine (renderer, shading, textures, characters, poses, islands, trees)
# and drops the Thunderhead circuit, its stands and the old garage set.
a = eng.index('// ---------- Thunderhead: the track ----------'); b = eng.index('// ---------- voxel islands, trees, clouds')
eng = eng[:a] + eng[b:]
a = eng.index('voxelIsland(0, 0, 48, 2, 9);')
eng = eng[:a]
# instanced blocks can be built into a diorama group
rep('m.receiveShadow = true; world.add(m); return m;', 'm.receiveShadow = true; (INST_PARENT || world).add(m); return m;')
rep('function instanced(geo, material, items, cast) {', 'let INST_PARENT = null;\nfunction instanced(geo, material, items, cast) {')

def build(head_file, show_files, out_rel, check_name):
    head = open(os.path.join(SP, head_file)).read()
    parts = [open(os.path.join(SP, f)).read() for f in ['story_core.js', 'news_world.js', 'news_world2.js'] + show_files]
    out = head.rstrip('\n') + '\n\n' + eng + '\n'.join(parts)
    dest = out_rel if os.path.isabs(out_rel) else os.path.join(REPO, out_rel)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    open(dest, 'w').write(out)
    js = out[out.index("<script>\n(function") + 8: out.rindex('</script>')]
    open(os.path.join(SP, check_name), 'w').write(js)
    print('ok', out_rel, len(out))

build('news_head.html', ['audio_core.js', 'jingle_data.js', 'news_show.js'], 'cube-news/index.html', 'news_check.js')
build('shorts_head.html', ['shorts_data.js', 'audio_core.js', 'shorts_audio.js', 'shorts_show.js'], os.path.join(SP, 'shorts_build.html'), 'shorts_build_check.js')
build('shorts_head.html', ['shorts_pub.js', 'audio_core.js'] + ([] if 'const MIX' in open(os.path.join(SP, 'shorts_pub.js')).read() else ['shorts_audio.js']) + ['shorts_show.js'], 'cube-news/shorts.html', 'shorts_check.js')
build('thumb_head.html', ['thumb_show.js'], os.path.join(SP, 'thumbs.html'), 'thumb_check.js')
build('brand_head.html', ['brand_show.js'], os.path.join(SP, 'brand.html'), 'brand_check.js')
build('mrnews_head.html', ['audio_core.js', 'mrnews_data.js', 'mrnews_show.js'], 'cube-news/mr-news.html', 'mrnews_check.js')
build('casting_head.html', ['audio_core.js', 'mascots_data.js', 'mascots.js', 'casting_show.js'], 'cube-news/mascotas.html', 'casting_check.js')
