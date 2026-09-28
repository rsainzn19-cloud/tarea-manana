package com.mangatraductor.app

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.mangatraductor.core.Box
import com.mangatraductor.core.DetectedText
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translator
import java.util.concurrent.ExecutionException

/** Detección de texto japonés con ML Kit (el modelo va dentro del APK, funciona sin conexión). */
class MlKitDetector : AutoCloseable {
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    /** Debe llamarse fuera del hilo principal. */
    fun detect(bitmap: Bitmap): List<DetectedText> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val out = mutableListOf<DetectedText>()
        for (block in result.textBlocks) {
            val lines = block.lines.mapNotNull { line ->
                line.boundingBox?.let { DetectedText(Box(it.left, it.top, it.right, it.bottom), line.text) }
            }
            if (lines.isNotEmpty()) {
                out += lines
            } else {
                block.boundingBox?.let { out += DetectedText(Box(it.left, it.top, it.right, it.bottom), block.text) }
            }
        }
        return out.filter { it.box.width > 2 && it.box.height > 2 }
    }

    override fun close() = recognizer.close()
}

/**
 * Traducción sin conexión con ML Kit. La primera vez descarga el diccionario
 * japonés (≈30 MB); después funciona sin internet.
 */
class MlKitTranslator(target: String) : Translator, AutoCloseable {
    private val client = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
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

    override fun translate(texts: List<String>, pageJpeg: ByteArray?): List<String> {
        ensureModel()
        return texts.map { Tasks.await(client.translate(it)) }
    }

    override fun close() = client.close()
}
