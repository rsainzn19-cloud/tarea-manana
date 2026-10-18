from faster_whisper import WhisperModel
from script import SHORTS
m = WhisperModel('small', device='cpu', compute_type='int8')
for lang in ('en', 'es'):
    for sid, sh in SHORTS.items():
        for i, b in enumerate(sh['beats']):
            segs, _ = m.transcribe(f'out/{lang}_{sid}_{i}.wav', language=lang, beam_size=1)
            print(lang, sid, i, '|', ' '.join(s.text.strip() for s in segs))
