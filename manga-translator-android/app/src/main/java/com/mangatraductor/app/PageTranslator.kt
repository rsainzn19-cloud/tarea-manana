package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.mangatraductor.core.Box
import com.mangatraductor.core.ClaudeTranslator
import com.mangatraductor.core.ComicTextDetector
import com.mangatraductor.core.GeminiApiTranslator
import com.mangatraductor.core.Inpainter
import com.mangatraductor.core.LamaInpainter
import com.mangatraductor.core.LocalLlm
import com.mangatraductor.core.MangaOcr
import com.mangatraductor.core.PaddleRecognizer
import com.mangatraductor.core.PageProcessor
import com.mangatraductor.core.PageTexts
import com.mangatraductor.core.PreparedPage
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.QwenTranslator
import com.mangatraductor.core.QwenVision
import com.mangatraductor.core.SimpleInpainter
import com.mangatraductor.core.SourceLanguage
import com.mangatraductor.core.StoryContext
import com.mangatraductor.core.TextBlock
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translation
import com.mangatraductor.core.Translator
import com.mangatraductor.core.asLineReader
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Imagen traducida: una copia rotulada y los bloques de texto encontrados. */
class TranslatedImage(val bitmap: Bitmap, val blocks: List<TextBlock>, val note: String?) {
    val texts: List<Pair<String, String>> get() = blocks.map { it.text to it.translation }
}

/** Resultado de traducir un archivo de página. */
class TranslatedPage(val file: File, val texts: List<Pair<String, String>>, val note: String?)

/**
 * Traduce imágenes completas: páginas de la galería o capturas de pantalla.
 * Mantiene cargados los modelos entre usos (cargar manga-ocr tarda un par de
 * segundos). No es seguro usarlo desde varios hilos a la vez: úsalo a través
 * de [Engine], que lo comparte por turnos en toda la app.
 */
class PageTranslator(private val context: Context) : AutoCloseable {

    private var detector: MlKitDetector? = null
    private val ocrModel = OcrModel(context)
    private var ocr: MangaOcr? = null
    private var mlKit: Pair<String, MlKitTranslator>? = null
    private var claude: Pair<String, ClaudeTranslator>? = null
    private var geminiApi: Pair<String, GeminiApiTranslator>? = null
    private var qwen: Pair<String, LocalLlm>? = null
    private var qwenVision: QwenVision? = null
    private val qualityModels = QualityModels(context)
    private var textDetector: ComicTextDetector? = null
    private var lama: LamaInpainter? = null
    private var paddle: Pair<SourceLanguage, PaddleRecognizer>? = null

    /** Una página leída y lista para traducir (ver [prepare]). */
    private class Prepared(
        val page: PreparedPage,
        val processor: PageProcessor,
        val translator: Translator,
        val settings: Settings,
        /** La página para las IAs que la ven, con los números de sus textos desde el que se indique. */
        val pageImage: ((Int) -> ByteArray?)?,
        val note: String?,
        /** Avisos del motor (p. ej. "se usó la traducción sin conexión"), que llegan al traducir. */
        val engineNotes: List<String>,
    )

    /** Traduce [bitmap] (no lo modifica) y devuelve una copia con la traducción escrita. */
    fun translate(bitmap: Bitmap, onProgress: (String) -> Unit = {}): TranslatedImage {
        val prepared = prepare(bitmap, onProgress)
        val translations = translateAll(listOf(prepared), onProgress).single()
        return finish(prepared, translations, onProgress)
    }

    /** Traduce el archivo [source] y guarda la página rotulada en [output] (JPEG). */
    fun translateFile(source: File, output: File, onProgress: (String) -> Unit): TranslatedPage =
        translateFiles(listOf(source to output)) { _, msg -> onProgress(msg) }.single()

