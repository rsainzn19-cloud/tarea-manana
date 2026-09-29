package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.mangatraductor.core.Box
import com.mangatraductor.core.DetectedText
import com.mangatraductor.core.SourceLanguage
import com.mangatraductor.core.Tiles
import com.mangatraductor.core.StoryContext
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translator
import java.util.concurrent.ExecutionException

/**
 * Detección de texto con ML Kit, con el modelo del idioma del cómic (japonés,
 * chino o coreano). Funciona sin conexión: en la versión completa los modelos
 * van dentro del APK; en la ligera los descarga Google Play Services la
 * primera vez (ver [Flavor.prepareDetector]).
 */
class MlKitDetector(private val context: Context, val source: SourceLanguage) : AutoCloseable {
    private val recognizer = TextRecognition.getClient(
        when (source) {
            SourceLanguage.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
            SourceLanguage.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            SourceLanguage.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
        }
    )
    private var prepared = false

    /** Debe llamarse fuera del hilo principal. */
    fun detect(bitmap: Bitmap): List<DetectedText> {
        if (!prepared) {
            Flavor.prepareDetector(context, recognizer)
            prepared = true
        }
        val tiles = tiles(bitmap.width, bitmap.height)
        if (tiles.size == 1) return detectIn(bitmap, 0)
        // Páginas muy largas (webtoon): por trozos que se solapan, para no perder la letra pequeña.
        val all = tiles.flatMap { (top, height) ->
            val tile = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
            try {
                detectIn(tile, top)
            } finally {
                tile.recycle()
            }
        }
        return dedupe(all)
    }

    private fun detectIn(bitmap: Bitmap, offsetY: Int): List<DetectedText> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val out = mutableListOf<DetectedText>()
        for (block in result.textBlocks) {
            val lines = block.lines.mapNotNull { line ->
                line.boundingBox?.let { DetectedText(Box(it.left, it.top + offsetY, it.right, it.bottom + offsetY), line.text) }
            }
            if (lines.isNotEmpty()) {
                out += lines
            } else {
                block.boundingBox?.let { out += DetectedText(Box(it.left, it.top + offsetY, it.right, it.bottom + offsetY), block.text) }
            }
        }
        return out.filter { it.box.width > 2 && it.box.height > 2 }
    }

    override fun close() = recognizer.close()

    companion object {
        /** Trozos (arriba, alto) en que se parte una imagen muy alta (ver [Tiles.vertical]). */
        fun tiles(width: Int, height: Int): List<Pair<Int, Int>> = Tiles.vertical(width, height)

        /** Quita lo repetido en las zonas solapadas (se queda con la caja más grande: la no cortada). */
        fun dedupe(found: List<DetectedText>): List<DetectedText> {
            val kept = mutableListOf<DetectedText>()
            for (d in found.sortedByDescending { it.box.area }) {
                if (kept.none { it.box.overlapArea(d.box) >= d.box.area * 0.6 }) kept += d
            }
            return kept
        }
    }
}

/**
 * Traducción sin conexión con ML Kit. La primera vez descarga el diccionario
 * del idioma del cómic (≈30 MB); después funciona sin internet.
 */
class MlKitTranslator(source: SourceLanguage, target: String) : Translator, AutoCloseable {
    private val client = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(
                when (source) {
                    SourceLanguage.JAPANESE -> TranslateLanguage.JAPANESE
                    SourceLanguage.CHINESE -> TranslateLanguage.CHINESE
                    SourceLanguage.KOREAN -> TranslateLanguage.KOREAN
                }
            )
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.ENGLISH)
            .build()
    )

    /** Debe llamarse fuera del hilo principal. */
    fun ensureModel() {
        try {
            Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
        } catch (e: ExecutionException) {
            throw TranslationException(
                "Falta el diccionario de traducción: conéctate a internet una vez para descargarlo (≈30 MB).", e)
        }
    }

    /** Traduce frase a frase: no entiende contexto, así que [story] no se usa. */
    override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> {
        ensureModel()
        return texts.map { Tasks.await(client.translate(it)) }
    }

    override fun close() = client.close()
}
