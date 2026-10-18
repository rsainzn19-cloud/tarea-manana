
// ---------- this edition: ten verified stories, strongest hook first ----------
const STORIES = [
  { d: 10, cat: 'Mundo', title: 'Piloto contra piloto en pleno vuelo', big: 'CAÍDA', small: '33,000 → 17,000 PIES',
    subs: ['En un vuelo de FlyDubai, uno de los pilotos apuñaló al otro y trató de estrellar el avión.', 'Iba de Dubái a Tel Aviv con unos 170 pasajeros y bajó de 33,000 a 17,000 pies en cerca de un minuto.', 'Un pasajero y la tripulación sometieron al atacante; el avión aterrizó en Tabuk, Arabia Saudita, y el motivo sigue sin aclararse.'],
    src: [['AP / PBS', 'https://www.pbs.org/newshour/world/what-to-know-about-the-terrifying-flydubai-flight-forced-to-land-in-saudi-arabia'], ['CNN', 'https://www.cnn.com/2026/09/30/middleeast/flydubai-israel-plane-pilot-hijacking-incident-latam-hnk-intl'], ['CBS News', 'https://www.cbsnews.com/news/tel-aviv-israel-flydubai-flight-saudi-arabia-diverted-pilots/'], ['Al Jazeera', 'https://www.aljazeera.com/news/2026/9/30/what-happened-on-diverted-flydubai-flight-from-dubai-to-tel-aviv']] },
  { d: 11, d2: 2, cut2: 11.3, cat: 'Mundo', title: 'La moneda de Irán toca un nuevo mínimo', big: '2.5 M', small: 'RIALES POR DÓLAR',
    subs: ['En Irán, un solo dólar ya cuesta más de 2.5 millones de riales: nuevo mínimo histórico.', 'La golpean la guerra, el bloqueo naval de Estados Unidos a su petróleo y las sanciones.', 'Catar media entre los dos países después de que Trump rechazara una propuesta iraní para reabrir el estrecho de Ormuz en siete días.'],
    src: [['AP / PBS', 'https://www.pbs.org/newshour/world/irans-currency-hits-a-new-record-low-as-war-erodes-the-countrys-economic-stability'], ['Iran International', 'https://www.iranintl.com/en/202609280172'], ['Democracy Now!', 'https://www.democracynow.org/2026/9/30/headlines/iranian_rial_hits_record_low_against_the_dollar_as_war_takes_heavy_toll_on_economy']] },
  { d: 9, cat: 'Ciencia', title: 'Será papá… desde el espacio', big: 'PAPÁ', small: 'EN ÓRBITA · CREW-13',
    subs: ['El canadiense Joshua Kutryk viajó a la Estación Espacial con Crew-13… y su tercer hijo nacerá mientras él está en órbita.', 'Su esposa espera al bebé a finales de noviembre; será apenas la cuarta persona en ser papá desde el espacio.', 'La nave llegó en solo ocho horas, un récord, y la tripulación pasará unos seis meses arriba.'],
    src: [['AP vía KSAT', 'https://www.ksat.com/news/world/2026/10/01/spacex-launches-4-astronauts-on-a-fast-track-to-the-international-space-station/'], ['Space.com', 'https://www.space.com/space-exploration/human-spaceflight/spacex-crew-13-astronaut-mission-iss-launch'], ['Yahoo Noticias Canadá', 'https://ca.news.yahoo.com/astronaut-crew-begins-6-month-225618780.html'], ['WSGW (AP)', 'https://www.wsgw.com/crew-docks-at-space-station-with-astronaut-wholl-miss-birth-of-3rd-child/']] },
  { d: 1, cat: 'Mundo', title: 'EE. UU. termina su salida de Irak', big: 'IRAK', small: '2003 → 2026',
    subs: ['Estados Unidos terminó de retirar sus últimas tropas de Irak el 30 de septiembre.', 'Salieron de la base aérea de Erbil, más de 20 años después de la invasión de 2003, como se pactó con Bagdad en 2024.', 'Ahora la seguridad del país queda en manos del gobierno iraquí.'],
    src: [['CNN', 'https://www.cnn.com/2026/09/30/middleeast/us-military-withdrawal-iraq-intl'], ['Breaking Defense', 'https://breakingdefense.com/2026/09/us-withdraws-last-remaining-troops-out-of-iraq/'], ['NPR', 'https://www.npr.org/2026/09/30/nx-s1-5983426/u-s-withdraws-final-troops-from-iraq']] },
  { d: 5, cat: 'América', title: 'Brasil elige presidente este domingo', big: '4 OCT', small: 'ELECCIONES EN BRASIL',
    subs: ['Este domingo 4 de octubre, Brasil elige presidente, gobernadores y Congreso.', 'Lula busca un cuarto mandato y en la mayoría de las encuestas va parejo con el senador Flávio Bolsonaro.', 'Si nadie pasa del 50% de los votos, habrá segunda vuelta el 25 de octubre.'],
    src: [['Americas Quarterly', 'https://www.americasquarterly.org/article/brazil-meet-the-candidates-2026/'], ['Wikipedia', 'https://en.wikipedia.org/wiki/2026_Brazilian_general_election']] },
  { d: 6, cat: 'México', title: 'El FMI baja su pronóstico para México', big: '1.5%', small: 'CRECIMIENTO 2026',
    subs: ['El FMI recortó su pronóstico: ahora espera que México crezca 1.5% en 2026 y 1.8% en 2027.', 'Antes calculaba 1.6% y 2.2%; lo atribuye a la alta incertidumbre externa.', 'Reconoció avances, pero pidió acelerar el ajuste fiscal para bajar la deuda.'],
    src: [['El Informador', 'https://www.informador.mx/economia/fmi-recorta-pronostico-de-crecimiento-de-mexico-en-2026-20261002-0139.html'], ['Crónica', 'https://www.cronica.com.mx/nacional/2026/10/02/fmi-preve-crecimiento-de-15-para-mexico-en-2026-y-reconoce-avances-en-consolidacion-fiscal/'], ['La Política Online', 'https://www.lapoliticaonline.com/mexico/economia-mx/el-fmi-advierte-a-amador-debe-acelerar-el-ajuste-fiscal-para-bajar-la-deuda/']] },
  { d: 7, cat: 'México', title: '2 de octubre: 58 años de Tlatelolco', big: '58 AÑOS', small: '2 DE OCTUBRE DE 1968',
    subs: ['Estudiantes y organizaciones marcharon en la Ciudad de México a 58 años de la matanza de Tlatelolco.', 'Salieron de la Plaza de las Tres Culturas rumbo al Zócalo, bajo la consigna «Ni perdón ni olvido».', 'Hubo un enfrentamiento con policías cerca del metro Tlatelolco y destrozos en algunos negocios.'],
    src: [['Expansión Política', 'https://politica.expansion.mx/mexico/2026/10/02/marcha-2-octubre-cdmx-confrontacion-metro-tlatelolco'], ['Infobae', 'https://www.infobae.com/mexico/2026/10/02/marcha-del-2-de-octubre-en-cdmx-ruta-horarios-calles-cerradas-y-alternativas-viales/']] },
  { d: 8, cat: 'Clima', title: 'Los glaciares suizos pierden 5.5% en un año', big: '−5.5%', small: 'HIELO EN SUIZA',
    subs: ['Los glaciares suizos perdieron 5.5% de su hielo en un solo año.', 'Es la segunda peor pérdida registrada, solo detrás de 2022, tras el verano más caluroso del que se tiene registro en Suiza.', 'En cinco años, los Alpes suizos perdieron casi una quinta parte de su hielo.'],
    src: [['ABC News (informe GLAMOS)', 'https://abcnews.com/International/extraordinary-ice-loss-measured-swiss-glaciers-report-finds/story?id=136924518'], ['NPR', 'https://www.npr.org/2026/09/30/nx-s1-5981403/swiss-glaciers-climate-change'], ['Swissinfo', 'https://www.swissinfo.ch/eng/glaciers-permafrost/a-fifth-of-swiss-alpine-ice-has-vanished-in-five-years/92132132']] },
  { d: 3, cat: 'Mundo', title: 'Ucrania estrena su propio misil balístico', big: 'FP-7', small: 'ALCANCE: 250 KM',
    subs: ['Ucrania usó en combate, por primera vez, su propio misil balístico: el FP-7.', 'Lo fabrica una empresa ucraniana y tiene un alcance de unos 250 kilómetros.', 'Zelenski lo anunció el 1 de octubre, pero no dijo qué objetivo atacó.'],
    src: [['Defense News', 'https://www.defensenews.com/industry/techwatch/2026/10/01/ukraine-deploys-fp-7-tactical-ballistic-missile-in-combat-for-first-time-zelenskyy-says/'], ['Kyiv Independent', 'https://kyivindependent.com/were-not-telling-ukraine-wont-say-what-first-fp-7-ballistic-missile-strike-hit/']] },
  { d: 4, cat: 'Mundo', title: 'Etiopía y Eritrea rompen relaciones', big: 'RUPTURA', small: 'ETIOPÍA · ERITREA',
    subs: ['Etiopía cerró su embajada en Eritrea y expulsó a diez diplomáticos; acusa a su vecino de apoyar a los rebeldes de Tigray.', 'Eritrea respondió rompiendo relaciones. Horas antes hubo explosiones y ataques con drones en Adís Abeba.', 'Es su peor choque desde la paz que firmaron en 2018.'],
    src: [['Al Jazeera', 'https://www.aljazeera.com/news/2026/10/1/eritrea-severs-diplomatic-ties-with-ethiopia-in-tit-for-tat-move'], ['Democracy Now!', 'https://www.democracynow.org/2026/10/2/headlines/ethiopia_and_eritrea_sever_diplomatic_ties_as_tigray_rebels_claim_drone_strikes_on_addis_ababa']] }
];
const STORY_DUR = 18, STUDIO_CUT = 4.5;
STORIES.forEach(st => { st.tex = wallTex({ cat: st.cat.toUpperCase(), big: st.big, small: st.small }); });
const TEX_HELLO = wallTex({ cat: 'EDICIÓN SEMANAL', big: 'CUBE NEWS', small: 'SÁBADO 3 DE OCTUBRE DE 2026' });
const TEX_BYE = wallTex({ cat: 'HASTA LA PRÓXIMA', big: '¿Y AHORA?', small: 'DÉJANOS TU NOTICIA EN LOS COMENTARIOS' });
function setWall(tex) { if (WALL_MAT.map === tex) return; WALL_MAT.map = tex; WALL_MAT.emissiveMap = tex; WALL_MAT.needsUpdate = true; }
const studioCam = (lt, v, push) => {
  const k = ease(clamp(lt / (push || 4.5)));
  return v ? shot(V(-0.85, 1.05, lerp(2.0, 1.6, k)), V(-0.75, 1.0, -2), 52, V(0, 0, -1), 8) : shot(V(0.4, 1.15, lerp(2.9, 2.55, k)), V(0.3, 0.95, -2), 34, V(0, 0, -1), 8);
};
function anchorAt(lt, anim) { stand(anchor, ANCHOR_SPOT, 0, anim || 'talk', [[0, 'feliz'], [0.6, 'serio']], lt); }

