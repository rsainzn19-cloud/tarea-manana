package com.mangatraductor.app

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.mangatraductor.core.ScreenPixels

/**
 * Captura de pantalla continua con MediaProjection: una pantalla virtual que
 * refleja la real y un ImageReader del que siempre se guarda el último
 * fotograma, para poder "hacer la foto" al instante cuando se toca el botón.
 *
 * (Desde Android 14 cada permiso de captura sólo permite crear una pantalla
 * virtual, por eso se crea una vez y se reutiliza, cambiándole el tamaño si se
 * gira el móvil.)
 */
class ScreenCapture(projection: MediaProjection, width: Int, height: Int, dpi: Int) : AutoCloseable {

    private val thread = HandlerThread("captura-pantalla").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Object()
    private var latest: Image? = null
    private var frames = 0L

    var width = width
        private set
    var height = height
        private set

    private var reader = newReader(width, height)
    private val display: VirtualDisplay = projection.createVirtualDisplay(
        "MangaTraductor", width, height, dpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface, null, handler,
    ) ?: throw IllegalStateException("No se pudo iniciar la captura de pantalla.")

    /** Número de fotogramas recibidos hasta ahora. */
    val frameCount: Long get() = synchronized(lock) { frames }

    private fun newReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3).apply {
            setOnImageAvailableListener({ r ->
                val image = try {
                    r.acquireLatestImage()
                } catch (e: IllegalStateException) {
                    null
                } ?: return@setOnImageAvailableListener
                synchronized(lock) {
                    latest?.close()
                    latest = image
                    frames++
                    lock.notifyAll()
                }
            }, handler)
        }

    /**
     * Espera hasta [timeoutMs] a que llegue un fotograma posterior al número
     * [after] y devuelve el más reciente como Bitmap. Si la pantalla no cambia
     * no llegan fotogramas nuevos: entonces se usa el último que haya.
     * No llamar desde el hilo principal.
     */
    fun grab(after: Long, timeoutMs: Long): Bitmap? = synchronized(lock) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (frames <= after) {
            val wait = deadline - SystemClock.uptimeMillis()
            if (wait <= 0) break
            lock.wait(wait)
        }
        latest?.toBitmap()
    }

    /** Ajusta la captura al nuevo tamaño de pantalla (por ejemplo al girar el móvil). */
    fun resize(w: Int, h: Int, dpi: Int) {
        if (w == width && h == height) return
        val old = reader
        reader = newReader(w, h)
        display.resize(w, h, dpi)
        display.surface = reader.surface
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        old.close()
        width = w
        height = h
    }

    override fun close() {
        display.release()
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        reader.close()
        thread.quitSafely()
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val argb = ScreenPixels.rgbaToArgb(bytes, width, height, plane.pixelStride, plane.rowStride)
        return Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
    }
}
