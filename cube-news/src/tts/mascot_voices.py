# one introduction line per casting candidate, in each candidate's own voice
import numpy as np, soundfile as sf, subprocess, json, re
from kokoro_onnx import Kokoro
from gen import tidy
k = Kokoro('kokoro-v1.0.onnx', 'voices-v1.0.bin')
LINES = {'es': '¡Qué onda, cubos! Aquí Míster Nius, con las noticias de la semana.', 'en': "What's up, cubes! Mister News here, with this week's news."}
src = open('../mascots.js').read()
VOICES = {m.group(1): {'en': (m.group(2), float(m.group(3))), 'es': (m.group(4), float(m.group(5)))} for m in re.finditer(r"id: '(\w+)'.*?voice: \{ en: \['(\w+)', ([\d.]+)\], es: \['(\w+)', ([\d.]+)\] \}", src, re.S)}
assert len(VOICES) == 6, VOICES
for mid, vs in VOICES.items():
    for lang, (voice, pitch) in vs.items():
        x, sr = k.create(LINES[lang], voice=voice, speed=1.08, lang='en-us' if lang == 'en' else 'es'); sf.write('_m.wav', np.asarray(x), sr)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', '_m.wav', '-af', f'rubberband=pitch={pitch}:formant=shifted:pitchq=quality', '_m2.wav'], check=True)
        y, s2 = sf.read('_m2.wav'); y = tidy(np.asarray(y, dtype=np.float64), s2); sf.write(f'out/m_{mid}_{lang}.wav', y, s2)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'out/m_{mid}_{lang}.wav', '-ac', '1', '-ar', '24000', '-b:a', '64k', f'out/m_{mid}_{lang}.mp3'], check=True)
        print(mid, lang, voice, pitch, round(len(y) / s2, 2))
