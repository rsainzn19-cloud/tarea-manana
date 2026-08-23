# quiz_volumen — prueba de concepto

Lee la pregunta de opción múltiple que esté en pantalla, la analiza, y traduce
la respuesta al **volumen del sistema**: A = 1, B = 2, C = 3, D = 4…

```
captura de pantalla  →  motor (local o API)  →  letra  →  volumen del sistema
```

El análisis puede correr **entero en tu máquina**, sin mandar nada a internet.

## Los tres motores

| `--motor` | Quién contesta | Qué sale de tu máquina | Qué necesitas |
|---|---|---|---|
| `local` | Modelo de visión en tu máquina (Ollama). Ve la captura directo. | Nada | Ollama + un modelo de visión |
| `ocr` | `tesseract` lee el texto y un modelo de texto local lo contesta. | Nada | Ollama + tesseract |
| `ocr-claude` | `tesseract` lee aquí; sólo el **texto** de la pregunta va a la API. | El texto de la pregunta | tesseract + `ANTHROPIC_API_KEY` |
| `claude` | La captura completa va a la API. | Una imagen de tu pantalla | `ANTHROPIC_API_KEY` |
| `auto` *(default)* | `local` si Ollama está corriendo; si no, `claude`. | — | — |

`ocr-claude` es el punto medio: acierta como `claude` porque es el mismo modelo
contestando, tu captura de pantalla nunca sale de la máquina, y cuesta del
orden de diez veces menos porque el texto ocupa muchísimos menos tokens que
una imagen.

**Cuál usar:** `ocr` suele acertar más que `local` si la pregunta es texto
limpio en pantalla — los modelos de texto razonan mejor que los de visión del
mismo tamaño, y el OCR sobre texto de pantalla es casi perfecto. `local` es
mejor cuando la pregunta trae imágenes, tablas o esquemas.

**Aviso honesto:** un modelo de 7B en tu laptop es bastante peor que Claude en
preguntas de medicina. Espera errores; para eso está `--min-confianza`. Si
tienes RAM de sobra, un modelo más grande (`qwen2.5:14b`, `qwen2.5:32b`) sube
bastante la calidad.

### Cuánto cuesta y cómo bajarlo

Los motores de API imprimen los tokens y un estimado en centavos después de
cada pregunta, así que no tienes que adivinar.

Lo caro **no es la pregunta que entra, es lo que el modelo escribe**: los
tokens de salida cuestan 5x los de entrada, y el esquema completo le pide
transcribir el enunciado, transcribir cada opción, razonar y contestar.

Tres palancas, de mayor a menor efecto:

| Cambio | Costo aproximado por pregunta |
|---|---|
| `--motor ocr-claude` (default: Opus 5, esquema completo) | ~2.8 centavos |
| `+ --barato` | ~1.6 centavos |
| `+ --modelo claude-sonnet-5 --barato` | ~1 centavo |
| `+ --modelo claude-haiku-4-5 --barato` | ~0.3 centavos |

`--barato` quita la transcripción de las opciones y pide una razón de una sola
línea. **Conserva lo importante**: el modelo sigue razonando *antes* de dar la
letra, que es lo que sostiene el acierto. Lo que pierdes es el diagnóstico de
"qué opciones leyó", útil cuando algo sale raro.

Para bajar los tokens de *entrada*, acota la captura con `--region X,Y,W,H` en
vez de mandar el texto de toda la pantalla.

### Qué modelo local elegir

Tamaños reales de Ollama, para una máquina con ~15 GB de RAM:

| Modelo | Tamaño | Comentario |
|---|---|---|
| `qwen3:14b` | 9.3 GB | Generación más nueva que `qwen2.5:14b`. Razona: úsalo con `--pensar`. |
| `medgemma:4b` | 3.3 GB | De Google, afinado en medicina. Chico, pero especializado; también lee imágenes. |
| `qwen2.5:14b` | 9 GB | Sólido y general, pero ya superado por qwen3. |
| `medgemma:27b` | 17 GB | El bueno de los médicos — **no cabe** en 15 GB de RAM. |
| `qwen3:30b-a3b` | 19 GB | MoE, sería rápido en CPU — **no cabe** tampoco. |

`meditron` y `medllama2` están basados en Llama 2 (2023): no los uses, razonan
peor que cualquier modelo general reciente.

**Resultado real de las pruebas** (preguntas de cirugía y oncología de nivel
posgrado, en una máquina con 15 GB de RAM):

| Modelo | Resultado |
|---|---|
| `qwen2.5vl:7b` | Falla la mayoría. Además tumbaba a Ollama con capturas grandes. |
| `qwen2.5:14b` | Falla la mayoría. |
| `medgemma:4b` | Falla la mayoría, pese a estar afinado en medicina. |

La conclusión, sin adornos: **con 15 GB de RAM no hay modelo local que conteste
bien preguntas de medicina de posgrado.** El techo son ~14B, y a ese tamaño no
alcanza el conocimiento clínico. El único candidato serio, `medgemma:27b`, pide
17 GB y no cabe. Si quieres respuestas confiables, el camino es `ocr-claude`.

**`--pensar` sólo sirve en modelos de razonamiento** (qwen3 y similares). Les
deja razonar en un canal aparte y *después* rellenar el esquema, en vez de
apretujar el razonamiento dentro de un campo. En los demás modelos da error.