const SCENES = [{ name: 'Bienvenida', dur: 8, env: 'studio',
  subs: [[0.3, 4.3, 'Un piloto que intenta estrellar un avión, un dólar que cuesta 2.5 millones y un astronauta que será papá en el espacio.'], [4.4, 7.8, '¡Qué onda, cubos! Aquí Mr. News: es sábado 3 de octubre y esto es Cube News.']],
  run(lt, v) {
    STUDIO.visible = true; setWall(TEX_HELLO); stand(anchor, ANCHOR_SPOT, 0, [[0, 'wave'], [2.2, 'talk']], 'feliz', lt);
    const k = ease(clamp(lt / 7.5));
    return v ? shot(V(...lerpA([3, 2.6, 7.5], [-0.6, 1.2, 2.6], k)), V(-0.75, 1.0, -1.8), 56, V(0, 0, -1), 10) : shot(V(...lerpA([5.5, 2.8, 6.5], [1.1, 1.3, 3.1], k)), V(0.1, 1.05, -1.8), 40, V(0, 0, -1), 10);
  } }];
// the show opens with the logo: the Earth cube drops in to the jingle
const INTRO_DUR = 4.5;
SCENES.unshift({ name: 'Intro', dur: INTRO_DUR, env: 'logo', intro: true, fx: () => LOGO.fx, subs: [],
  run(lt, v) { LOGO.g.visible = true; LOGO.update(lt); return LOGO.cam(lt, v); } });
