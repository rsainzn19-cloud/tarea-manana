# Manga Traductor para Android

App de Android que traduce manga del japonés: **lee el texto de los globos**, lo
**traduce** y lo **escribe encima**, borrando antes el japonés. Funciona de dos formas:

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
4. **Mantén pulsada** una página para: ver los textos (japonés → traducción),
   compartirla, guardarla en la galería, volver a traducirla o quitarla.
5. En el menú **⋮** están los **Ajustes** y **Guardar todas en la galería**
   (se guardan en *Imágenes/MangaTraductor*).

### Lo que se descarga

- **Versión completa:** nada del OCR (manga-ocr y el OCR japonés de ML Kit van
  dentro del APK).
- **Versión ligera:** manga-ocr (117 MB) lo descarga el gestor de descargas de
  Android en cuanto hay Wi-Fi (con datos móviles, el botón «Descargar ya con datos»):
  sigue aunque cierres la app, se reanuda si se corta y se ve en las notificaciones.
  Se baja de la copia publicada en este repositorio (versión *manga-ocr-modelo*) y,
  si falla, de Hugging Face; cada archivo se comprueba con su SHA-256. El OCR
  japonés de ML Kit lo descarga Google Play Services. Mientras tanto se usa un OCR
  más básico.
- **Las dos:** el diccionario de traducción sin conexión (~30 MB) se descarga solo,
  en segundo plano, la primera vez que abres la app con internet. Después la
  traducción funciona **sin internet**.

### Ajustes

| Opción | Qué hace |
|---|---|
| Traducir a | Inglés o español. |
| Motor de traducción | Ver la tabla de abajo. |
| Clave de Gemini | Sólo para Gemini 3.8 Flash. Es gratis: botón «Conseguir clave gratis» (Google AI Studio). |
| Clave de API de Anthropic | Sólo para Claude. Las claves se guardan en el almacenamiento privado de la app. |
| Tamaño de Qwen | 4B (traduce mejor, 3,1 GB) o 2B (el doble de rápido, 1,6 GB). |
| MAYÚSCULAS | Escribe la traducción en mayúsculas, estilo scanlation. |
| Usar manga-ocr | Desactívalo para comparar con el OCR de ML Kit. |
| Recordar la historia | Pasa a la IA un resumen, los nombres y las últimas frases de las páginas anteriores. |

| Motor | Internet | Coste | Calidad |
|---|---|---|---|
| **Sin conexión (ML Kit)** | No | Gratis | Literal. |
| **Gemini Nano** | No | Gratis | Más natural. Es la IA del propio móvil (Pixel 10 y otros compatibles); como Android sólo deja usarla a la app que está delante, con el botón flotante la app se abre un instante de forma invisible. |
| **Qwen 3.5** | Sólo para descargarlo una vez | Gratis | Buena (sobre todo el 4B). Funciona dentro del móvil con ONNX Runtime, también desde el botón flotante; tarda de 20 s a 1 min por página. Necesita un móvil de 64 bits y 6–8 GB de RAM. |
| **Gemini 3.8 Flash** (recomendado) | Sí | Gratis con clave de Google (con límite diario) | Muy buena: ve la página entera. |
| **Claude** | Sí | De pago por uso | La mejor: ve la página, entiende quién habla y el tono, corrige errores del OCR. |

**Memoria de la historia:** con Gemini, Claude, Gemini Nano y Qwen, cada página
traducida se añade a la historia (resumen, nombres de los personajes y últimas
frases) y se pasa a la IA en la siguiente, para que los nombres y el tono se
mantengan. En el menú **⋮ → Historia** se ve lo que recuerda y se empieza una
**Nueva historia** al cambiar de manga.

Si un motor de IA falla (sin conexión, clave incorrecta, límite gratuito, Qwen
todavía descargándose...), la imagen se traduce igual con el motor sin conexión
y la app avisa.

**Privacidad:** con el motor sin conexión, Gemini Nano y Qwen todo ocurre en el
móvil. Con Gemini o Claude, la imagen (reducida) y el texto se envían a la API de
Google o de Anthropic (en el plan gratis de Gemini, Google puede usarlos para
mejorar sus productos).

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

## Cómo funciona

```
imagen o captura ─► ML Kit (detecta dónde hay texto) ─► agrupar en globos ─► manga-ocr (lee el japonés)
                 ─► ML Kit Translate / Gemini Nano / Qwen / Gemini / Claude (+ memoria de la historia)
                 ─► borrar el japonés ─► escribir la traducción en el globo
```

- **`core/`**: Kotlin puro, sin Android, así se puede probar en el ordenador:
  agrupar cajas en globos (`BlockMerger`), máscara del texto, borrado e interior
  del globo (`Cleaner`), manga-ocr con ONNX Runtime (`MangaOcr`), traducción con
  Claude usando el SDK oficial de Anthropic para Java (`ClaudeTranslator`), con
  Gemini (`GeminiApiTranslator`) y con Qwen (`QwenTranslator`, `LocalLlm`,
  `BpeTokenizer`, `OnnxPatcher`), la memoria de la historia (`StoryContext`) y
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
./gradlew :app:testCompletaReleaseUnitTest   # Android simulado (Robolectric): pantalla, botón
                                     # flotante, capa de traducción, rotulado y OCR del APK
```

El flujo `.github/workflows/android-apk.yml` compila y prueba la app en GitHub
cada vez que cambia esta carpeta, y publica el APK en *Releases* (el enlace de
arriba siempre apunta a la última compilación).

## Limitaciones

- La detección de texto de ML Kit a veces no ve textos muy pequeños o muy estilizados;
  las onomatopeyas dibujadas a mano casi nunca se detectan.
- Si el texto está sobre el dibujo, al borrarlo puede quedar alguna mancha.
- Si dos globos están muy pegados pueden unirse en uno.
- Con el botón flotante, si la página se mueve mientras se traduce, la traducción
  puede quedar desplazada: tócala para cerrarla y vuelve a tocar el botón.

## Licencias

- Fuente Comic Neue: SIL Open Font License (incluida en `app/src/main/assets/licenses/`).
- manga-ocr (kha-white/manga-ocr-base) y su vocabulario: Apache 2.0; se usa la
  exportación ONNX de onnx-community/manga-ocr-base-ONNX.
- Qwen 3.5 (Alibaba): Apache 2.0; se usa la exportación ONNX de onnx-community
  y se descarga aparte (no va dentro del APK).
- Iconos de Material Icons: Apache 2.0.
