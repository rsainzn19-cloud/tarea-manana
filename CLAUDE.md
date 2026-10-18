# tarea-manana

Proyectos de animación de bloques hechos con código (Three.js r128, todo en una sola página HTML):

- `meteoro-thunderhead/`: el motor 3D base y la primera animación.
- `estudio-diorama/`, `pelicula-completa/`: proyectos anteriores.
- `cube-news/`: el canal de noticias animadas con Mr. News. Es el proyecto activo.

Al usuario se le responde en español.

## Cube News

- **Plan de producción:** antes de hacer un video nuevo, lee `cube-news/PIPELINE.md`.
- **Textos y arte del canal:** `cube-news/canal/`.
- **Páginas publicadas** (se generan, no se editan a mano): `index.html` (semanal), `shorts.html`, `mr-news.html` (ficha del personaje), `mascotas.html` (casting).
- **Videos:** `cube-news/videos/`, con sus títulos y descripciones en `DESCRIPCIONES.md`.

### Código: `cube-news/src/`

| Archivo | Qué hace |
|---|---|
| `assemble_news.py` | Arma todas las páginas. Toma el motor de `meteoro-thunderhead/index.html`, le aplica parches con `rep()` y le pega `story_core.js`, `news_world.js`, `news_world2.js` y las partes de cada página. |
| `story_core.js` | Utilidades y post-proceso `POST`: bloom, color, impacto y el modo PS1 opcional. |
| `news_world.js` | Personajes: pieles, poses (`ANIMS`) y la fábrica de mascotas `mascotDef`. `PENGUIN` es Mr. News: ojos anime cuadrados en una calcomanía (`chibiEyes`/`eyeShapeSquare`), sombrero y traje. También el estudio y el logo Tierra-cubo. |
| `news_world2.js` | Los escenarios de cada historia (`D[...]`) y la intro del logo (`LOGO`). |
| `shorts_show.js` | Los Shorts: tiempos por palabra (`layout`), cortes del presentador en cada pausa (`segs`), subtítulos, tarjetas y el gancho `window.CUBE_EXPORT` para exportar. |
| `audio_core.js`, `shorts_audio.js` | Música, efectos y mezcla offline con WebAudio. |
| `news_show.js`, `mrnews_show.js`, `casting_show.js` | Noticiero semanal, ficha de Mr. News y casting. |
| `thumb_show.js`, `brand_show.js` | Miniaturas, avatar y banners. |
| `*_head.html` | El HTML y CSS de cada página. |
| `shorts_data.js` | Guiones y voces en base64. Solo para el build local; con él se pueden rehacer las mezclas sin los modelos de voz. |
| `shorts_pub.js` | Lo mismo para la página publicada, con las mezclas ya hechas. |
| `mrnews_data.js`, `mascots_data.js`, `jingle_data.js` | Datos generados: voces de la ficha, casting y jingle. |
| `tts/script.py` | Guiones en inglés y español. |
| `tts/gen.py` | Genera la voz con Kokoro (`am_michael` en inglés, `em_alex` en español) y le aplica el efecto chillón. |
| `tts/asr.py` | Comprueba con Whisper que la voz se entiende. |
| `make_shorts_data.py` | Junta `tts/out` y `vid/mix` en `shorts_data.js` y `shorts_pub.js`. |

### Comandos (desde `cube-news/src`)

- **Armar las páginas:** `python3 assemble_news.py`
- **Buscar errores en una página:** `node errs.js shorts_build.html`
- **Fotogramas de prueba de un Short:** `node export_short.js <k> <en|es> <carpeta> 30 0 "t=1.5,10"`
  - `k`: 0 = piloto, 1 = dólar, 2 = papá.
- **Mezcla de audio de un Short:** `node export_short.js <k> <lang> <carpeta> 30 0 audio`
- **Los 6 MP4:** `bash run_videos.sh`
  - Necesita las mezclas en `vid/mix/*.wav`.
  - Tarda ~35 min por Short en un contenedor sin GPU.
- **Miniaturas:** `node shot_thumbs.js <carpeta>`
- **Avatar y banners:** `node shot_brand.js <carpeta>`

Los scripts de render usan Playwright con el Chromium preinstalado. Además redirigen el CDN a `three.min.js` y Google Fonts a `fonts/`, así funcionan sin red.

### Lo que no está en git

- **Modelos de voz:** `tts/kokoro-v1.0.onnx` y `tts/voices-v1.0.bin`.
  - Se descargan de https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.onnx y `.../voices-v1.0.bin`.
  - Paquetes de Python: `kokoro-onnx`, `soundfile`, `numpy`, `piper-tts` y `faster-whisper`.
  - ffmpeg debe tener el filtro `rubberband`.
- **`tts/out/` y `vid/`:** voces sueltas, fotogramas y mezclas. Se regeneran con los comandos de arriba.

### Cosas que conviene saber

- **Parpadeo en capturas sueltas:** en los fotogramas `t=...`, Mr. News sale a veces parpadeando, porque el salto de tiempo dispara el parpadeo. En video continuo no pasa.
- **Commits de videos:** sube cada MP4 al repo hasta que `run.log` diga `done <nombre>`. Mientras ffmpeg escribe, git ve un archivo a medias.
- **Opciones apagadas:** el modo PS1 (`POST.setPS1`) y las plumas low-poly de Mr. News (`anchor.polyBits`) existen pero están apagados; el usuario prefirió el estilo normal.
- **Reglas de contenido:**
  - Nunca mostrar personas reales.
  - Cada noticia se verifica con al menos 2 fuentes.
  - No se usan música, clips ni diseños de terceros.

## Ahorro de tokens

- **Respuestas cortas:** el resultado y lo que el usuario tiene que decidir; sin repetir lo que ya se dijo.
- **No abrir archivos enormes.** Las páginas publicadas y los `*_data.js`/`shorts_pub.js` pesan megas de base64 (`.claude/settings.json` bloquea leerlos). Para buscar algo ahí, usa `grep -o` con un patrón corto.
- **Leer por partes:** `grep -n` para ubicar y luego leer solo esas líneas, no el archivo entero.
- **Imágenes solo cuando hagan falta:** para revisar, junta varias capturas chicas en una sola hoja (ffmpeg `hstack`) en vez de abrir una por una.
- **Renders largos en segundo plano** y avisos por línea (`run.log`), sin estar consultando el progreso.
- **Una sesión por trabajo:** para producir videos, abre una sesión nueva; este archivo y `cube-news/PIPELINE.md` tienen todo lo necesario.
