package com.mangatraductor.app

import android.content.Context
import com.mangatraductor.core.MangaOcr
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * manga-ocr viene dentro del APK (assets sin comprimir, ver app/build.gradle.kts).
 * Se mapea en memoria directamente desde el APK: no se copia ni se descarga nada.
 */
class OcrModel(private val context: Context) {

    val isAvailable: Boolean
        get() = try {
            context.assets.openFd(ENCODER).close()
            true
        } catch (e: IOException) {
            false
        }

    fun load(): MangaOcr {
        val vocab = context.assets.open("manga_ocr_vocab.txt").bufferedReader().readLines()
        return MangaOcr.fromBuffers(
            map(ENCODER),
            map(DECODER),
            vocab,
            threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
        )
    }

    private fun map(name: String): MappedByteBuffer = context.assets.openFd(name).use { fd ->
        fd.createInputStream().channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private companion object {
        const val ENCODER = "manga_ocr/encoder_model_quantized.onnx"
        const val DECODER = "manga_ocr/decoder_model_quantized.onnx"
    }
}
