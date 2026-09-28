package com.mangatraductor.app

import android.content.Context
import com.mangatraductor.core.MangaOcr
import java.io.File
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * El modelo manga-ocr. En la versión completa viene dentro del APK (assets sin
 * comprimir, ver app/build.gradle.kts) y se mapea en memoria directamente desde
 * el APK. En la versión ligera se descarga una vez (ver [ModelDownloader]).
 */
class OcrModel(context: Context) : DownloadableModel(context) {

    override val key = "manga-ocr"

    override val files = listOf(
        ModelFile("encoder_model_quantized.onnx", 86_967_767,
            "ddd1af56963093795705fa38da6ce7e6567d1658e7c7359db7e13fcd37dbf279"),
        ModelFile("decoder_model_quantized.onnx", 29_627_936,
            "2e7177d2b0a59f1c612b694ed70c13971bee765cc2b2bc7bc9376e4753652f27"),
    )

    /**
     * Servidores, en orden: la copia publicada en GitHub (junto a la app) y el
     * original en Hugging Face (revisión fija).
     */
    override val sources: List<(ModelFile) -> String> = SOURCES.map { base -> { f: ModelFile -> "$base/${f.name}" } }

    override val title: String get() = context.getString(R.string.ocr_download_title)

    override val prefsName = "descarga_ocr"

    override val legacyDirs = listOf(File(context.filesDir, "manga-ocr")) // versiones anteriores

    /** El modelo va dentro del APK (versión completa). */
    val inApk: Boolean
        get() = try {
            context.assets.openFd(ASSET_DIR + files[0].name).close()
            true
        } catch (e: IOException) {
            false
        }

    override val isAvailable: Boolean get() = inApk || isDownloaded

    fun load(): MangaOcr {
        val vocab = context.assets.open("manga_ocr_vocab.txt").bufferedReader().readLines()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        return if (inApk) {
            MangaOcr.fromBuffers(mapAsset(files[0].name), mapAsset(files[1].name), vocab, threads)
        } else {
            val encoder = installed(files[0]) ?: throw IOException("Falta descargar manga-ocr")
            val decoder = installed(files[1]) ?: throw IOException("Falta descargar manga-ocr")
            MangaOcr.fromFiles(encoder.path, decoder.path, vocab, threads)
        }
    }

    private fun mapAsset(name: String): MappedByteBuffer = context.assets.openFd(ASSET_DIR + name).use { fd ->
        fd.createInputStream().channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    companion object {
        private const val ASSET_DIR = "manga_ocr/"

        val SOURCES = listOf(
            "https://github.com/rsainzn19-cloud/tarea-manana/releases/download/manga-ocr-modelo",
            "https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/f9023406bb2f6b17df67bc4a327c56ecd20611f0/onnx",
        )
    }
}
