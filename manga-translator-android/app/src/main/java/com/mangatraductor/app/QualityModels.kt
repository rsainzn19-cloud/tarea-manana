package com.mangatraductor.app

import android.content.Context
import com.mangatraductor.core.ComicTextDetector
import com.mangatraductor.core.LamaInpainter
import java.io.IOException

/**
 * Los modelos de los mejores traductores de manga de código abierto
 * (manga-image-translator, BallonsTranslator, Koharu), en ONNX:
 * - comic-text-detector: encuentra cada bloque de texto entero y marca los
 *   píxeles exactos de las letras (95 MB).
 * - LaMa para manga (AnimeMangaInpainting): reconstruye el dibujo y las
 *   tramas bajo el texto que no está en un globo liso (208 MB).
 * Se descargan de Hugging Face (revisiones fijas) la primera vez.
 */
class QualityModels(context: Context) : DownloadableModel(context) {

    override val key = "modelos-calidad"

    override val files = listOf(
        ModelFile(DETECTOR, 94_669_756, "1a86ace74961413cbd650002e7bb4dcec4980ffa21b2f19b86933372071d718f",
            "mayocream/comic-text-detector-onnx/resolve/a5d67ec772adef819ef5b0e7aa701fcf4c8bf74a/$DETECTOR"),
        ModelFile(LAMA, 207_482_644, "4512adab295ee5a5e02ccd1bdf8d45dccbac88309d9cff1532ffd5de876f02a4",
            "mayocream/lama-manga-onnx/resolve/b55497aadbfcb9740e1ed16f008268d71b4f3f79/$LAMA"),
    )

    override val sources: List<(ModelFile) -> String> = listOf { f -> "https://huggingface.co/${f.remotePath}" }

    override val title: String get() = context.getString(R.string.quality_download_title)

    private val threads get() = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

    fun loadDetector(): ComicTextDetector =
        ComicTextDetector.load(installed(files[0]) ?: throw IOException("Falta descargar el detector"), threads)

    fun loadInpainter(): LamaInpainter =
        LamaInpainter.load(installed(files[1]) ?: throw IOException("Falta descargar LaMa"), threads)

    private companion object {
        const val DETECTOR = "comic-text-detector.onnx"
        const val LAMA = "lama-manga.onnx"
    }
}
