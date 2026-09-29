package com.mangatraductor.app

import android.app.DownloadManager
import android.content.Context
import androidx.core.content.edit

/**
 * Descarga un modelo (manga-ocr en la versión ligera, Qwen) con el gestor de
 * descargas de Android: sigue aunque se cierre la app, se reanuda si se corta
 * la conexión, puede esperar al Wi-Fi y enseña el progreso en la barra de
 * notificaciones. Si un servidor falla se prueba el siguiente.
 */
class ModelDownloader(private val context: Context, private val model: DownloadableModel) {

    private val manager: DownloadManager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences(model.prefsName, Context.MODE_PRIVATE)

    /** Hay una descarga en marcha (o esperando al Wi-Fi). */
    val isActive: Boolean get() = ids().isNotEmpty()

    /**
     * Empieza (o reinicia) la descarga desde el servidor número [source].
     * Con [allowMetered] = false espera a tener Wi-Fi.
     */
    fun start(allowMetered: Boolean, source: Int = 0) {
        cancel()
        model.downloadDir.mkdirs()
        val ids = model.files.filter { model.installed(it) == null }.map { f ->
            val tmp = model.tmpFile(f)
            tmp.delete()
            val request = DownloadManager.Request(android.net.Uri.parse(model.sources[source](f)))
                .setTitle(model.title)
                .setDescription(f.name)
                .setDestinationInExternalFilesDir(context, null, "${model.key}/${tmp.name}")
                .setAllowedOverMetered(allowMetered)
                .setAllowedOverRoaming(allowMetered)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            manager.enqueue(request)
        }
        prefs.edit {
            putString(KEY_IDS, ids.joinToString(","))
            putInt(KEY_SOURCE, source)
            putBoolean(KEY_METERED, allowMetered)
        }
    }

    /** Anula la descarga en marcha (y borra lo descargado a medias). */
    fun cancel() {
        val ids = ids()
        if (ids.isNotEmpty()) manager.remove(*ids.toLongArray())
        prefs.edit { remove(KEY_IDS) }
    }

    /**
     * Consulta cómo va la descarga. Si ya terminó, comprueba los archivos y deja
     * el modelo listo; si falló, prueba el siguiente servidor.
     */
    fun poll(): DownloadState {
        val ids = ids()
        if (ids.isEmpty()) return if (model.isAvailable) DownloadState.Ready else DownloadState.Missing

        val rows = mutableListOf<DownloadRow>()
        manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray())).use { c ->
            while (c.moveToNext()) {
                rows += DownloadRow(
                    status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                )
            }
        }
        val alreadyInstalled = model.files.filter { model.installed(it) != null }.sumOf { it.size }
        val summary = summarize(rows, expected = ids.size, doneBytes = alreadyInstalled, totalBytes = model.totalBytes)

        return when (summary) {
            is DownloadState.Ready -> finish()
            is DownloadState.Failed -> nextSourceOr(summary)
            else -> summary
        }
    }

    /** Todas las descargas terminaron: comprobar los archivos y dejarlos como definitivos. */
    private fun finish(): DownloadState {
        for (f in model.files) {
            if (model.installed(f) != null) continue
            val tmp = model.tmpFile(f)
            // No era de esta descarga (p. ej. se cambió de idioma mientras tanto): se bajará después.
            if (!tmp.exists()) continue
            if (!model.install(tmp, f)) {
                return nextSourceOr(DownloadState.Failed(context.getString(R.string.ocr_error_corrupt)))
            }
        }
        // Quitar las entradas de la lista de descargas (los archivos ya se movieron).
        cancel()
        return if (model.isAvailable) DownloadState.Ready else DownloadState.Missing
    }

    private fun nextSourceOr(failure: DownloadState.Failed): DownloadState {
        val source = prefs.getInt(KEY_SOURCE, 0)
        val metered = prefs.getBoolean(KEY_METERED, false)
        return if (source + 1 < model.sources.size) {
            start(metered, source + 1)
            DownloadState.Downloading(0)
        } else {
            cancel()
            failure
        }
    }

    private fun ids(): List<Long> =
        prefs.getString(KEY_IDS, null).orEmpty().split(",").mapNotNull { it.toLongOrNull() }

    /** Una fila de la consulta al gestor de descargas. */
    data class DownloadRow(val status: Int, val reason: Int, val bytes: Long)

    companion object {
        private const val KEY_IDS = "ids"
        private const val KEY_SOURCE = "servidor"
        private const val KEY_METERED = "con_datos"

        /**
         * Resume el estado de varias descargas en uno solo. [expected] es cuántas
         * se encolaron: si falta alguna fila, el usuario la canceló desde la
         * notificación. [doneBytes] son los de archivos ya instalados.
         */
        fun summarize(rows: List<DownloadRow>, expected: Int, doneBytes: Long, totalBytes: Long): DownloadState {
            if (rows.size < expected) return DownloadState.Failed("la descarga se canceló")
            rows.firstOrNull { it.status == DownloadManager.STATUS_FAILED }?.let { return DownloadState.Failed(reasonText(it.reason, totalBytes)) }
            if (rows.all { it.status == DownloadManager.STATUS_SUCCESSFUL }) return DownloadState.Ready
            val paused = rows.filter { it.status == DownloadManager.STATUS_PAUSED }
            if (paused.any { it.reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI }) return DownloadState.WaitingForWifi
            if (paused.any { it.reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK }) return DownloadState.WaitingForNetwork
            val bytes = doneBytes + rows.sumOf { it.bytes.coerceAtLeast(0) }
            return DownloadState.Downloading((bytes * 100 / totalBytes).toInt().coerceIn(0, 99))
        }

        private fun reasonText(reason: Int, totalBytes: Long): String = when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                "no hay espacio suficiente (hacen falta unos ${DownloadableModel.sizeText(totalBytes + totalBytes / 20)} libres)"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "no se encontró el almacenamiento"
            DownloadManager.ERROR_FILE_ERROR -> "no se pudo guardar el archivo"
            DownloadManager.ERROR_HTTP_DATA_ERROR,
            DownloadManager.ERROR_CANNOT_RESUME,
            DownloadManager.ERROR_TOO_MANY_REDIRECTS,
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "falló la conexión con el servidor"
            in 400..599 -> "el servidor respondió con el error HTTP $reason"
            else -> "error de descarga ($reason)"
        }
    }
}
