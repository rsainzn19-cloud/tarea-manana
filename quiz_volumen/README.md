# quiz_volumen — prueba de concepto

Lee la pregunta de opción múltiple que esté en pantalla, la analiza, y traduce
la respuesta al **volumen del sistema**: A = 1, B = 2, C = 3, D = 4…

```
captura de pantalla  →  motor (local o API)  →  letra  →  volumen del sistema
```

El análisis puede correr **entero en tu máquina**, sin mandar nada a internet.

## Los tres motores

| `--motor` | Quién contesta | Sale a internet | Qué necesitas |
|---|---|---|---|
| `local` | Modelo de visión en tu máquina (Ollama). Ve la captura directo. | No | Ollama + un modelo de visión |
| `ocr` | `tesseract` lee el texto y un modelo de texto local lo contesta. | No | Ollama + tesseract |
| `claude` | La API de Anthropic. | Sí | `ANTHROPIC_API_KEY` |
| `auto` *(default)* | `local` si Ollama está corriendo; si no, `claude`. | — | — |

**Cuál usar:** `ocr` suele acertar más que `local` si la pregunta es texto
limpio en pantalla — los modelos de texto razonan mejor que los de visión del
mismo tamaño, y el OCR sobre texto de pantalla es casi perfecto. `local` es
mejor cuando la pregunta trae imágenes, tablas o esquemas.

**Aviso honesto:** un modelo de 7B en tu laptop es bastante peor que Claude en
preguntas de medicina. Espera errores; para eso está `--min-confianza`. Si
tienes RAM de sobra, un modelo más grande (`qwen2.5:14b`, `qwen2.5:32b`) sube
bastante la calidad.

## Instalación

```bash
pip install -r requirements.txt
```

### Para los motores locales (recomendado)

