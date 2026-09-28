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
 * el APK. En la versión ligera se descarga una vez (ver [OcrDownloader]); cada
 * archivo se comprueba con su SHA-256 antes de usarlo.
 */
class OcrModel(private val context: Context) {

    class ModelFile(val name: String, val size: Long, val sha256: String)

    val files = listOf(
        ModelFile("encoder_model_quantized.onnx", 86_967_767,
            "ddd1af56963093795705fa38da6ce7e6567d1658e7c7359db7e13fcd37dbf279"),
        ModelFile("decoder_model_quantized.onnx", 29_627_936,
            "2e7177d2b0a59f1c612b694ed70c13971bee765cc2b2bc7bc9376e4753652f27"),
    )

    /** Carpeta donde se descarga el modelo (almacenamiento propio de la app). */
    val downloadDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "manga-ocr")
    private val legacyDir = File(context.filesDir, "manga-ocr") // versiones anteriores

    val totalBytes: Long = files.sumOf { it.size }

    /** El modelo va dentro del APK (versión completa). */
    val inApk: Boolean
        get() = try {
            context.assets.openFd(ASSET_DIR + files[0].name).close()
            true
        } catch (e: IOException) {
            false
        }

    /** El archivo ya descargado y completo, o null si falta. */
    fun installed(f: ModelFile): File? =
        listOf(downloadDir, legacyDir).map { File(it, f.name) }.firstOrNull { it.length() == f.size }

    val isDownloaded: Boolean get() = files.all { installed(it) != null }

    val isAvailable: Boolean get() = inApk || isDownloaded

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

    /**
     * Comprueba un archivo descargado ([tmp]) y, si es correcto, lo deja como
     * definitivo. Si está dañado lo borra y devuelve false.
     */
    fun install(tmp: File, f: ModelFile): Boolean {
        if (tmp.length() != f.size || sha256(tmp) != f.sha256) {
            tmp.delete()
            return false
        }
        val target = File(downloadDir, f.name)
        target.delete()
        if (tmp.renameTo(target)) return true
        tmp.copyTo(target, overwrite = true)
        tmp.delete()
        return true
    }

    /**
     * Descarga sin el gestor de descargas de Android (por si no está disponible
     * en el móvil). Prueba cada servidor de [SOURCES] hasta que uno funcione.
     * [onProgress] recibe los bytes llevados.
     */
    fun downloadInProcess(onProgress: (Long) -> Unit) {
        downloadDir.mkdirs()
        var lastError: IOException? = null
        for (source in SOURCES) {
            try {
                var done = 0L
                for (f in files) {
                    if (installed(f) != null) {
                        done += f.size
                        onProgress(done)
                        continue
                    }
                    val tmp = File(downloadDir, f.name + TMP_SUFFIX)
                    val conn = URL("$source/${f.name}").openConnection() as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    try {
                        if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} al descargar ${f.name}")
                        conn.inputStream.use { input ->
                            tmp.outputStream().use { output ->
                                val buffer = ByteArray(256 * 1024)
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    done += n
                                    onProgress(done)
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                    if (!install(tmp, f)) throw IOException("El archivo ${f.name} llegó dañado.")
                }
                return
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("No se pudo descargar manga-ocr")
    }

    companion object {
        private const val ASSET_DIR = "manga_ocr/"
        const val TMP_SUFFIX = ".descarga"

        /**
         * Servidores del modelo, en orden: la copia publicada en GitHub (junto a
         * la app) y el original en Hugging Face (revisión fija).
         */
        val SOURCES = listOf(
            "https://github.com/rsainzn19-cloud/tarea-manana/releases/download/manga-ocr-modelo",
            "https://huggingface.co/onnx-community/manga-ocr-base-ONNX/resolve/f9023406bb2f6b17df67bc4a327c56ecd20611f0/onnx",
        )

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
