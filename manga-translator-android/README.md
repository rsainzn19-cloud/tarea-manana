# Manga Traductor para Android

App de Android que traduce cómics asiáticos — **manga (japonés), manhua (chino) y
manhwa (coreano)**: **lee el texto de los globos**, lo **traduce** y lo **escribe
encima**, borrando antes el original. Funciona de dos formas:

- **Botón flotante (en segundo plano):** mientras lees en el navegador (o en
  cualquier app), tocas el botón flotante, la app **captura la pantalla** y pone
  la traducción **encima de los globos, en su sitio**.
- **Páginas sueltas:** eliges imágenes de la galería (o las compartes con la app)
  y se traducen en una lista tipo lector.

Es la versión para móvil de [`../manga-translator`](../manga-translator).

## Instalar el APK

Hay dos versiones de la misma app (se pueden instalar una encima de la otra):

| Versión | Tamaño | OCR |
|---|---|---|
| **Completa** — [MangaTraductor-arm64-v8a.apk](https://github.com/rsainzn19-cloud/tarea-manana/releases/download/manga-traductor/MangaTraductor-arm64-v8a.apk) | ~150 MB | **Ya incluido**: no descarga nada del OCR. |
| **Ligera** — [MangaTraductor-ligera-arm64-v8a.apk](https://github.com/rsainzn19-cloud/tarea-manana/releases/download/manga-traductor/MangaTraductor-ligera-arm64-v8a.apk) | <30 MB | Descarga manga-ocr (117 MB) sola la primera vez que se abre con Wi-Fi (con datos móviles pregunta), y el OCR de Google lo da Google Play Services. |

Los enlaces son para casi todos los móviles (Android de 64 bits). Para móviles
antiguos de 32 bits están las variantes `armeabi-v7a` en la pestaña *Releases*.

1. Descarga el APK en el móvil y ábrelo.
2. Android pedirá permiso para "instalar apps desconocidas" desde el navegador o
   el gestor de archivos: acéptalo para esa app.
3. Necesita **Android 8.0 o superior** (la ligera, además, Google Play Services).

Las versiones nuevas se instalan encima de la anterior: todas se firman con la
misma clave (`app/signing/`).

## Botón flotante: traducir la pantalla

1. En la app pulsa **Activar botón flotante** y acepta, la primera vez:
   - **Notificaciones** (para la notificación fija con el botón «Detener»).
   - **Mostrar sobre otras apps** (para que el botón salga encima del navegador).
   - **Capturar la pantalla** (Android lo pregunta cada vez que se activa; elige
     pantalla completa).
2. Abre el navegador con el manga y **toca el botón flotante**. En unos segundos
   aparece la traducción encima de cada globo.
3. **Toca la traducción** para cerrarla y seguir leyendo. **Mantén pulsada** la
   traducción para guardar la captura traducida en la galería.
4. El botón se puede **arrastrar**; al soltarlo se pega al borde. **Mantenlo
   pulsado** (o usa «Detener» en la notificación) para desactivarlo.

Mientras está activo, Android muestra un aviso de que la app está capturando la
pantalla: es normal. La captura sólo se usa cuando tocas el botón.

**Si el interruptor «Mostrar sobre otras apps» sale bloqueado** (Android 13+ con
apps instaladas fuera de la tienda): Ajustes → Apps → Manga Traductor → menú ⋮ →
**Permitir ajustes restringidos**, y vuelve a intentarlo.

## Páginas sueltas

1. Pulsa **Elegir páginas** y selecciona una o varias imágenes (o, desde la
   galería o el navegador, usa **Compartir → Manga Traductor**).
2. Las páginas se traducen de una en una y se muestran en una lista vertical.
3. **Toca** una página para alternar entre la traducción y el original.
4. **Mantén pulsada** una página para: ver los textos (original → traducción),
   compartirla, guardarla en la galería, volver a traducirla o quitarla.
5. En el menú **⋮** están los **Ajustes** y **Guardar todas en la galería**
   (se guardan en *Imágenes/MangaTraductor*).

### Lo que se descarga

- **Versión completa:** nada del OCR (manga-ocr y el OCR de ML Kit en japonés,
  chino y coreano van dentro del APK).
- **Versión ligera:** manga-ocr (117 MB) lo descarga el gestor de descargas de
  Android en cuanto hay Wi-Fi (con datos móviles, el botón «Descargar ya con datos»):
  sigue aunque cierres la app, se reanuda si se corta y se ve en las notificaciones.
  Se baja de la copia publicada en este repositorio (versión *manga-ocr-modelo*) y,
  si falla, de Hugging Face; cada archivo se comprueba con su SHA-256. El OCR
  de ML Kit del idioma elegido lo descarga Google Play Services. Mientras tanto se
  usa un OCR más básico.
- **Las dos:** el diccionario de traducción sin conexión (~30 MB) se descarga solo,
  en segundo plano, la primera vez que abres la app con internet. Después la
  traducción funciona **sin internet**.

### Ajustes

| Opción | Qué hace |
|---|---|
| Idioma del cómic | Japonés (manga), chino (manhua) o coreano (manhwa). manga-ocr sólo lee japonés; en chino y coreano cada línea la lee PaddleOCR (con los modelos de calidad) o, si no, el OCR de ML Kit. |
| Traducir a | Inglés o español. |
| Motor de traducción | Ver la tabla de abajo. |
| Clave de Gemini | Sólo para Gemini 3.8 Flash. Es gratis: botón «Conseguir clave gratis» (Google AI Studio). |
| Clave de API de Anthropic | Sólo para Claude. Las claves se guardan en el almacenamiento privado de la app. |
| Tamaño de Qwen | 4B (traduce mejor, 3,1 GB) o 2B (el doble de rápido, 1,6 GB). |
| Qwen ve la página | Qwen también mira la página (quién habla, qué pasa). Unos segundos más por página y 220 MB más de descarga; desactivado de fábrica. |
| Letra | «A mano» (Patrick Hand, la de fábrica, como el rotulado de un manga publicado) o «Cómic» (Comic Neue). Las onomatopeyas van siempre con Bangers. |
| MAYÚSCULAS | Escribe la traducción en mayúsculas, como un manga publicado (activado de fábrica). |
| Modelos de calidad | Usa comic-text-detector, LaMa y PaddleOCR (ver más abajo). |
| Usar manga-ocr | Desactívalo para comparar con el OCR de ML Kit. |
| Recordar la historia | Pasa a la IA un resumen, los nombres y las últimas frases de las páginas anteriores. |

| Motor | Internet | Coste | Calidad |
|---|---|---|---|
| **Sin conexión (ML Kit)** | No | Gratis | Literal. |
| **Gemini Nano** | No | Gratis | Más natural. Es la IA del propio móvil (Pixel 10 y otros compatibles); como Android sólo deja usarla a la app que está delante, con el botón flotante la app se abre un instante de forma invisible. |
| **Qwen 3.5** | Sólo para descargarlo una vez | Gratis | Buena (sobre todo el 4B). Funciona dentro del móvil con ONNX Runtime, también desde el botón flotante; tarda de 20 s a 1 min por página. Opcionalmente ve la página. Necesita un móvil de 64 bits y 6–8 GB de RAM. |
| **Gemini 3.8 Flash** (recomendado) | Sí | Gratis con clave de Google (con límite diario) | Muy buena: ve la página entera y traduce hasta 3 páginas juntas. |
| **Claude** | Sí | De pago por uso | La mejor: ve la página, entiende quién habla y el tono, corrige errores del OCR; también hasta 3 páginas juntas. |

**Memoria de la historia:** con Gemini, Claude, Gemini Nano y Qwen, cada página
traducida se añade a la historia (resumen, nombres de los personajes y últimas
frases) y se pasa a la IA en la siguiente, para que los nombres y el tono se
mantengan. En el menú **⋮ → Historia** se ve lo que recuerda, se pueden
**Editar nombres** a mano (una línea por nombre: `ハル = Haru`) y se empieza una
**Nueva historia** al cambiar de manga.

Si un motor de IA falla (sin conexión, clave incorrecta, límite gratuito, Qwen
todavía descargándose...), la imagen se traduce igual con el motor sin conexión
y la app avisa.

**Privacidad:** con el motor sin conexión, Gemini Nano y Qwen todo ocurre en el
móvil. Con Gemini o Claude, la imagen (reducida) y el texto se envían a la API de
Google o de Anthropic (en el plan gratis de Gemini, Google puede usarlos para
mejorar sus productos).

### Modelos de calidad (de manga-image-translator, BallonsTranslator y Koharu)

Los mejores traductores de manga de código abierto usan estos modelos, que
también usa la app (se descargan una vez, ~300 MB, con Wi-Fi; interruptor en
Ajustes):

- **comic-text-detector** (dmMaze, entrenado con Manga109 y cómics): encuentra
  cada bloque de texto **entero** (un globo = un bloque, aunque el OCR lo parta en
  líneas) y marca los **píxeles exactos de las letras**, también en texto
  estilizado. La app agrupa las líneas de ML Kit por bloque, añade los bloques que
  ML Kit no vio (manga-ocr los lee) y borra con su máscara.
- **LaMa para manga** (AnimeMangaInpainting): el texto que no está en un globo
  liso (sobre tramas o dibujo) se reconstruye en recortes de 512×512 en vez de
  emborronarse. En la prueba con trama, el error baja de 80 a 36 (de 255).
- **PaddleOCR** (PP-OCRv5 de Baidu, sólo en chino y coreano, ~15 MB, sólo se baja
  el del idioma elegido): vuelve a leer cada línea que encuentra ML Kit (también
  las columnas verticales, girándolas). Lee mejor el chino y el coreano,
  también con letra estilizada; si no está seguro se queda el texto de ML Kit.

Todos corren con el mismo ONNX Runtime que manga-ocr. comic-text-detector es
~20 veces más lento si la CPU procesa números «desnormales»: la sesión los pone a
cero (`session.set_denormal_as_zero`), de 38 s a ~2 s por página en el PC.

**Velocidad:** la primera vez, la app mide en el propio móvil si el detector y
LaMa van más rápido con la CPU o con XNNPACK (el acelerador de ONNX Runtime para
móviles) y se queda con el más rápido. El detector trabaja a la vez que ML Kit,
y el borrado con LaMa se hace mientras la IA traduce.

### Calidad de la traducción y del rotulado

- **Globos partidos:** si el detector parte el texto de un globo en dos (columnas
  separadas, un «…» aparte), la app lo detecta buscando el globo que rodea a cada
  trozo y los une antes de leer y traducir: una sola frase por globo, en su orden.
- **Nada se pisa:** si dos zonas de escritura se cruzan, se reparten por el hueco
  que hay entre sus textos.
- **Dónde escribir:** las líneas siguen la forma del globo (más anchas en el centro
  de un globo ovalado, como rotula un letrista); también se prueban rectángulos
  dentro del globo real y se usa lo que permite la letra más grande. Nunca se
  parte una palabra (ni «SIGH» de su «...»). La letra no crece más de 1,5 veces la
  de la mitad de los globos de la página, para que se vea uniforme.
- **Onomatopeyas:** las que la IA marca como efecto de sonido (o, sin IA, un texto
  corto en kana suelto sobre el dibujo) se rotulan con letra de efecto (Bangers),
  más grandes, con borde, y apiladas letra a letra si el hueco es alto y estrecho.
- **La IA sabe qué globo es cada frase:** Gemini y Claude reciben la página con una
  etiqueta roja numerada junto a cada texto, leen la conversación entera, corrigen
  el OCR mirando el globo (el original corregido es el que se guarda en la memoria
  y en «Ver textos»), dicen si cada texto es diálogo, narración u onomatopeya y
  descartan lo que no es texto (marcas de agua, trazos).
- **Varias páginas juntas:** al elegir varias páginas, Gemini y Claude traducen
  hasta 3 en la misma petición (con las imágenes de las 3): la conversación sigue
  de una página a otra.
- **Webtoons:** las tiras muy largas se analizan por trozos solapados y con más
  resolución, para no perder la letra pequeña.

### Qwen 3.5 en el móvil

Al elegir Qwen en Ajustes, la app descarga el modelo con el gestor de descargas
de Android (espera al Wi-Fi; en la tarjeta de la pantalla principal está
«Descargar ya con datos»). Se baja de Hugging Face, revisión fija
(`onnx-community/Qwen3.5-4B-ONNX` o `Qwen3.5-2B-ONNX`, pesos de 4 bits), y cada
archivo se comprueba con su SHA-256. Al cambiar de tamaño se borra el otro.

- El tokenizador de Qwen (`BpeTokenizer`) y la generación (`LocalLlm`, con el
  estado de atención y de las capas lineales de Qwen 3.5) están escritos en
  Kotlin sobre el mismo ONNX Runtime que manga-ocr: el APK no crece.
- Tras la descarga, `OnnxPatcher` marca las multiplicaciones de 4 bits para que
  se calculen en int8 (`accuracy_level = 4`): ~45 % más rápido con la misma
  traducción.
- Responde sin «pensar» (plantilla de Qwen con el bloque `<think>` vacío) y con
  búsqueda voraz: una línea «número: traducción» por globo.
- **Qwen ve la página** (opcional): Qwen 3.5 es multimodal. La app descarga
  también su codificador de imagen (`vision_encoder_q4.onnx`, 218 MB), reduce la
  página a ~250 trozos de 32×32 píxeles (unos 416×608) con el número de cada globo
  marcado, y mete sus vectores en la conversación con las posiciones en 3 ejes
  (orden, fila, columna) de Qwen (`QwenVision`, `LocalLlm`). En el PC la imagen
  tarda ~1–2 s más la lectura de ~250 piezas más.

## Cómo funciona

```
imagen o captura ─► ML Kit (detecta el texto; las tiras largas por trozos) ─► agrupar en globos
                 └► comic-text-detector (a la vez: bloques y máscara de las letras)
                 ─► unir los trozos de un mismo globo ─► manga-ocr (japonés) o PaddleOCR / ML Kit (chino, coreano)
                 ─► ML Kit Translate / Gemini Nano / Qwen / Gemini / Claude (+ memoria de la historia)
                    └► a la vez, borrar el original (LaMa si no es un globo liso)
                 ─► escribir la traducción siguiendo la forma del globo
```

- **`core/`**: Kotlin puro, sin Android, así se puede probar en el ordenador:
  agrupar cajas en globos (`BlockMerger`), máscara del texto, borrado e interior
  del globo (`Cleaner`, `Bubbles`), manga-ocr con ONNX Runtime (`MangaOcr`),
  comic-text-detector, LaMa y PaddleOCR (`ComicTextDetector`, `LamaInpainter`,
  `PaddleRecognizer`), traducción con
  Claude usando el SDK oficial de Anthropic para Java (`ClaudeTranslator`), con
  Gemini (`GeminiApiTranslator`) y con Qwen (`QwenTranslator`, `LocalLlm`,
  `BpeTokenizer`, `OnnxPatcher`, `QwenVision`), la memoria de la historia (`StoryContext`) y
  la conversión de las capturas de pantalla (`ScreenPixels`).
- **`app/`**: la app Android:
  - `ScreenTranslateService.kt`: servicio en segundo plano con la captura de
    pantalla (MediaProjection), el botón flotante (`FloatingBubble.kt`) y la
    traducción superpuesta (`TranslationOverlay.kt`).
  - `OcrModel.kt`: carga manga-ocr **directamente desde el APK** (mapeado en memoria)
    o, en la versión ligera, lo descarga una vez.
  - `QwenModel.kt`, `ModelDownloader.kt`, `DownloadableModel.kt`: Qwen y la
    descarga de modelos con el gestor de descargas de Android.
  - `src/completa/` y `src/ligera/`: lo que cambia entre las dos versiones.
  - `MlKit.kt`, `Typesetter.kt`, `PageTranslator.kt`: detección, traducción y rotulado.
  - `MainActivity.kt`, `PagesViewModel.kt`: la interfaz.

## Compilar

Necesitas JDK 17+ y el SDK de Android (o Android Studio). La primera compilación
de la versión completa descarga manga-ocr (117 MB, revisión fija y SHA-256
comprobado) y lo mete en el APK:

```bash
cd manga-translator-android
./gradlew :app:assembleRelease          # las dos versiones
./gradlew :app:assembleLigeraRelease    # sólo la ligera
# APKs en app/build/outputs/apk/{completa,ligera}/release/
```

Pruebas en el ordenador:

```bash
./gradlew :core:test                 # agrupar, borrar, capturas, Claude y Gemini (servidores simulados)
MANGA_OCR_DIR=~/.gradle/caches/manga-ocr/f9023406bb2f6b17df67bc4a327c56ecd20611f0 \
  ./gradlew :core:test               # + OCR real con manga-ocr
QWEN_DIR=/carpeta/con/qwen3.5-2b ./gradlew :core:test   # + Qwen de verdad (tokenizador idéntico al
                                     # de Hugging Face y traducción de 5 globos)
QUALITY_DIR=/carpeta/con/los/onnx ./gradlew :core:test  # + comic-text-detector, LaMa y PaddleOCR de verdad
                                     # (paddle-zh.onnx/.yml y paddle-ko.onnx/.yml)
./gradlew :app:testCompletaReleaseUnitTest   # Android simulado (Robolectric): pantalla, botón
                                     # flotante, capa de traducción, rotulado y OCR del APK
```

**Prueba con manga real:** `RealMangaTest` traduce páginas de verdad de punta a
punta (detector, OCR, borrado y rotulado de Android) y guarda el resultado en
`app/build/test-output/real/`. Se probó con *Black Jack ni Yoroshiku* de Shuho
Sato (libre para cualquier uso, en [densho810.com](https://densho810.com/free/)),
comparando con su edición oficial en inglés:

```bash
REAL_MANGA_DIR=/carpeta/con/páginas.png QUALITY_DIR=... MANGA_OCR_DIR=... \
  REAL_MANGA_TRANSLATIONS=traducciones.tsv \
  ./gradlew :app:testCompletaDebugUnitTest --tests '*RealMangaTest*'
# traducciones.tsv: «original<TAB>traducción» (con «SFX:» delante, onomatopeya);
# sin él se usa Qwen (QWEN_DIR) o una traducción de relleno
```

El flujo `.github/workflows/android-apk.yml` compila y prueba la app en GitHub
cada vez que cambia esta carpeta, y publica el APK en *Releases* (el enlace de
arriba siempre apunta a la última compilación).

## Limitaciones

- Sin los modelos de calidad, ML Kit a veces no ve textos muy pequeños o muy
  estilizados; las onomatopeyas muy dibujadas a veces no se detectan ni con ellos.
- Los diálogos se escriben siempre en horizontal (en inglés y español es lo
  normal); sólo las onomatopeyas se apilan en vertical.
- Si el texto está sobre el dibujo, al borrarlo puede quedar alguna mancha.
- Si dos globos están muy pegados pueden unirse en uno.
- Con el botón flotante, si la página se mueve mientras se traduce, la traducción
  puede quedar desplazada: tócala para cerrarla y vuelve a tocar el botón.

## Licencias

- Fuentes Comic Neue, Patrick Hand y Bangers: SIL Open Font License (incluidas en
  `app/src/main/assets/licenses/`).
- manga-ocr (kha-white/manga-ocr-base) y su vocabulario: Apache 2.0; se usa la
  exportación ONNX de onnx-community/manga-ocr-base-ONNX.
- comic-text-detector (dmMaze/comic-text-detector, GPL-3.0) en la exportación ONNX de
  mayocream/comic-text-detector-onnx, y LaMa para manga (dreMaz/AnimeMangaInpainting)
  en la de mayocream/lama-manga-onnx: se descargan aparte (no van dentro del APK).
- PaddleOCR PP-OCRv5 (Baidu, `PaddlePaddle/PP-OCRv5_mobile_rec_onnx` y
  `korean_PP-OCRv5_mobile_rec_onnx`): Apache 2.0; se descarga aparte.
- Qwen 3.5 (Alibaba), también su codificador de imagen: Apache 2.0; se usa la
  exportación ONNX de onnx-community y se descarga aparte (no va dentro del APK).
- Iconos de Material Icons: Apache 2.0.
