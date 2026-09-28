package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mangatraductor.core.ClaudeTranslator
import com.mangatraductor.core.GeminiApiTranslator
import com.mangatraductor.core.MangaOcr
import com.mangatraductor.core.PageProcessor
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.StoryContext
import com.mangatraductor.core.TextBlock
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translator
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
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

    private val detector = MlKitDetector(context)
    private val ocrModel = OcrModel(context)
    private var ocr: MangaOcr? = null
    private var mlKit: Pair<String, MlKitTranslator>? = null
    private var claude: Pair<String, ClaudeTranslator>? = null
    private var geminiApi: Pair<String, GeminiApiTranslator>? = null

    /** Traduce [bitmap] (no lo modifica) y devuelve una copia con la traducción escrita. */
    fun translate(bitmap: Bitmap, onProgress: (String) -> Unit = {}): TranslatedImage {
        val settings = Settings(context)

        onProgress("Buscando texto…")
        val detections = detector.detect(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val image = PixelImage(w, h, pixels)

        val reader = if (settings.useMangaOcr && ocrModel.isAvailable) {
            ocr ?: ocrModel.load().also { ocr = it }
        } else {
            null
        }

        // Versión ligera mientras se descarga manga-ocr: se usa el OCR básico de ML Kit.
        var note: String? = if (reader == null && settings.useMangaOcr) {
            "manga-ocr todavía se está descargando: se usó el OCR básico (menos preciso)."
        } else {
            null
        }
        val offline = mlKitTranslator(settings.language)
        val translator: Translator = when {
            settings.engine == Settings.ENGINE_GEMINI_API && settings.geminiKey.isNotBlank() ->
                withFallback(geminiApiTranslator(settings.geminiKey, settings.language), offline) { note = it }
            settings.engine == Settings.ENGINE_CLAUDE && settings.claudeKey.isNotBlank() ->
                withFallback(claudeTranslator(settings.claudeKey, settings.language), offline) { note = it }
            settings.engine == Settings.ENGINE_GEMINI_NANO ->
                withFallback(GeminiNanoTranslator(context, settings.language), offline) { note = it }
            else -> {
                if (settings.engine == Settings.ENGINE_GEMINI_API) note = "Falta la clave de Gemini: se usó la traducción sin conexión."
                if (settings.engine == Settings.ENGINE_CLAUDE) note = "Falta la clave de Claude: se usó la traducción sin conexión."
                offline
            }
        }
        // Los motores en la nube ven también la página entera.
        val seesPage = settings.engine == Settings.ENGINE_CLAUDE || settings.engine == Settings.ENGINE_GEMINI_API
        val pageJpeg = if (seesPage) jpegForAi(bitmap) else null

        // Memoria de la historia: las páginas anteriores ayudan a traducir esta.
        val stories = MangaApp.from(context).stories
        val story = if (settings.rememberStory) stories.current else null

        val result = PageProcessor(reader, translator).process(image, detections, pageJpeg, story, onProgress)
        if (result.blocks.isEmpty()) note = "No se encontró texto japonés."
        if (story != null && result.blocks.isNotEmpty()) stories.save(story)

        val out = Bitmap.createBitmap(result.cleaned.argb, w, h, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
        Typesetter(context, settings.uppercase).draw(out, result.blocks)
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

    /**
     * Usa [primary]; si falla, traduce con [offline] y avisa con [onNote]. Las
     * frases que [primary] deje vacías también se traducen con [offline].
     */
    private fun withFallback(primary: Translator, offline: MlKitTranslator, onNote: (String) -> Unit) = object : Translator {
        override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> {
            val result = try {
                primary.translate(texts, pageJpeg, story).toMutableList()
            } catch (e: TranslationException) {
                onNote("${e.message} Se usó la traducción sin conexión.")
                return offline.translate(texts, null, story)
            }
            val missing = result.indices.filter { result[it].isBlank() }
            if (missing.isNotEmpty()) {
                val filled = offline.translate(missing.map { texts[it] }, null, story)
                missing.forEachIndexed { i, index -> result[index] = filled[i] }
            }
            return result
        }
    }

    private fun geminiApiTranslator(key: String, language: String): GeminiApiTranslator {
        val id = "$key|$language"
        geminiApi?.let { (cached, t) -> if (cached == id) return t }
        return GeminiApiTranslator(key, language).also { geminiApi = id to it }
    }

    private fun mlKitTranslator(language: String): MlKitTranslator {
        mlKit?.let { (lang, t) -> if (lang == language) return t else t.close() }
        return MlKitTranslator(language).also { mlKit = language to it }
    }

    private fun claudeTranslator(key: String, language: String): ClaudeTranslator {
        val id = "$key|$language"
        claude?.let { (cached, t) -> if (cached == id) return t }
        return ClaudeTranslator(key, language).also { claude = id to it }
    }

    /** Imagen reducida (máx. 1568 px) en JPEG para que la IA vea el contexto. */
    private fun jpegForAi(bitmap: Bitmap): ByteArray {
        val scale = 1568f / max(bitmap.width, bitmap.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
        } else {
            bitmap
        }
        val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        if (small !== bitmap) small.recycle()
        return bytes
    }

    override fun close() {
        detector.close()
        ocr?.close()
        mlKit?.second?.close()
    }

    companion object {
        /** Píxeles máximos al procesar (~2000x3000): más no mejora el OCR y gasta mucha memoria. */
        const val MAX_PIXELS = 6_000_000L

        fun decode(file: File, maxPixels: Long): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
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
