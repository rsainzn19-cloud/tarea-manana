package com.mangatraductor.app

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.mangatraductor.core.Acceleration
import com.mangatraductor.core.ComicTextDetector
import com.mangatraductor.core.LamaInpainter
import com.mangatraductor.core.PaddleRecognizer
import com.mangatraductor.core.SourceLanguage
import java.io.IOException

/**
 * Los modelos de los mejores traductores de manga de código abierto
 * (manga-image-translator, BallonsTranslator, Koharu), en ONNX:
 * - comic-text-detector: encuentra cada bloque de texto entero y marca los
 *   píxeles exactos de las letras (95 MB).
 * - LaMa para manga (AnimeMangaInpainting): reconstruye el dibujo y las
 *   tramas bajo el texto que no está en un globo liso (208 MB).
 * - En chino y coreano, el lector de PaddleOCR (PP-OCRv5) de ese idioma
 *   (unos 15 MB): lee mejor que ML Kit.
 * Se descargan de Hugging Face (revisiones fijas) la primera vez.
 */
class QualityModels(context: Context) : DownloadableModel(context) {

    override val key = "modelos-calidad"

    private val base = listOf(
        ModelFile(DETECTOR, 94_669_756, "1a86ace74961413cbd650002e7bb4dcec4980ffa21b2f19b86933372071d718f",
            "mayocream/comic-text-detector-onnx/resolve/a5d67ec772adef819ef5b0e7aa701fcf4c8bf74a/$DETECTOR"),
        ModelFile(LAMA, 207_482_644, "4512adab295ee5a5e02ccd1bdf8d45dccbac88309d9cff1532ffd5de876f02a4",
            "mayocream/lama-manga-onnx/resolve/b55497aadbfcb9740e1ed16f008268d71b4f3f79/$LAMA"),
    )

    /** Los archivos de PaddleOCR de [source] (modelo y diccionario), o ninguno en japonés (manga-ocr). */
    fun paddleFiles(source: SourceLanguage): List<ModelFile> = when (source) {
        SourceLanguage.JAPANESE -> emptyList()
        SourceLanguage.CHINESE -> listOf(
            ModelFile("paddle-zh.onnx", 16_534_782, "da72dc72ca4dc220df0dfde68c1dedc31c58d3e76a25871122e5056227d50092",
                "PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/$PADDLE_ZH/inference.onnx"),
            ModelFile("paddle-zh.yml", 148_345, "5dfeb2777f6d0db8177d8128a8acfcf6e6276dc4ac73ea3bf0dc06d6a5e85d8e",
                "PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/$PADDLE_ZH/inference.yml"),
        )
        SourceLanguage.KOREAN -> listOf(
            ModelFile("paddle-ko.onnx", 13_418_787, "92f0b7785e64fc9090106a241cf4c1eb97472824558272751b88a2a4476d3a08",
                "PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/$PADDLE_KO/inference.onnx"),
            ModelFile("paddle-ko.yml", 96_039, "f757fa1c40e99edcf27e9cce879b93eb2a51fa46f5ef39095689b8c37dd75998",
                "PaddlePaddle/korean_PP-OCRv5_mobile_rec_onnx/resolve/$PADDLE_KO/inference.yml"),
        )
    }

    /** El detector y LaMa, más el lector del idioma elegido. */
    override val files: List<ModelFile> get() = base + paddleFiles(Settings(context).source)

    /** Lo que falta por bajar (para la tarjeta de descarga). */
    val missingBytes: Long get() = files.filter { installed(it) == null }.sumOf { it.size }

    /** El detector y LaMa ya están (aunque falte el lector de chino o coreano). */
    val hasDetectorAndLama: Boolean get() = base.all { installed(it) != null }

    override val sources: List<(ModelFile) -> String> = listOf { f -> "https://huggingface.co/${f.remotePath}" }

    override val title: String get() = context.getString(R.string.quality_download_title)

    private val threads get() = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

    /**
     * La primera vez se mide en este móvil si va más rápido con la CPU o con
     * XNNPACK, y se recuerda.
     */
    private val accelerations = context.getSharedPreferences("aceleracion", Context.MODE_PRIVATE)

    fun loadDetector(): ComicTextDetector {
        val file = installed(base[0]) ?: throw IOException("Falta descargar el detector")
        accelerations.getString("detector", null)?.let { return ComicTextDetector.load(file, threads, Acceleration.valueOf(it)) }
        return ComicTextDetector.loadFastest(file, threads).also { remember("detector", it.acceleration) }
    }

    fun loadInpainter(): LamaInpainter {
        val file = installed(base[1]) ?: throw IOException("Falta descargar LaMa")
        accelerations.getString("lama", null)?.let { return LamaInpainter.load(file, threads, Acceleration.valueOf(it)) }
        return LamaInpainter.loadFastest(file, threads).also { remember("lama", it.acceleration) }
    }

    /** El lector de PaddleOCR de [source], si está descargado. */
    fun loadPaddle(source: SourceLanguage): PaddleRecognizer? {
        val (model, dictionary) = paddleFiles(source).ifEmpty { return null }.map { installed(it) ?: return null }
        return PaddleRecognizer.load(model, dictionary, threads)
    }

    private fun remember(model: String, acceleration: Acceleration) {
        Log.i("MangaTraductor", "$model: más rápido con $acceleration")
        accelerations.edit { putString(model, acceleration.name) }
    }

    private companion object {
        const val DETECTOR = "comic-text-detector.onnx"
        const val LAMA = "lama-manga.onnx"
        const val PADDLE_ZH = "ed152b8b495f84de93cda5709d768548a9127622"
        const val PADDLE_KO = "5c6f574b8e2230adf4287b33e736d71b9fabd28e"
    }
}