STORIES.forEach((st, i) => {
  // a story can hand over to a second diorama for its last beat
  const dio = D[st.d], dioAt = lt => st.d2 && lt >= st.cut2 ? D[st.d2] : dio;
  SCENES.push({ name: st.title, story: st, index: i, dur: STORY_DUR, dio,
    env: lt => lt < STUDIO_CUT ? 'studio' : val(dioAt(lt).env, lt, 'day'),
    subs: [[0.3, 4.4, st.subs[0]], [4.6, 11.2, st.subs[1]], [11.4, 17.8, st.subs[2]]],
    shake: dio.shake, impact: dio.impact, flash: dio.flash, fx: dio.fx,
    run(lt, v, now) {
      if (lt < STUDIO_CUT) { STUDIO.visible = true; setWall(st.tex); anchorAt(lt); return studioCam(lt, v); }
      const dd = dioAt(lt); dd.g.visible = true; dd.update(lt, now); const c = dd.cam(lt, v); c.box = val(dd.box, lt, 14); return c;
    } });
});
SCENES.push({ name: 'Despedida', dur: 8, env: 'studio',
  subs: [[0.4, 3.9, '¿Qué noticia quieres que expliquemos a fondo? Déjala en los comentarios.'], [4.0, 7.8, 'Soy Mr. News. ¡Quédate cuadrado y bien informado!']],
  run(lt, v) { STUDIO.visible = true; setWall(TEX_BYE); stand(anchor, ANCHOR_SPOT, 0, [[0, 'talk'], [4.2, 'point'], [6.4, 'wave']], [[0, 'feliz'], [6.2, 'guino']], lt); return studioCam(lt, v, 8); } });
