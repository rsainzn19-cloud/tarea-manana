import numpy as np, soundfile as sf, subprocess
from kokoro_onnx import Kokoro
from faster_whisper import WhisperModel
from gen import tidy
from sample import f0
k = Kokoro('kokoro-v1.0.onnx', 'voices-v1.0.bin'); m = WhisperModel('small', device='cpu', compute_type='int8')
EN = 'A pilot stabbed the other pilot mid-flight... and tried to crash the plane! Stay square!'
ES = 'Un piloto apuñaló a su compañero en pleno vuelo... y trató de estrellar el avión. ¡Quédate cuadrado!'
def squeak(src, dst, pitch, formant):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-af', f'rubberband=pitch={pitch}:formant={formant}:pitchq=quality', dst], check=True)
for voice, text, lang in [('af_heart', EN, 'en'), ('af_bella', EN, 'en'), ('am_puck', EN, 'en'), ('ef_dora', ES, 'es'), ('em_santa', ES, 'es')]:
    x, sr = k.create(text, voice=voice, speed=1.12, lang='en-us' if lang == 'en' else 'es'); sf.write('sq_in.wav', tidy(np.asarray(x, float), sr), sr)
    for pitch in (1.35, 1.6):
        for fm in ('shifted', 'preserved'):
            squeak('sq_in.wav', 'sq_out.wav', pitch, fm); y, s2 = sf.read('sq_out.wav')
            segs, _ = m.transcribe('sq_out.wav', language=lang, beam_size=1)
            print(voice, pitch, fm, 'f0', round(f0(y, s2)), '|', ' '.join(s.text.strip() for s in segs)[:110])
