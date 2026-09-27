package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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
import com.mangatraductor.core.Translator

/**
 * Detección de texto japonés con ML Kit. El modelo lo aporta Google Play
 * Services: la primera vez se descarga (unos MB) y después funciona sin conexión.
 */
class MlKitDetector(context: Context) : AutoCloseable {
    private val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val modules = ModuleInstall.getClient(context)
    private var ready = false

    /** Asegura que el modelo esté instalado (si la app no vino de Play Store no se instala solo). */
    private fun ensureModel() {
        if (ready) return
        if (!isAvailable()) {
            Tasks.await(modules.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()))
            // La petición vuelve enseguida; la descarga sigue en segundo plano.
            val deadline = System.currentTimeMillis() + 3 * 60_000
            while (!isAvailable()) {
                if (System.currentTimeMillis() > deadline) {
                    throw IllegalStateException(
                        "Google Play Services no terminó de descargar el OCR japonés. Revisa la conexión y reintenta.")
                }
                Thread.sleep(1000)
            }
        }
        ready = true
    }

    private fun isAvailable(): Boolean =
        Tasks.await(modules.areModulesAvailable(recognizer)).areModulesAvailable()

    /** Debe llamarse fuera del hilo principal. */
    fun detect(bitmap: Bitmap): List<DetectedText> {
        ensureModel()
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
        Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
    }

    override fun translate(texts: List<String>, pageJpeg: ByteArray?): List<String> {
        ensureModel()
        return texts.map { Tasks.await(client.translate(it)) }
    }

    override fun close() = client.close()
}
