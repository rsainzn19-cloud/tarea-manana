package com.mangatraductor.app

import android.content.Context
import com.mangatraductor.core.MangaOcr
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Descarga (una sola vez) y carga el modelo manga-ocr en formato ONNX.
 * Se fija una revisión concreta del repositorio y se comprueba el SHA-256
 * de cada archivo, así nunca se carga un archivo corrupto o distinto.
 */
class OcrModel(private val context: Context) {

    private class ModelFile(val name: String, val size: Long, val sha256: String)

    private val dir = File(context.filesDir, "manga-ocr")
    private val files = listOf(
        ModelFile("encoder_model_quantized.onnx", 86_967_767,
            "ddd1af56963093795705fa38da6ce7e6567d1658e7c7359db7e13fcd37dbf279"),
        ModelFile("decoder_model_quantized.onnx", 29_627_936,
            "2e7177d2b0a59f1c612b694ed70c13971bee765cc2b2bc7bc9376e4753652f27"),
    )

    val totalBytes: Long = files.sumOf { it.size }

    val isDownloaded: Boolean
        get() = files.all { File(dir, it.name).length() == it.size }

    /** Descarga los archivos que falten. [onProgress] recibe bytes descargados en total. */
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

    fun load(): MangaOcr {
        val vocab = context.assets.open("manga_ocr_vocab.txt").bufferedReader().readLines()
        return MangaOcr(
            File(dir, files[0].name).path,
            File(dir, files[1].name).path,
            vocab,
            threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
        )
    }

    companion object {
        private const val BASE_URL =
            "https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/f9023406bb2f6b17df67bc4a327c56ecd20611f0/onnx"
    }
}
