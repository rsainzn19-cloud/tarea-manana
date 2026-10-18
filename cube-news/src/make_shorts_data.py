# Builds shorts_data.js: the bilingual script plus the narration clips as base64 MP3.
import json, base64, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'tts'))
from script import SHORTS
SP = os.path.dirname(os.path.abspath(__file__)); OUT = os.path.join(SP, 'tts', 'out')
meta = json.load(open(os.path.join(OUT, 'meta.json')))
voice = {lang: {sid: [{'d': d, 'src': base64.b64encode(open(os.path.join(OUT, f'{lang}_{sid}_{i}.mp3'), 'rb').read()).decode()} for i, d in enumerate(meta[lang][sid])] for sid in SHORTS} for lang in ('es', 'en')}
for sid, sh in SHORTS.items():
    for lang in ('es', 'en'): assert len(sh['beats']) == len(meta[lang][sid]), (sid, lang)
js = '// ---------- the scripts in both languages and the narration (Kokoro TTS, Apache-2.0 voices) ----------\n'
js += 'const SCRIPT = ' + json.dumps(SHORTS, ensure_ascii=False) + ';\n'
js += 'const VOICE = ' + json.dumps(voice, separators=(',', ':')) + ';\n'
js += 'const BRAND_VOICE = ' + json.dumps(base64.b64encode(open(os.path.join(OUT, 'brand.mp3'), 'rb').read()).decode()) + ';\n'
open(os.path.join(SP, 'shorts_data.js'), 'w').write(js)
print('shorts_data.js', round(len(js) / 1e6, 2), 'MB')
# the published page: script, line lengths and the six finished mixes (MP3)
durs = {lang: {sid: [{'d': d} for d in meta[lang][sid]] for sid in SHORTS} for lang in ('es', 'en')}
pub = '// ---------- the scripts in both languages, line lengths and the finished sound mixes ----------\n'
pub += 'const SCRIPT = ' + json.dumps(SHORTS, ensure_ascii=False) + ';\n'
pub += 'const VOICE = ' + json.dumps(durs, separators=(',', ':')) + ';\n'
MIXD = os.path.join(SP, 'vid', 'mix')
if all(os.path.exists(os.path.join(MIXD, f'{lang}_{sid}.mp3')) for lang in ('es', 'en') for sid in SHORTS):
    mix = {lang: {sid: base64.b64encode(open(os.path.join(MIXD, f'{lang}_{sid}.mp3'), 'rb').read()).decode() for sid in SHORTS} for lang in ('es', 'en')}
    pub += 'const MIX = ' + json.dumps(mix, separators=(',', ':')) + ';\n'
else:
    print('no mixes yet: the published page will mix in the browser')
    pub = pub  # falls back to live mixing, which needs the voice clips
    pub = js
open(os.path.join(SP, 'shorts_pub.js'), 'w').write(pub)
print('shorts_pub.js', round(len(pub) / 1e6, 2), 'MB')
