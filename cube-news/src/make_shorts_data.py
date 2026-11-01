# Builds shorts_data.js (script + narration clips as base64 MP3) and shorts_pub.js (script + finished mixes).
# Incremental: a Short whose clips are not in tts/out (or mix not in vid/mix) keeps what the old .js files had,
# so a new Short only needs its own voices and mixes.
import json, base64, os, sys, re, glob
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'tts'))
from script import SHORTS
SP = os.path.dirname(os.path.abspath(__file__)); OUT = os.path.join(SP, 'tts', 'out'); MIXD = os.path.join(SP, 'vid', 'mix')
LANGS = ('es', 'en')
def const(path, name):
    if not os.path.exists(path): return None
    m = re.search(r'^const ' + name + r' = (.*);$', open(path, encoding='utf-8').read(), re.M)
    return json.loads(m.group(1)) if m else None
old_voice = const(os.path.join(SP, 'shorts_data.js'), 'VOICE') or {}
old_brand = const(os.path.join(SP, 'shorts_data.js'), 'BRAND_VOICE')
old_mix = const(os.path.join(SP, 'shorts_pub.js'), 'MIX') or {}
meta = {l: {} for l in LANGS}
for f in sorted(glob.glob(os.path.join(OUT, 'meta*.json'))):
    for l, d in json.load(open(f)).items(): meta[l].update(d)
b64 = lambda p: base64.b64encode(open(p, 'rb').read()).decode()
voice = {l: {} for l in LANGS}
for l in LANGS:
    for sid, sh in SHORTS.items():
        if sid in meta[l]:
            voice[l][sid] = [{'d': d, 'src': b64(os.path.join(OUT, f'{l}_{sid}_{i}.mp3'))} for i, d in enumerate(meta[l][sid])]
        else:
            voice[l][sid] = old_voice[l][sid]
        assert len(sh['beats']) == len(voice[l][sid]), (sid, l)
brand = b64(os.path.join(OUT, 'brand.mp3')) if os.path.exists(os.path.join(OUT, 'brand.mp3')) else old_brand
js = '// ---------- the scripts in both languages and the narration (Kokoro TTS, Apache-2.0 voices) ----------\n'
js += 'const SCRIPT = ' + json.dumps(SHORTS, ensure_ascii=False) + ';\n'
js += 'const VOICE = ' + json.dumps(voice, separators=(',', ':')) + ';\n'
js += 'const BRAND_VOICE = ' + json.dumps(brand) + ';\n'
open(os.path.join(SP, 'shorts_data.js'), 'w').write(js)
print('shorts_data.js', round(len(js) / 1e6, 2), 'MB')
# the published page: script, line lengths and the finished mixes (MP3)
durs = {l: {sid: [{'d': c['d']} for c in voice[l][sid]] for sid in SHORTS} for l in LANGS}
mix = {l: {} for l in LANGS}; missing = []
for l in LANGS:
    for sid in SHORTS:
        p = os.path.join(MIXD, f'{l}_{sid}.mp3')
        if os.path.exists(p): mix[l][sid] = b64(p)
        elif sid in old_mix.get(l, {}): mix[l][sid] = old_mix[l][sid]
        else: missing.append(f'{l}_{sid}')
if missing:
    # keep the old published file (and its mixes) until every Short has one
    print('no mix yet for', missing, ': shorts_pub.js left as it was'); sys.exit()

pub = '// ---------- the scripts in both languages, line lengths and the finished sound mixes ----------\n'
pub += 'const SCRIPT = ' + json.dumps(SHORTS, ensure_ascii=False) + ';\n'
pub += 'const VOICE = ' + json.dumps(durs, separators=(',', ':')) + ';\n'
pub += 'const MIX = ' + json.dumps(mix, separators=(',', ':')) + ';\n'
open(os.path.join(SP, 'shorts_pub.js'), 'w').write(pub)
print('shorts_pub.js', round(len(pub) / 1e6, 2), 'MB')