const val = (x, lt, d) => x == null ? d : typeof x === 'function' ? x(lt) : x;
let acc = 0;
SCENES.forEach(s => { s.a = acc; acc += s.dur; s.b = acc; s.fxList = s.fx ? s.fx() : []; });
const FIRST_STORY = 2, TOTAL = acc, SHORT_STORY = 0, CLIP = [SCENES[SHORT_STORY + FIRST_STORY].a, SCENES[SHORT_STORY + FIRST_STORY].b];
const CUTS = SCENES.slice(1).map(s => s.a);

// ---------- looks for each place ----------
const ENVS = {
  studio: { bg: 'studio', fog: 0x0a1230, near: 30, far: 140, tint: [1, 1, 1], bloom: 0.6, vig: 0.35 },
  day: { bg: 'day', fog: 0xc9dcff, near: 90, far: 260, tint: [1, 1, 1], bloom: 0.35, vig: 0.3 },
  logo: { bg: 'logo', fog: 0x8fd0ff, near: 300, far: 600, tint: [1, 1, 1], bloom: 0.45, vig: 0.12 },
  dusk: { bg: 'dusk', fog: 0xd8907a, near: 50, far: 200, tint: [1.02, 0.84, 0.74], bloom: 0.55, vig: 0.4, warm: 0.35 },
  space: { bg: 'night', fog: 0x000000, near: 400, far: 900, tint: [1.05, 1.05, 1.1], bloom: 0.8, vig: 0.45 }
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

// ---------- UI ----------
const S = { fmt: 'h', playing: !REDUCED, t: REDUCED ? SCENES[SHORT_STORY + FIRST_STORY].a + 7 : 0, range: [0, TOTAL], clip: false, retro: false };
const ICON_PLAY = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M4 2.5v11l9-5.5z"/></svg>';
const ICON_PAUSE = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3.5 2.5h3v11h-3zM9.5 2.5h3v11h-3z"/></svg>';
const el = { logo: $('logoTxt'), sound: $('soundBtn'), pill: $('storyPill'), tc: $('tc'), tcPill: $('tcPill'), sub: $('sub'), play: $('play'), scrub: $('scrub'), clip: $('clipBtn'),
  lower: $('lower'), lTag: $('lTag'), lHead: $('lHead'), lSrc: $('lSrc'), crawl: $('crawl'), wipe: $('wipe') };
const fmtT = s => Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0');
// ticker text twice so the crawl can loop without a gap
(function ticker() {
  const items = STORIES.map(st => st.title);
  for (let k = 0; k < 2; k++) items.forEach(t => { const s = document.createElement('span'); s.textContent = t; el.crawl.appendChild(s); });
})();
const WIPE_CELLS = [];
for (let r = 0; r < 5; r++) for (let c = 0; c < 8; c++) { const i = document.createElement('i'); el.wipe.appendChild(i); WIPE_CELLS.push({ i, d: (r + c) / 11 }); }
// sources list in the notes
(function sources() {
  const ol = $('sources');
  STORIES.forEach(st => {
    const li = document.createElement('li'); const b = document.createElement('strong'); b.textContent = st.title + ': '; li.appendChild(b);
    st.src.forEach(([name, url], k) => { if (k) li.appendChild(document.createTextNode(', ')); const a = document.createElement('a'); a.href = url; a.target = '_blank'; a.rel = 'noopener'; a.textContent = name; li.appendChild(a); });
    ol.appendChild(li);
  });
})();
let lastScene = -1, lastSub = null, lastTc = '', lastLower = null, lastWipe = -1, crawlW = 0;
const chapBtns = STORIES.map((st, k) => {
  const sc = SCENES[k + FIRST_STORY], li = document.createElement('li'), btn = document.createElement('button');
  btn.innerHTML = '<span class="n">' + (k + 1) + '</span><span><span class="k">' + st.cat + '</span>' + st.title + (k === SHORT_STORY ? '<span class="tag-s">Short</span>' : '') + '</span><span class="tm">' + fmtT(sc.a) + '</span>';
  btn.addEventListener('click', () => { if (S.clip) setClip(false); S.t = sc.a + 0.001; lastScene = -1; syncUI(); });
  li.appendChild(btn); $('shots').appendChild(li); return btn;
});
function setFmt(f) { S.fmt = f; stage.dataset.format = f; crawlW = 0; syncUI(); }
function setClip(on) { S.clip = on; S.range = on ? CLIP.slice() : [0, TOTAL]; S.t = S.range[0]; S.playing = true; lastScene = -1; setFmt(on ? 'v' : 'h'); }
function syncUI() {
  $('retro').setAttribute('aria-pressed', S.retro);
  $('fmtH').setAttribute('aria-pressed', S.fmt === 'h'); $('fmtV').setAttribute('aria-pressed', S.fmt === 'v');
  el.play.innerHTML = S.playing ? ICON_PAUSE + 'Pausar' : ICON_PLAY + 'Reproducir';
  el.clip.textContent = S.clip ? 'Volver al noticiero completo' : 'Recortar a Short (noticia 1)';
  el.clip.setAttribute('aria-pressed', S.clip);
  el.sound.setAttribute('aria-pressed', JG.on); el.sound.textContent = JG.on ? 'Sonido activado' : 'Activar sonido';
  el.scrub.min = S.range[0]; el.scrub.max = S.range[1];
  el.tcPill.classList.toggle('live', S.playing);
}
$('fmtH').addEventListener('click', () => setFmt('h'));
$('fmtV').addEventListener('click', () => setFmt('v'));
$('retro').addEventListener('click', () => { S.retro = !S.retro; resize(); syncUI(); });
el.play.addEventListener('click', () => { S.playing = !S.playing; syncUI(); });
// sound: for now the episode plays the intro jingle; it follows the video clock, so scrubbing and pausing stay in sync
const JG = { ctx: null, on: false, buf: null, src: null, at: 0, off: 0 };
function jingleStop() { if (JG.src) { try { JG.src.stop(); } catch (e) { /* already ended */ } JG.src = null; } }
function jingleSync(sc, lt) {
  const want = JG.on && S.playing && sc.intro && JG.buf;
  if (JG.src && (!want || Math.abs(JG.off + JG.ctx.currentTime - JG.at - lt) > 0.25)) jingleStop();
  if (want && !JG.src && lt < INTRO_DUR - 0.2) { const s = JG.ctx.createBufferSource(); s.buffer = JG.buf; s.connect(JG.ctx.destination); s.start(0, lt); JG.src = s; JG.at = JG.ctx.currentTime; JG.off = lt; }
}
el.sound.addEventListener('click', async () => {
  if (!JG.ctx) { const AC = window.AudioContext || window.webkitAudioContext; if (!AC) { el.sound.disabled = true; return; } JG.ctx = new AC(); }
  JG.ctx.resume(); JG.on = !JG.on; syncUI();
  if (JG.on && !JG.buf) { try { JG.buf = await decodeB64(JG.ctx, JINGLE); } catch (e) { JG.on = false; syncUI(); } }
});
$('restart').addEventListener('click', () => { S.t = S.range[0]; S.playing = true; syncUI(); });
el.clip.addEventListener('click', () => setClip(!S.clip));
let scrubbing = false;
el.scrub.addEventListener('pointerdown', () => { scrubbing = true; });
window.addEventListener('pointerup', () => { scrubbing = false; });
el.scrub.addEventListener('input', () => { S.t = clamp(+el.scrub.value, S.range[0], S.range[1] - 0.001); });
const fsBtn = $('fs'), monitor = $('monitor');
if (!document.fullscreenEnabled || !monitor.requestFullscreen) fsBtn.hidden = true;
fsBtn.addEventListener('click', () => { if (document.fullscreenElement) document.exitFullscreen(); else monitor.requestFullscreen().catch(() => { fsBtn.hidden = true; }); });
document.addEventListener('keydown', e => {
  if (e.code !== 'Space' || /^(INPUT|BUTTON|TEXTAREA|A)$/.test(e.target.tagName)) return;
  e.preventDefault(); S.playing = !S.playing; syncUI();
});
function resize() {
  const w = stage.clientWidth, h = stage.clientHeight; if (!w || !h) return;
  renderer.setPixelRatio(S.retro ? clamp(300 / Math.min(w, h), 0.25, 1.5) : Math.min(window.devicePixelRatio || 1, 2));
  renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix(); canvas.classList.toggle('retro', S.retro); crawlW = 0;
}
new ResizeObserver(resize).observe(stage); resize();
if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { FONT_REDRAW.forEach(f => f()); crawlW = 0; });
function hud(i, sc, lt) {
  stage.classList.toggle('intro', !!sc.intro); logoText(el.logo, lt, !!sc.intro);
  if (i !== lastScene) {
    lastScene = i;
    const n = sc.story ? 'Noticia ' + (sc.index + 1) + ' de ' + STORIES.length : sc.name;
    el.pill.textContent = (S.clip ? 'Short · ' : '') + n;
    chapBtns.forEach((b, j) => b.setAttribute('aria-current', sc.story && j === sc.index ? 'true' : 'false'));
  }
  // lower third: the headline stays up for the whole story
  const low = sc.story && lt > 0.7 && lt < sc.dur - 0.3 ? sc.index : -1;
  if (low !== lastLower) {
    lastLower = low; el.lower.classList.toggle('on', low >= 0);
    if (low >= 0) { const st = STORIES[low]; el.lTag.textContent = st.cat; el.lHead.textContent = st.title; el.lSrc.textContent = 'Fuentes: ' + st.src.map(s => s[0]).join(' · '); }
  }
  // cube wipe across every cut between segments
  let wp = -1;
  if (!REDUCED) for (const tc of CUTS) { const p = (S.t - (tc - 0.4)) / 0.8; if (p >= 0 && p <= 1) { wp = p; break; } }
  if (wp !== lastWipe) {
    lastWipe = wp;
    for (const c of WIPE_CELLS) { let s = 0; if (wp >= 0) { const q = clamp((wp - c.d * 0.35) / 0.65); s = q < 0.5 ? easeOut(q * 2) : 1 - ease((q - 0.5) * 2); } c.i.style.transform = 'scale(' + (s * 1.02).toFixed(3) + ')'; }
  }
  // ticker crawl follows the video clock, so pausing stops it
  if (!crawlW) crawlW = el.crawl.scrollWidth / 2 || 1;
  el.crawl.style.transform = 'translateX(' + (-((S.t * 60) % crawlW)).toFixed(1) + 'px)';
  const s = sc.subs.find(x => lt >= x[0] && lt < x[1]), txt = s ? s[2] : '';
  if (txt !== lastSub) { lastSub = txt; el.sub.textContent = txt; el.sub.classList.toggle('on', !!txt); }
  const tc = fmtT(S.t - S.range[0]) + ' / ' + fmtT(S.range[1] - S.range[0]);
  if (tc !== lastTc) { lastTc = tc; el.tc.textContent = tc; }
  if (!scrubbing) el.scrub.value = S.t;
}
const sceneIndex = t => { for (let i = SCENES.length - 1; i >= 0; i--) if (t >= SCENES[i].a) return i; return 0; };
let last = performance.now();
const _look = V(0, 0, 0), _shk = V(0, 0, 0);
scene.autoUpdate = false;
function frame(ms) {
  const dt = clamp((ms - last) / 1000, 0, 0.05), now = ms / 1000; last = Math.max(last, ms);
  if (S.playing) { S.t += dt; if (S.t >= S.range[1]) S.t = S.range[0]; }
  const i = sceneIndex(S.t), sc = SCENES[i], lt = S.t - sc.a;
  hideAll();
  const env = val(sc.env, lt, 'studio'); setEnv(env);
  const c = sc.run(lt, S.fmt === 'v', now);
  jingleSync(sc, lt);
  for (const ch of ACTIVE) updateChar(ch, now);
  const sh = REDUCED ? 0 : shakeAt(sc.shake, lt);
  camera.position.copy(c.p); _look.copy(c.l);
  if (sh > 0) { _shk.set(Math.sin(now * 61) + Math.sin(now * 37.3), Math.sin(now * 53.7 + 1) + Math.sin(now * 29.1), Math.sin(now * 47.9 + 2)).multiplyScalar(sh * 0.5); camera.position.add(_shk); _look.add(_shk.multiplyScalar(0.6)); }
  if (camera.fov !== c.f) { camera.fov = c.f; camera.updateProjectionMatrix(); }
  camera.up.copy(c.up || UP); camera.lookAt(_look);
  aimSun(c.focus, c.box);
  scene.updateMatrixWorld();
  PART.begin(); drawFx2(sc.fxList, lt); for (const e of EMIT) e(now, lt); PART.end();
  let imp = 0; if (!REDUCED) for (const t0 of sc.impact || []) { const d = lt - t0; if (d >= 0 && d < 0.05) imp = 1; else if (d >= 0.1 && d < 0.14) imp = 0.85; }
  let fl = 0; if (!REDUCED) for (const t0 of sc.flash || []) { const d = lt - t0; if (d >= 0 && d < 0.4) fl = Math.max(fl, 0.8 * (1 - d / 0.4)); }
  POST.U.uImpact.value = imp; POST.U.uFlash.value = fl;
  POST.U.uAberr.value = sh * 12 + imp * 2.5;
  POST.U.uSat.value = 1.1; POST.U.uVig.value = ENVS[env].vig; POST.U.uWarm.value = ENVS[env].warm || 0; POST.U.uSepia.value = 0;
  POST.render();
  hud(i, sc, lt);
  requestAnimationFrame(frame);
}
syncUI();
requestAnimationFrame(frame);
})();
</script>
