package com.mangatraductor.app

import android.content.Context
import com.mangatraductor.core.MangaOcr
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * El modelo manga-ocr. En la versión completa viene dentro del APK (assets sin
 * comprimir, ver app/build.gradle.kts) y se mapea en memoria directamente desde
 * el APK. En la versión ligera se descarga una vez (revisión fija, SHA-256
 * comprobado) a los archivos privados de la app.
 */
class OcrModel(private val context: Context) {

    private class ModelFile(val name: String, val size: Long, val sha256: String)

    private val files = listOf(
        ModelFile("encoder_model_quantized.onnx", 86_967_767,
            "ddd1af56963093795705fa38da6ce7e6567d1658e7c7359db7e13fcd37dbf279"),
        ModelFile("decoder_model_quantized.onnx", 29_627_936,
            "2e7177d2b0a59f1c612b694ed70c13971bee765cc2b2bc7bc9376e4753652f27"),
    )
    private val dir = File(context.filesDir, "manga-ocr")

    val totalBytes: Long = files.sumOf { it.size }

    /** El modelo va dentro del APK (versión completa). */
    val inApk: Boolean
        get() = try {
            context.assets.openFd(ASSET_DIR + files[0].name).close()
            true
        } catch (e: IOException) {
            false
        }

    val isDownloaded: Boolean
        get() = files.all { File(dir, it.name).length() == it.size }

    val isAvailable: Boolean get() = inApk || isDownloaded

    fun load(): MangaOcr {
        val vocab = context.assets.open("manga_ocr_vocab.txt").bufferedReader().readLines()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        return if (inApk) {
            MangaOcr.fromBuffers(mapAsset(files[0].name), mapAsset(files[1].name), vocab, threads)
        } else {
            MangaOcr.fromFiles(File(dir, files[0].name).path, File(dir, files[1].name).path, vocab, threads)
        }
    }

    private fun mapAsset(name: String): MappedByteBuffer = context.assets.openFd(ASSET_DIR + name).use { fd ->
        fd.createInputStream().channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    /** Descarga los archivos que falten (versión ligera). [onProgress] recibe los bytes llevados. */
    fun download(onProgress: (Long) -> Unit) {
        dir.mkdirs()
        var done = 0L
        for (f in files) {
            val target = File(dir, f.name)
            if (target.length() == f.size) {
                done += f.size
                onProgress(done)
                continue
            }
            val part = File(dir, f.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            val conn = URL("$BASE_URL/${f.name}").openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            try {
                if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} al descargar ${f.name}")
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            onProgress(done)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != f.sha256) {
                part.delete()
                throw IOException("El archivo ${f.name} llegó dañado; vuelve a intentarlo.")
            }
            if (!part.renameTo(target)) throw IOException("No se pudo guardar ${f.name}")
        }
    }

    private companion object {
        const val ASSET_DIR = "manga_ocr/"
        const val BASE_URL =
            "https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/f9023406bb2f6b17df67bc4a327c56ecd20611f0/onnx"
    }
}
