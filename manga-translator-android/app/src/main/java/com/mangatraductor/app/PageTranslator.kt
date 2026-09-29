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
import com.mangatraductor.core.GeminiApiTranslator
import com.mangatraductor.core.LocalLlm
import com.mangatraductor.core.MangaOcr
import com.mangatraductor.core.PageProcessor
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.QwenTranslator
import com.mangatraductor.core.SourceLanguage
import com.mangatraductor.core.StoryContext
import com.mangatraductor.core.TextBlock
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translator
import java.io.ByteArrayOutputStream
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

    /** Traduce [bitmap] (no lo modifica) y devuelve una copia con la traducción escrita. */
    fun translate(bitmap: Bitmap, onProgress: (String) -> Unit = {}): TranslatedImage {
        val settings = Settings(context)
        val source = settings.source
        // Qwen ocupa 1,5–3 GB de memoria: se suelta en cuanto se elige otro motor.
        if (settings.engine != Settings.ENGINE_QWEN) releaseQwen()

        onProgress("Buscando texto…")
        val detections = detector(source).detect(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val image = PixelImage(w, h, pixels)

        // manga-ocr sólo lee japonés; en chino y coreano se usa el texto de ML Kit.
        val wantsMangaOcr = settings.useMangaOcr && source == SourceLanguage.JAPANESE
        val reader = if (wantsMangaOcr && ocrModel.isAvailable) {
            ocr ?: ocrModel.load().also { ocr = it }
        } else {
            null
        }

        // Versión ligera mientras se descarga manga-ocr: se usa el OCR básico de ML Kit.
        var note: String? = if (reader == null && wantsMangaOcr) {
            "manga-ocr todavía se está descargando: se usó el OCR básico (menos preciso)."
        } else {
            null
        }
        val offline = mlKitTranslator(source, settings.language)
        val translator: Translator = when {
            settings.engine == Settings.ENGINE_GEMINI_API && settings.geminiKey.isNotBlank() ->
                withFallback(geminiApiTranslator(settings.geminiKey, source, settings.language), offline, fillBlanks = false) { note = it }
            settings.engine == Settings.ENGINE_CLAUDE && settings.claudeKey.isNotBlank() ->
                withFallback(claudeTranslator(settings.claudeKey, source, settings.language), offline, fillBlanks = false) { note = it }
            settings.engine == Settings.ENGINE_GEMINI_NANO ->
                withFallback(GeminiNanoTranslator(context, settings.language, source), offline) { note = it }
            settings.engine == Settings.ENGINE_QWEN -> {
                val llm = qwenModel(settings.qwenSize, onProgress) { note = it }
                if (llm == null) offline
                else withFallback(QwenTranslator(llm, settings.language, source, onProgress), offline) { note = it }
            }
            else -> {
                if (settings.engine == Settings.ENGINE_GEMINI_API) note = "Falta la clave de Gemini: se usó la traducción sin conexión."
                if (settings.engine == Settings.ENGINE_CLAUDE) note = "Falta la clave de Claude: se usó la traducción sin conexión."
                offline
            }
        }
        // Los motores en la nube ven también la página entera, con el número de cada globo.
        val seesPage = settings.engine == Settings.ENGINE_CLAUDE || settings.engine == Settings.ENGINE_GEMINI_API
        val pageImage: ((List<Box>) -> ByteArray?)? = if (seesPage) { boxes -> jpegForAi(bitmap, boxes) } else null

        // Memoria de la historia: las páginas anteriores ayudan a traducir esta.
        val stories = MangaApp.from(context).stories
        val story = if (settings.rememberStory) stories.current else null

        val result = PageProcessor(reader, translator, source).process(image, detections, pageImage, story, onProgress)
        if (result.blocks.isEmpty()) note = "No se encontró texto en ${context.getString(sourceName(source))}."
        if (story != null && result.blocks.isNotEmpty()) stories.save(story)

        val out = Bitmap.createBitmap(result.cleaned.argb, w, h, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
        Typesetter(context, settings.uppercase, settings.language).draw(out, result.blocks)
        return TranslatedImage(out, result.blocks, note)
    }

    /** Traduce el archivo [source] y guarda la página rotulada en [output] (JPEG). */
    fun translateFile(source: File, output: File, onProgress: (String) -> Unit): TranslatedPage {
        val bitmap = decode(source, MAX_PIXELS)
        val result = translate(bitmap, onProgress)
        output.parentFile?.mkdirs()
        output.outputStream().use { result.bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        bitmap.recycle()
        result.bitmap.recycle()
        return TranslatedPage(output, result.texts, result.note)
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
        override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> {
            val result = try {
                primary.translate(texts, pageJpeg, story).toMutableList()
            } catch (e: TranslationException) {
                onNote("${e.message} Se usó la traducción sin conexión.")
                return offline.translate(texts, null, story)
            }
            val missing = result.indices.filter { result[it].isBlank() }
            // Si la IA dejara vacía casi toda la página, no es ruido: algo falló.
            if (missing.isNotEmpty() && (fillBlanks || missing.size * 2 > texts.size)) {
                val filled = offline.translate(missing.map { texts[it] }, null, story)
                missing.forEachIndexed { i, index -> result[index] = filled[i] }
            }
            return result
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

    private fun releaseQwen() {
        qwen?.second?.close()
        qwen = null
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
     * La página reducida en JPEG para que la IA vea el contexto (máx. 1568 px;
     * las tiras largas tipo webtoon, hasta ~4 MP), con una etiqueta roja con el
     * número de cada texto: así la IA sabe qué globo es cada frase y puede
     * leerlo ella misma si el OCR se equivocó.
     */
    private fun jpegForAi(bitmap: Bitmap, boxes: List<Box>): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val scale = if (h > w * 5 / 2) {
            minOf(1.0, sqrt(4_000_000.0 / (w.toLong() * h)), 7900.0 / h).toFloat()
        } else {
            minOf(1f, 1568f / max(w, h))
        }
        val small = Bitmap.createScaledBitmap(bitmap, max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()), true)
            .let { if (it === bitmap || !it.isMutable) it.copy(Bitmap.Config.ARGB_8888, true) else it }
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
            canvas.drawText(i.toString(), cx, cy - (number.ascent() + number.descent()) / 2, number)
        }
        val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        small.recycle()
        return bytes
    }

    override fun close() {
        detector?.close()
        ocr?.close()
        releaseQwen()
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