1. Instala [Ollama](https://ollama.com) y déjalo corriendo (`ollama serve`).
2. Baja un modelo:

```bash
ollama pull qwen2.5vl:7b     # visión, para --motor local
ollama pull qwen2.5:7b       # texto,  para --motor ocr
```

3. Sólo para `--motor ocr`, instala tesseract:

```bash
brew install tesseract tesseract-lang          # macOS
sudo apt install tesseract-ocr tesseract-ocr-spa   # Ubuntu
# Windows: https://github.com/UB-Mannheim/tesseract/wiki
```

### Para el motor `claude`

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### Extras según tu sistema

- **Windows:** `pip install pycaw comtypes` (sin eso no puede mover el volumen).
- **macOS:** la primera vez pide permiso de *Grabación de pantalla* para tu
  terminal (Ajustes → Privacidad y seguridad → Grabación de pantalla).

## Uso

```bash
# Esperando en segundo plano: toca Alt dos veces y captura
python3 quiz_volumen.py --motor local --hotkey --step 10

# Todo local, sin internet
python3 quiz_volumen.py --motor local --step 10

# Local por OCR, viendo qué texto leyó
python3 quiz_volumen.py --motor ocr --ver-ocr --step 10

# Vigilando la pantalla cada 5 segundos (Ctrl-C para salir)
python3 quiz_volumen.py --watch 5 --step 10

# Probar el control de volumen sin analizar nada
python3 quiz_volumen.py --fake-answer C --step 10

# Ver qué contestaría sin tocar el volumen
python3 quiz_volumen.py --dry-run
```

### Opciones

| Opción | Para qué |
|---|---|
| `--motor {auto,local,ocr,claude}` | Quién contesta. Ver la tabla de arriba. |
| `--modelo NOMBRE` | Modelo concreto (`qwen2.5:14b`, `llava:13b`, `claude-opus-5`…). |
| `--num-ctx N` | Tokens de contexto del modelo local (default 8192). Súbelo si la captura no cabe. |
| `--ollama-host URL` | Si Ollama no está en `localhost:11434`. |
| `--ver-ocr` | Imprimir el texto que leyó tesseract, para depurar. |
| `--hotkey` | Quedarse esperando en segundo plano y capturar cuando toques la tecla. Sólo Windows. |
| `--tecla NOMBRE` | Qué tecla dispara (`alt` default, `ctrl`, `shift`, `f8`–`f12`). |
| `--taps N` | Cuántos toques seguidos hacen falta (default 2, dentro de 0.6 s). |
| `--watch SEG` | Repetir cada SEG segundos. Si la pantalla no cambió, no vuelve a analizar. |
| `--step N` | Volumen por letra. Default `1` (A=1%, B=2%…). Con `10`: A=10%, B=20%… |
| `--hold SEG` | Después de SEG segundos regresa el volumen a como estaba. |
| `--min-confianza 0-1` | No mueve el volumen si el modelo no está lo bastante seguro. |
| `--max-ancho PX` | Encoger la captura antes de analizarla (default 1280; `0` = no encoger). |
| `--delay SEG` | Esperar antes de capturar, para darte tiempo de cambiar de ventana. |
| `--region X,Y,W,H` | Capturar sólo un rectángulo en vez de toda la pantalla. |
| `--monitor N` | Qué pantalla (1 = principal, 0 = todas juntas). |
| `--image RUTA` | Usar un PNG en vez de capturar (útil para probar). |
| `--save-shot RUTA` | Guardar la captura para revisar qué se vio. |
| `--dry-run` | Hace todo menos cambiar el volumen. |
| `--fake-answer LETRA` | Se salta el análisis y finge esa respuesta. |

## Cómo funciona

- **Captura** (`quiz_volumen.py`) — `mss` toma el PNG. Si no está instalado, cae
  a `screencapture` (macOS) o `gnome-screenshot`/`scrot`/`grim` (Linux). Luego
  la encoge a 1280 px de ancho: una captura de pantalla completa ahoga al
  codificador de visión de los modelos locales y el proceso de Ollama se cae
  con un 500. `--save-shot` guarda la original, no la encogida.
- **Análisis** (`motores.py`) — los tres motores devuelven el mismo dict
  `{hay_pregunta, pregunta, respuesta, confianza, razon}`. Los tres piden
  **salida estructurada** con el mismo JSON Schema: `output_config.format` en la
  API de Anthropic, `format` en Ollama. La letra viene restringida por un enum,
  así que no hay que adivinar parseando texto libre.
- **Red de seguridad** — `_extraer_letra()` rescata respuestas mal formadas
  (`"B)"`, `"la opción D"`, `"2"`) pero es estricta a propósito: si de la
  respuesta no sale una letra clara, devuelve `NINGUNA` con confianza 0 y el
  volumen no se mueve. Preferimos no contestar a inventar.
- **Volumen** — `osascript` (macOS), `wpctl`/`pactl`/`amixer` (Linux),
  `pycaw` → `nircmd` → teclas multimedia (Windows). Esa última no necesita
  instalar nada: manda las teclas de subir/bajar volumen con `ctypes`, pero se
  mueve en pasos de 2%, así que el volumen se redondea al par más cercano
  (con `--step 10` cae exacto). El programa dice por cuál de las tres vías lo
  logró, y si `pycaw` falló, imprime el motivo real.

## Límites conocidos

- Un solo dígito por ronda: el volumen sólo codifica una respuesta a la vez.
- Con `--step 1` la diferencia entre 1% y 2% no se oye; se ve en el indicador.
  Para distinguir de oído usa `--step 10` o más.
- En `--watch`, "la pantalla no cambió" es un hash exacto del PNG: un cursor
  parpadeando o un reloj cuentan como cambio.
- El indicador de volumen aparece en pantalla al cambiarlo — el canal no es
  discreto.
- `--hotkey` lee el teclado global con `GetAsyncKeyState`, así que sólo
  funciona en Windows. Sostener la tecla (como en Alt-Tab) no dispara: cuenta
  toques sueltos, no que esté presionada.
- Ojo con `--tecla alt`: en muchos programas de Windows tocar Alt solo abre la
  barra de menú, y eso sale en la captura. Si te estorba, usa `--tecla f9`.
- Los motores locales tardan varios segundos por pregunta en CPU.
- Si el modelo local se cae con `error 500 ... connection forcibly closed`, es
  el proceso de Ollama muriéndose por memoria: baja más el `--max-ancho` (1024,
  800) o pásate a `--motor ocr`.
- Una captura de pantalla ocupa miles de tokens. Ollama da 4096 por defecto,
  que no alcanza, por eso el programa pide 8192. En una pantalla 4K puede que
  ni eso baste: sube `--num-ctx`, acota con `--region`, o usa `--motor ocr`,
  que manda texto en vez de imagen y ocupa muchísimo menos.
- En Windows sin `pycaw`, el volumen se mueve por teclas multimedia: no se
  puede leer el nivel actual, así que `--hold` no lo restaura.

Es un prototipo para probar la idea; no lo uses en exámenes vigilados.