    /**
     * Varias páginas seguidas: las IAs que ven la página (Gemini, Claude) las
     * traducen en una sola petición, entendiendo mejor la escena y gastando
     * menos del límite gratuito. [onProgress] recibe el número de página.
     */
    fun translateFiles(files: List<Pair<File, File>>, onProgress: (Int, String) -> Unit): List<TranslatedPage> {
        val prepared = files.mapIndexed { i, (source, _) ->
            val bitmap = decode(source, MAX_PIXELS)
            try {
                prepare(bitmap) { onProgress(i, it) }
            } finally {
                bitmap.recycle()
            }
        }
        val translations = translateAll(prepared) { msg -> prepared.indices.forEach { onProgress(it, msg) } }
        return prepared.indices.map { i ->
            val result = finish(prepared[i], translations[i]) { onProgress(i, it) }
            val output = files[i].second
            output.parentFile?.mkdirs()
            output.outputStream().use { result.bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it) }
            result.bitmap.recycle()
            TranslatedPage(output, result.texts, result.note)
        }
    }

    /** ¿El motor elegido traduce varias páginas a la vez? (los que ven la página) */
    fun translatesSeveralPages(): Boolean {
        val settings = Settings(context)
        return (settings.engine == Settings.ENGINE_GEMINI_API && settings.geminiKey.isNotBlank()) ||
            (settings.engine == Settings.ENGINE_CLAUDE && settings.claudeKey.isNotBlank())
    }

    private fun translateAll(pages: List<Prepared>, onProgress: (String) -> Unit): List<List<Translation>> {
        if (pages.all { it.page.blocks.isEmpty() }) return pages.map { emptyList() }
        onProgress(if (pages.size > 1) "Traduciendo ${pages.size} páginas juntas…" else "Traduciendo…")
        var first = 0
        val inputs = pages.map { p ->
            PageTexts(p.page.texts, p.pageImage?.invoke(first)).also { first += p.page.blocks.size }
        }
        val story = pages.first().let { p -> if (p.settings.rememberStory) MangaApp.from(context).stories.current else null }
        return try {
            pages.first().translator.translatePages(inputs, story)
        } catch (e: Exception) {
            pages.forEach { it.page.cancel() }
            throw e
        }
    }

    /** Detecta el texto, lo lee, elige el motor y empieza a borrar el original. */
    private fun prepare(bitmap: Bitmap, onProgress: (String) -> Unit): Prepared {
        val settings = Settings(context)
        val source = settings.source
        // Qwen ocupa 1,5–3 GB de memoria: se suelta en cuanto se elige otro motor.
        if (settings.engine != Settings.ENGINE_QWEN) releaseQwen()

        onProgress("Buscando texto…")
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val image = PixelImage(w, h, pixels)

        // Detector de manga y borrado LaMa, si están descargados (si no, lo básico).
        // El detector trabaja a la vez que ML Kit.
        val quality = settings.useQualityModels && qualityModels.hasDetectorAndLama
        if (!quality) releaseQuality()
        if (settings.useQualityModels && !qualityModels.isDownloaded) MangaApp.from(context).quality.ensure()
        val layoutJob = if (quality) {
            CompletableFuture.supplyAsync {
                try {
                    (textDetector ?: qualityModels.loadDetector().also { textDetector = it }).detect(image)
                } catch (e: Exception) {
                    android.util.Log.w("MangaTraductor", "comic-text-detector falló", e)
                    null
                }
            }
        } else {
            null
        }
        val detections = detector(source).detect(bitmap)
        if (layoutJob != null) onProgress("Buscando globos…")
        val layout = layoutJob?.get()
        val inpainter: Inpainter = if (quality) {
            try {
                lama ?: qualityModels.loadInpainter().also { lama = it }
            } catch (e: Exception) {
                android.util.Log.w("MangaTraductor", "LaMa no se pudo cargar", e)
                SimpleInpainter
            }
        } else {
            SimpleInpainter
        }

        // manga-ocr sólo lee japonés; en chino y coreano se usa el texto de ML Kit.
        val wantsMangaOcr = settings.useMangaOcr && source == SourceLanguage.JAPANESE
        val reader = if (wantsMangaOcr && ocrModel.isAvailable) {
            ocr ?: ocrModel.load().also { ocr = it }
        } else {
            null
        }

        // En chino y coreano, cada línea que encuentra ML Kit la vuelve a leer PaddleOCR.
        val lineReader = if (quality && reader == null) paddleFor(source)?.asLineReader() else null

        // Versión ligera mientras se descarga manga-ocr: se usa el OCR básico de ML Kit.
        var note: String? = if (reader == null && wantsMangaOcr) {
            "manga-ocr todavía se está descargando: se usó el OCR básico (menos preciso)."
        } else {
            null
        }
        val notes = mutableListOf<String>()
        val offline = mlKitTranslator(source, settings.language)
        val translator: Translator = when {
            settings.engine == Settings.ENGINE_GEMINI_API && settings.geminiKey.isNotBlank() ->
                withFallback(geminiApiTranslator(settings.geminiKey, source, settings.language), offline, fillBlanks = false) { notes += it }
            settings.engine == Settings.ENGINE_CLAUDE && settings.claudeKey.isNotBlank() ->
                withFallback(claudeTranslator(settings.claudeKey, source, settings.language), offline, fillBlanks = false) { notes += it }
            settings.engine == Settings.ENGINE_GEMINI_NANO ->
                withFallback(GeminiNanoTranslator(context, settings.language, source), offline) { notes += it }
            settings.engine == Settings.ENGINE_QWEN -> {
                val llm = qwenModel(settings.qwenSize, onProgress) { note = it }
                if (llm == null) {
                    offline
                } else {
                    val vision = if (settings.qwenSeesPage) qwenVisionFor(settings.qwenSize) else null.also { releaseQwenVision() }
                    val qwenTranslator = QwenTranslator(llm, settings.language, source, vision, ::decodeJpeg, onProgress)
                    withFallback(qwenTranslator, offline) { notes += it }
                }
            }
            else -> {
                if (settings.engine == Settings.ENGINE_GEMINI_API) note = "Falta la clave de Gemini: se usó la traducción sin conexión."
                if (settings.engine == Settings.ENGINE_CLAUDE) note = "Falta la clave de Claude: se usó la traducción sin conexión."
                offline
            }
        }
        val processor = PageProcessor(reader, translator, source, inpainter, lineReader)
        val page = processor.prepare(image, detections, layout, onProgress)
        // Los motores en la nube (y Qwen, si se activa) ven también la página entera, con el
        // número de cada globo (se prepara ya: después la imagen original ya no está).
        val qwenSees = settings.engine == Settings.ENGINE_QWEN && qwenVision != null && settings.qwenSeesPage
        val seesPage = settings.engine == Settings.ENGINE_CLAUDE || settings.engine == Settings.ENGINE_GEMINI_API || qwenSees
        val pageImage: ((Int) -> ByteArray?)? = if (seesPage && page.blocks.isNotEmpty()) {
            val small = if (qwenSees) scaledForQwen(bitmap) else scaledForAi(bitmap)
            val boxes = page.boxes
            val job: (Int) -> ByteArray? = { first -> jpegForAi(small, boxes, first) }
            job
        } else {
            null
        }
        return Prepared(page, processor, translator, settings, pageImage, note, notes)
    }

    /** Pone las traducciones, rotula la página y la guarda en la memoria de la historia. */
    private fun finish(p: Prepared, translations: List<Translation>, onProgress: (String) -> Unit): TranslatedImage {
        val settings = p.settings
        val stories = MangaApp.from(context).stories
        val story = if (settings.rememberStory) stories.current else null
        val result = p.processor.finish(p.page, translations, story, onProgress)
        var note = p.engineNotes.lastOrNull() ?: p.note
        if (result.blocks.isEmpty()) note = "No se encontró texto en ${context.getString(sourceName(settings.source))}."
        if (story != null && result.blocks.isNotEmpty()) stories.save(story)

        val image = p.page.image
        val out = Bitmap.createBitmap(result.cleaned.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
        Typesetter(context, settings.uppercase, settings.language, settings.font).draw(out, result.blocks)
        return TranslatedImage(out, result.blocks, note)
    }

    private fun detector(source: SourceLanguage): MlKitDetector {
        detector?.let { if (it.source == source) return it else it.close() }
        return MlKitDetector(context, source).also { detector = it }
    }

    /**
     * Usa [primary]; si falla, traduce con [offline] y avisa con [onNote]. Con
     * [fillBlanks], las frases que [primary] deje vacías también se traducen
     * con [offline]; sin él (Claude y Gemini) una frase vacía es que la IA la
     * marcó como ruido (marca de agua, trazos del dibujo) y no se rotula.
     */
    private fun withFallback(
        primary: Translator, offline: MlKitTranslator, fillBlanks: Boolean = true, onNote: (String) -> Unit,
    ) = object : Translator {
        override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> =
            translatePages(listOf(PageTexts(texts, pageJpeg)), story).single().map { it.text }

        override fun translatePages(pages: List<PageTexts>, story: StoryContext?): List<List<Translation>> {
            val results = try {
                primary.translatePages(pages, story)
            } catch (e: TranslationException) {
                onNote("${e.message} Se usó la traducción sin conexión.")
                return pages.map { page -> offline.translate(page.texts, null, story).map { Translation(it) } }
            }
            return pages.zip(results).map { (page, result) ->
                val out = result.toMutableList()
                val missing = out.indices.filter { out[it].text.isBlank() }
                // Si la IA dejara vacía casi toda la página, no es ruido: algo falló.
                if (missing.isNotEmpty() && (fillBlanks || missing.size * 2 > page.texts.size)) {
                    val filled = offline.translate(missing.map { page.texts[it] }, null, story)
                    missing.forEachIndexed { i, index -> out[index] = Translation(filled[i], out[index].original, out[index].kind) }
                }
                out
            }
        }
    }

    /** Qwen cargado (o null, con el motivo en [onNote], si todavía no se puede usar). */
    private fun qwenModel(size: QwenModel.Size, onProgress: (String) -> Unit, onNote: (String) -> Unit): LocalLlm? {
        val model = QwenModel(context, size)
        qwen?.let { (key, llm) -> if (key == model.key) return llm }
        releaseQwen()
        if (!QwenModel.supported) {
            onNote(context.getString(R.string.qwen_unsupported) + " Se usó la traducción sin conexión.")
            return null
        }
        if (!model.isDownloaded) {
            onNote("Qwen todavía se está descargando: se usó la traducción sin conexión.")
            MangaApp.from(context).qwen.ensure()
            return null
        }
        onProgress("Cargando Qwen (unos segundos)…")
        return try {
            model.load().also { qwen = model.key to it }
        } catch (e: Exception) {
            onNote("No se pudo cargar Qwen (${e.message}). Se usó la traducción sin conexión.")
            null
        } catch (e: OutOfMemoryError) {
            onNote("No hay memoria suficiente para Qwen ${size.id.uppercase()}: prueba el tamaño 2B. Se usó la traducción sin conexión.")
            null
        }
    }

    private fun releaseQuality() {
        textDetector?.close()
        textDetector = null
        lama?.close()
        lama = null
        paddle?.second?.close()
        paddle = null
    }

    /** El lector de PaddleOCR del idioma (se cambia si se cambia de idioma), o null si falta. */
    private fun paddleFor(source: SourceLanguage): PaddleRecognizer? {
        paddle?.let { (language, recognizer) -> if (language == source) return recognizer }
        paddle?.second?.close()
        paddle = null
        return try {
            qualityModels.loadPaddle(source)?.also { paddle = source to it }
        } catch (e: Exception) {
            android.util.Log.w("MangaTraductor", "PaddleOCR no se pudo cargar", e)
            null
        }
    }

    private fun releaseQwen() {
        qwen?.second?.close()
        qwen = null
        releaseQwenVision()
    }

    private fun releaseQwenVision() {
        qwenVision?.close()
        qwenVision = null
    }

    /** El codificador de imagen de Qwen (se descarga aparte), o null si falta. */
    private fun qwenVisionFor(size: QwenModel.Size): QwenVision? {
        qwenVision?.let { return it }
        val model = QwenModel(context, size)
        if (!model.isDownloaded) {
            MangaApp.from(context).qwen.ensure()
            return null
        }
        return try {
            model.loadVision()?.also { qwenVision = it }
        } catch (e: Exception) {
            android.util.Log.w("MangaTraductor", "La vista de Qwen no se pudo cargar", e)
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** El JPEG de la página, en píxeles (para que Qwen la vea). */
    private fun decodeJpeg(bytes: ByteArray): PixelImage? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return PixelImage(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
    }

    private fun geminiApiTranslator(key: String, source: SourceLanguage, language: String): GeminiApiTranslator {
        val id = "$key|${source.code}|$language"
        geminiApi?.let { (cached, t) -> if (cached == id) return t }
        return GeminiApiTranslator(key, language, source).also { geminiApi = id to it }
    }

    private fun mlKitTranslator(source: SourceLanguage, language: String): MlKitTranslator {
        val id = "${source.code}|$language"
        mlKit?.let { (cached, t) -> if (cached == id) return t else t.close() }
        return MlKitTranslator(source, language).also { mlKit = id to it }
    }

    private fun claudeTranslator(key: String, source: SourceLanguage, language: String): ClaudeTranslator {
        val id = "$key|${source.code}|$language"
        claude?.let { (cached, t) -> if (cached == id) return t }
        return ClaudeTranslator(key, language, source).also { claude = id to it }
    }

    /**
     * La página reducida para que la IA vea el contexto (máx. 1568 px; las
     * tiras largas tipo webtoon, hasta ~4 MP).
     */
    /**
     * La página al tamaño con el que la ve Qwen (unos 250 trozos de 32 x 32):
     * los números de los globos se dibujan ya a ese tamaño para que los lea.
     */
    private fun scaledForQwen(bitmap: Bitmap): Pair<Bitmap, Float> {
        val (w, h) = QwenVision.targetSize(bitmap.width, bitmap.height)
        val scale = sqrt(w.toDouble() * h / (bitmap.width.toDouble() * bitmap.height)).toFloat().coerceAtMost(1f)
        val small = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
        return (if (small === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else small) to scale
    }

    private fun scaledForAi(bitmap: Bitmap): Pair<Bitmap, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val scale = if (h > w * 5 / 2) {
            minOf(1.0, sqrt(4_000_000.0 / (w.toLong() * h)), 7900.0 / h).toFloat()
        } else {
            minOf(1f, 1568f / max(w, h))
        }
        val small = Bitmap.createScaledBitmap(bitmap, max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()), true)
        return (if (small === bitmap) bitmap.copy(Bitmap.Config.ARGB_8888, false) else small) to scale
    }

    /**
     * La página en JPEG con una etiqueta roja con el número de cada texto
     * (desde [first]): así la IA sabe qué globo es cada frase y puede leerlo
     * ella misma si el OCR se equivocó.
     */
    private fun jpegForAi(scaled: Pair<Bitmap, Float>, boxes: List<Box>, first: Int): ByteArray {
        val (base, scale) = scaled
        val small = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(small)
        val radius = max(9f, min(small.width, small.height) * 0.016f).coerceAtMost(16f)
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 20, 20) }
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = radius * 1.25f
        }
        boxes.forEachIndexed { i, box ->
            val cx = (box.left * scale - radius * 0.4f).coerceIn(radius, small.width - radius)
            val cy = (box.top * scale - radius * 0.4f).coerceIn(radius, small.height - radius)
            canvas.drawCircle(cx, cy, radius, circle)
            canvas.drawText((first + i).toString(), cx, cy - (number.ascent() + number.descent()) / 2, number)
        }
        val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        small.recycle()
        return bytes
    }

    override fun close() {
        detector?.close()
        ocr?.close()
        releaseQwen()
        releaseQuality()
        mlKit?.second?.close()
    }

    companion object {
        /** Píxeles máximos al procesar (~2000x3000): más no mejora el OCR y gasta mucha memoria. */
        const val MAX_PIXELS = 6_000_000L

        /** Las tiras largas (webtoon, manhua, manhwa) pueden tener más, para no dejar la letra diminuta. */
        const val MAX_PIXELS_LONG_STRIP = 20_000_000L

        fun decode(file: File, maxPixels: Long): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            @Suppress("NAME_SHADOWING")
            val maxPixels = if (bounds.outHeight > bounds.outWidth * 5 / 2) max(maxPixels, MAX_PIXELS_LONG_STRIP) else maxPixels
            var sample = 1
            while ((bounds.outWidth / (sample * 2)).toLong() * (bounds.outHeight / (sample * 2)) >= maxPixels) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(file.path, options)
                ?: throw IllegalArgumentException("No se pudo abrir la imagen.")
            val scale = sqrt(maxPixels.toDouble() / (bitmap.width.toLong() * bitmap.height))
            if (scale >= 1.0) return bitmap
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true,
            )
            bitmap.recycle()
            return scaled
        }
    }
}
