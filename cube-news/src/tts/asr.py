from faster_whisper import WhisperModel
import sys
from script import SHORTS
ONLY = sys.argv[1:]  # python3 asr.py binance -> only that Short
m = WhisperModel('small', device='cpu', compute_type='int8')
for lang in ('en', 'es'):
    for sid, sh in SHORTS.items():
        if ONLY and sid not in ONLY: continue
        for i, b in enumerate(sh['beats']):
            segs, _ = m.transcribe(f'out/{lang}_{sid}_{i}.wav', language=lang, beam_size=1)
            print(lang, sid, i, '|', ' '.join(s.text.strip() for s in segs))
