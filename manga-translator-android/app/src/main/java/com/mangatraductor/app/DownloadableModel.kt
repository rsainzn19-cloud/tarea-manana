package com.mangatraductor.app

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Estado de la descarga de un modelo (manga-ocr en la versión ligera, Qwen). */
sealed interface DownloadState {
    data object Ready : DownloadState
    /** Falta y no se está descargando. */
    data object Missing : DownloadState
    data class Downloading(val percent: Int) : DownloadState
    /** En cola hasta que haya Wi-Fi (se puede forzar con datos móviles). */
    data object WaitingForWifi : DownloadState
    data object WaitingForNetwork : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Un archivo de un modelo: nombre en el móvil, ruta en el servidor, tamaño y SHA-256. */
class ModelFile(val name: String, val size: Long, val sha256: String, val remotePath: String = name)

/**
 * Un modelo que la app descarga la primera vez que hace falta (ver
 * [ModelDownloader]). Cada archivo se comprueba con su SHA-256 antes de usarlo.
 */
abstract class DownloadableModel(protected val context: Context) {

    /** Nombre de la carpeta (y de la descarga). */
    abstract val key: String

    abstract val files: List<ModelFile>

    /** Dirección de cada archivo en cada servidor, por orden de preferencia. */
    abstract val sources: List<(ModelFile) -> String>

    /** Título de la notificación de descarga. */
    abstract val title: String

    /** Preferencias donde se apunta la descarga en marcha. */
    open val prefsName: String get() = "descarga_$key"

    /** Otras carpetas donde puede estar ya (versiones anteriores de la app). */
    protected open val legacyDirs: List<File> = emptyList()

    /** Carpeta donde se descarga el modelo (almacenamiento propio de la app). */
    val downloadDir: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, key)

    val totalBytes: Long get() = files.sumOf { it.size }

    /** El archivo ya descargado y completo, o null si falta. */
    fun installed(f: ModelFile): File? =
        (listOf(downloadDir) + legacyDirs).map { File(it, f.name) }.firstOrNull { it.length() == f.size }

    val isDownloaded: Boolean get() = files.all { installed(it) != null }

    /** Lo que falta por bajar (para la tarjeta de descarga). */
    val missingBytes: Long get() = files.filter { installed(it) == null }.sumOf { it.size }

    /** Listo para usar (descargado, o incluido en el APK). */
    open val isAvailable: Boolean get() = isDownloaded

    fun tmpFile(f: ModelFile) = File(downloadDir, f.name + TMP_SUFFIX)

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

    /** Borra lo descargado (para liberar espacio). */
    fun delete() {
        downloadDir.deleteRecursively()
    }

    /**
     * Descarga sin el gestor de descargas de Android (por si no está disponible
     * en el móvil). Prueba cada servidor hasta que uno funcione.
     * [onProgress] recibe los bytes llevados.
     */
    fun downloadInProcess(onProgress: (Long) -> Unit) {
        downloadDir.mkdirs()
        var lastError: IOException? = null
        for (source in sources) {
            try {
                var done = 0L
                for (f in files) {
                    if (installed(f) != null) {
                        done += f.size
                        onProgress(done)
                        continue
                    }
                    val tmp = tmpFile(f)
                    val conn = URL(source(f)).openConnection() as HttpURLConnection
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
        throw lastError ?: IOException("No se pudo descargar $key")
    }

    companion object {
        const val TMP_SUFFIX = ".descarga"

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

        /** «117 MB», «3,1 GB». */
        fun sizeText(bytes: Long): String = if (bytes >= 1_000_000_000L) {
            String.format(Locale("es"), "%.1f GB", bytes / 1e9)
        } else {
            "${(bytes + 500_000) / 1_000_000} MB"
        }
    }
}
