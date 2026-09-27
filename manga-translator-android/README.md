# Manga Traductor para Android

App de Android que traduce páginas de manga del japonés: **lee el texto de los
globos**, lo **traduce** y lo **escribe encima de la imagen**, borrando antes el
japonés. Es la versión para móvil de [`../manga-translator`](../manga-translator).

## Instalar el APK

1. Descarga el APK en el móvil:
   - `MangaTraductor-arm64-v8a.apk` → **casi todos los móviles** (Android de 64 bits).
   - `MangaTraductor-armeabi-v7a.apk` → sólo móviles antiguos o muy básicos de 32 bits.
2. Ábrelo. Android pedirá permiso para "instalar apps desconocidas" desde el
   navegador o el gestor de archivos: acéptalo para esa app.
3. Necesita **Android 8.0 o superior** y **Google Play Services** (lo tienen casi
   todos los móviles; no funciona en móviles Huawei sin servicios de Google).

> Si más adelante instalas un APK compilado en otro ordenador (por ejemplo desde
> GitHub Actions), puede que Android pida desinstalar primero la versión anterior,
> porque cada ordenador firma con su propia clave de pruebas.

## Cómo se usa

1. Pulsa **Elegir páginas** y selecciona una o varias imágenes (o, desde la
   galería o el navegador, usa **Compartir → Manga Traductor**).
2. Las páginas se traducen de una en una y se muestran en una lista vertical.
3. **Toca** una página para alternar entre la traducción y el original.
4. **Mantén pulsada** una página para: ver los textos (japonés → traducción),
   compartirla, guardarla en la galería, volver a traducirla o quitarla.
5. En el menú **⋮** están los **Ajustes** y **Guardar todas en la galería**
   (se guardan en *Imágenes/MangaTraductor*).

### La primera vez

- **OCR de Google (unos MB):** la primera página pide a Google Play Services el
  modelo de japonés de ML Kit y espera a que se descargue. Es automático.
- **manga-ocr (recomendado, 117 MB):** la app muestra un aviso para descargarlo.
  Es el mismo modelo que usa el programa de Python y lee muchísimo mejor el japonés
  vertical de los globos. Sin él se usa sólo el OCR de ML Kit, que falla más.
- **Diccionario de traducción (~30 MB):** con el motor sin conexión, la primera
  página descarga el modelo japonés de Google ML Kit. Después funciona **sin internet**.

### Ajustes

| Opción | Qué hace |
|---|---|
| Traducir a | Inglés o español. |
| Motor de traducción | **Sin conexión (ML Kit)**: gratis y privado, calidad literal. **Claude**: mucho mejor (ve la página entera, entiende quién habla y el tono, corrige errores del OCR); necesita una clave de API de Anthropic y es de pago por uso. |
| Clave de API de Anthropic | Sólo para Claude. Se guarda en el almacenamiento privado de la app. |
| MAYÚSCULAS | Escribe la traducción en mayúsculas, estilo scanlation. |
| Usar manga-ocr | Desactívalo para comparar con el OCR de ML Kit. |

Si Claude falla (sin conexión, clave incorrecta...), esa página se traduce igual
con el motor sin conexión y la app avisa.

**Privacidad:** con el motor sin conexión todo ocurre en el móvil. Con Claude, la
página (reducida) y el texto se envían a la API de Anthropic.

## Cómo funciona

```
imagen ─► ML Kit (detecta dónde hay texto) ─► agrupar en globos ─► manga-ocr (lee el japonés)
       ─► ML Kit Translate o Claude ─► borrar el japonés ─► escribir la traducción en el globo
```

- **`core/`**: Kotlin puro, sin Android, así se puede probar en el ordenador:
  agrupar cajas en globos (`BlockMerger`), máscara del texto, borrado e interior
  del globo (`Cleaner`), manga-ocr con ONNX Runtime (`MangaOcr`) y traducción con
  Claude usando el SDK oficial de Anthropic para Java (`ClaudeTranslator`).
- **`app/`**: la app Android: detección y traducción con ML Kit (`MlKit.kt`),
  rotulado con la fuente Comic Neue (`Typesetter.kt`), descarga verificada del
  modelo (`OcrModel.kt`) y la interfaz (`MainActivity.kt`, `PagesViewModel.kt`).

## Compilar

Necesitas JDK 17+ y el SDK de Android (o Android Studio):

```bash
cd manga-translator-android
./gradlew :app:assembleRelease
# APKs en app/build/outputs/apk/release/
```

Pruebas en el ordenador:

```bash
./gradlew :core:test                 # agrupar, borrar, Claude (con servidor simulado)
MANGA_OCR_DIR=/ruta/al/modelo ./gradlew :core:test   # + OCR real con manga-ocr ONNX
./gradlew :app:testReleaseUnitTest   # Android simulado (Robolectric): pantalla y rotulado
```

El flujo `.github/workflows/android-apk.yml` compila el APK en GitHub cada vez
que cambia esta carpeta (se descarga en *Actions → la ejecución → Artifacts*).

## Limitaciones

- La detección de texto de ML Kit a veces no ve textos muy pequeños o muy estilizados;
  las onomatopeyas dibujadas a mano casi nunca se detectan.
- Si el texto está sobre el dibujo, al borrarlo puede quedar alguna mancha.
- Si dos globos están muy pegados pueden unirse en uno.

## Licencias

- Fuente Comic Neue: SIL Open Font License (incluida en `app/src/main/assets/licenses/`).
- manga-ocr (kha-white/manga-ocr-base) y su vocabulario: Apache 2.0; se usa la
  exportación ONNX de onnx-community/manga-ocr-base-ONNX.
