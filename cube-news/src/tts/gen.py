import os, re, json, wave, subprocess, numpy as np, soundfile as sf
from script import SHORTS
from kokoro_onnx import Kokoro
from piper import PiperVoice, SynthesisConfig
os.makedirs('out', exist_ok=True)
# Mr. News is a penguin: a young voice pitched up (formants too) so it sounds like a cartoon
VOICES = {'en': 'am_michael', 'es': 'em_alex'}
SQUEAK = {'en': 1.5, 'es': 1.5}
def squeak(x, sr, lang='en'):
    sf.write('_sq_in.wav', x, sr)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', '_sq_in.wav', '-af', f'rubberband=pitch={SQUEAK[lang]}:formant=shifted:pitchq=quality', '_sq_out.wav'], check=True)
    y, s2 = sf.read('_sq_out.wav'); return np.asarray(y, dtype=np.float64), s2
def spoken(d): return (d.get('say') or d['text']).replace('*', '').replace('…', '... ').replace('’', "'").strip()
def tidy(x, sr):
    # trim silence at both ends, then even out loudness between the two engines
    # keep a generous lead-in: quiet first consonants (s, f, h) sit under the threshold
    env = np.abs(x); thr = 0.012; idx = np.where(env > thr)[0]
    a, b = max(0, idx[0] - int(0.14 * sr)), min(len(x), idx[-1] + int(0.08 * sr)); x = x[a:b]
    rms = np.sqrt((x ** 2).mean()); x = x * (10 ** (-17 / 20) / max(rms, 1e-6)); peak = np.abs(x).max()
    if peak > 0.95: x = x * 0.95 / peak
    return x.astype(np.float32)
def main():
  kok = Kokoro('kokoro-v1.0.onnx', 'voices-v1.0.bin')
  meta = {}
  for lang in ('en', 'es'):
      meta[lang] = {}
      for sid, sh in SHORTS.items():
          durs = []
          for i, b in enumerate(sh['beats']):
              text = spoken(b[lang]); base = f'out/{lang}_{sid}_{i}'
              if lang == 'en':
                  x, sr = kok.create(text, voice=VOICES['en'], speed=1.1, lang='en-us')
              else:
                  x, sr = kok.create(text, voice=VOICES['es'], speed=1.08, lang='es')
              x, sr = squeak(np.asarray(x, dtype=np.float64), sr, lang); x = tidy(x, sr); sf.write(base + '.wav', x, sr)
              subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', base + '.wav', '-ac', '1', '-ar', '24000', '-b:a', '56k', base + '.mp3'], check=True)
              durs.append(round(len(x) / sr, 3))
          meta[lang][sid] = durs
          print(lang, sid, durs, round(sum(durs), 1))
  json.dump(meta, open('out/meta.json', 'w'), indent=1)
  # the jingle shout and the catchphrases
  from script import PHRASES
  x, sr = kok.create('This is Cube News!', voice=VOICES['en'], speed=1.0, lang='en-us'); x, sr = squeak(np.asarray(x, dtype=np.float64), sr); x = tidy(x, sr); sf.write('out/brand.wav', x, sr)
  subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', 'out/brand.wav', '-ac', '1', '-ar', '24000', '-b:a', '64k', 'out/brand.mp3'], check=True)
  for ph in PHRASES:
    for lang in ('en', 'es'):
      x, sr = kok.create(spoken(ph[lang]), voice=VOICES[lang], speed=1.0, lang='en-us' if lang == 'en' else 'es'); x, sr = squeak(np.asarray(x, dtype=np.float64), sr, lang); x = tidy(x, sr)
      base = f"out/ph_{lang}_{ph['id']}"; sf.write(base + '.wav', x, sr)
      subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', base + '.wav', '-ac', '1', '-ar', '24000', '-b:a', '64k', base + '.mp3'], check=True)
      print('phrase', lang, ph['id'], round(len(x) / sr, 2))

if __name__ == '__main__':
  main()