**Cuando falle, mira qué transcribió.** El programa imprime la pregunta y las
opciones tal como las leyó el modelo. Si la transcripción está mal, el problema
es de lectura: baja menos la imagen (`--max-ancho 1600`), acota con `--region`,
o pásate a `--motor ocr`. Si la transcripción está bien pero la letra está mal,
el problema es de conocimiento: necesitas un modelo más grande o `--motor claude`.

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
winget install UB-Mannheim.TesseractOCR            # Windows
brew install tesseract tesseract-lang              # macOS
sudo apt install tesseract-ocr tesseract-ocr-spa   # Ubuntu
```

En Windows el instalador no lo agrega al PATH; el programa lo busca solo en
`C:\Program Files\Tesseract-OCR`. Si lo pusiste en otro lado, pásalo con
`--tesseract`. Marca **Spanish** en los idiomas al instalar, o los acentos
salen mal.

### Para el motor `claude`

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### Extras según tu sistema

- **Windows:** `pip install pycaw comtypes` (sin eso no puede mover el volumen).
- **macOS:** la primera vez pide permiso de *Grabación de pantalla* para tu
  terminal (Ajustes → Privacidad y seguridad → Grabación de pantalla).

## Uso diario (ya instalado)

Abre PowerShell y:

```powershell
cd $HOME\Desktop\tarea-manana-claude-questions-volume-control-a9agep\quiz_volumen
python quiz_volumen.py --motor local --hotkey --step 10
```

O haz doble clic en `abrir.bat`, que lanza el motor `ocr-claude` en modo espera
con Opus 5 y `--barato` (~1.6 centavos por pregunta).
Para cambiar de modelo, edítalo con el Bloc de notas: trae los comentarios
arriba de la línea que hay que tocar.

**Acceso directo en el Escritorio** (Windows), corriendo esto *dentro* de la
carpeta `quiz_volumen`:

```powershell
$a=(New-Object -ComObject WScript.Shell).CreateShortcut("$HOME\Desktop\Quiz Volumen.lnk"); $a.TargetPath="$PWD\abrir.bat"; $a.WorkingDirectory="$PWD"; $a.IconLocation="$env:SystemRoot\System32\SndVol.exe,0"; $a.Save()
```
Ese motor sólo necesita tesseract y la `ANTHROPIC_API_KEY`: **no** requiere
Ollama. Si vas a usar un motor local (`local` u `ocr`), Ollama tiene que estar
corriendo — ícono en la bandeja del sistema.

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
| `--barato` | Recorta lo que el modelo escribe. Baja bastante el costo en los motores de API. |
| `--pensar [NIVEL]` | Dejar razonar al modelo antes de contestar (`low`/`medium`/`high`/`max`). Sólo modelos de razonamiento. |
| `--num-ctx N` | Tokens de contexto del modelo local (default 8192). Súbelo si la captura no cabe. |
| `--ollama-host URL` | Si Ollama no está en `localhost:11434`. |
| `--ver-ocr` | Imprimir el texto que leyó tesseract, para depurar. |
| `--hotkey` | Quedarse esperando en segundo plano y capturar cuando toques la tecla. Sólo Windows. |
| `--tecla TECLA` | Qué tecla dispara: una letra (`a`–`z`), `alt`, `ctrl`, `shift` o `f8`–`f12`. |
| `--popup [completo\|mini]` | Recuadro en pantalla. `completo` trae pregunta y razonamiento; `mini` sólo la letra. |
| `--popup-seg SEG` | Cuánto dura el recuadro (default 8; `0` = hasta que le des clic). |
| `--taps N` | Cuántos toques seguidos hacen falta (default 2, dentro de 0.6 s). |
| `--watch SEG` | Repetir cada SEG segundos. Si la pantalla no cambió, no vuelve a analizar. |
| `--step N` | Volumen por letra. Default `1` (A=1%, B=2%…). Con `10`: A=10%, B=20%… |
| `--hold SEG` | Después de SEG segundos regresa el volumen a como estaba. |
| `--min-confianza 0-1` | No mueve el volumen si el modelo no está lo bastante seguro. |
| `--max-ancho PX` | Encoger la captura antes de analizarla (default 1280; `0` = no encoger). No aplica a `--motor ocr`. |
| `--tesseract RUTA` | Ruta al `tesseract.exe` si no está en el PATH. |
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
  con un 500. `--save-shot` guarda la original, no la encogida. En `--motor ocr`
  no la encoge: ahí la imagen nunca llega al modelo, así que reducirla sólo le
  quitaría resolución a tesseract.
- **Análisis** (`motores.py`) — los tres motores devuelven el mismo dict
  `{hay_pregunta, pregunta, opciones, razon, respuesta, confianza}`. **El orden
  de los campos del esquema importa:** la salida estructurada se genera campo
  por campo en ese orden, así que el modelo primero transcribe lo que ve, luego
  razona en `razon`, y sólo al final se compromete con `respuesta`. Con la letra
  arriba, el modelo adivinaba y después se justificaba. Los tres piden
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
- El recuadro de `--popup` necesita tkinter, que viene con el instalador de
  Python de python.org. Se identifica como lo que es: si buscas algo que
  aparente ser otra cosa, este no es el proyecto.
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
