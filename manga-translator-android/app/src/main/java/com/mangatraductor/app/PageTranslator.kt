package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mangatraductor.core.ClaudeTranslator
import com.mangatraductor.core.MangaOcr
import com.mangatraductor.core.PageProcessor
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translator
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Resultado de traducir una página. */
class TranslatedPage(val file: File, val texts: List<Pair<String, String>>, val note: String?)

/**
 * Traduce páginas completas. Mantiene cargados los modelos entre páginas
 * (cargar manga-ocr tarda un par de segundos). No es seguro usarlo desde
 * varios hilos a la vez: el ViewModel procesa las páginas de una en una.
 */
class PageTranslator(private val context: Context) : AutoCloseable {

    private val detector = MlKitDetector(context)
    private val ocrModel = OcrModel(context)
    private var ocr: MangaOcr? = null
    private var mlKit: Pair<String, MlKitTranslator>? = null
    private var claude: Pair<String, ClaudeTranslator>? = null

    fun translate(source: File, output: File, onProgress: (String) -> Unit): TranslatedPage {
        val settings = Settings(context)

        onProgress("Buscando texto…")
        val bitmap = decode(source, MAX_PIXELS)
        val detections = detector.detect(bitmap)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val image = PixelImage(w, h, pixels)

        val reader = if (settings.useMangaOcr && ocrModel.isDownloaded) {
            ocr ?: ocrModel.load().also { ocr = it }
        } else {
            null
        }

        var note: String? = null
        val offline = mlKitTranslator(settings.language)
        val translator: Translator = if (settings.engine == Settings.ENGINE_CLAUDE && settings.claudeKey.isNotBlank()) {
            // Si Claude falla, la página se traduce igualmente sin conexión.
            val primary = claudeTranslator(settings.claudeKey, settings.language)
            object : Translator {
                override fun translate(texts: List<String>, pageJpeg: ByteArray?): List<String> = try {
                    primary.translate(texts, pageJpeg)
                } catch (e: TranslationException) {
                    note = "${e.message} Se usó la traducción sin conexión."
                    offline.translate(texts, null)
                }
            }
        } else {
            if (settings.engine == Settings.ENGINE_CLAUDE) note = "Falta la clave de Claude: se usó la traducción sin conexión."
            onProgress("Preparando traductor…")
            offline.ensureModel()
            offline
        }
        val pageJpeg = if (settings.engine == Settings.ENGINE_CLAUDE) jpegForClaude(bitmap) else null

        val result = PageProcessor(reader, translator).process(image, detections, pageJpeg, onProgress)
        if (result.blocks.isEmpty()) note = "No se encontró texto japonés en esta página."

        val out = Bitmap.createBitmap(result.cleaned.argb, w, h, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
        Typesetter(context, settings.uppercase).draw(out, result.blocks)

        output.parentFile?.mkdirs()
        output.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        bitmap.recycle()
        out.recycle()
        return TranslatedPage(output, result.blocks.map { it.text to it.translation }, note)
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

    /** Página reducida (máx. 1568 px) en JPEG para que Claude vea el contexto. */
    private fun jpegForClaude(bitmap: Bitmap): ByteArray {
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
