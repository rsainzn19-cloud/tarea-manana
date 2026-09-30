package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Borrado con LaMa (AnimeMangaInpainting, la versión para manga que usan
 * manga-image-translator y Koharu) en ONNX: reconstruye tramas, líneas y
 * dibujo bajo el texto en vez de emborronarlo. Trabaja en un recorte
 * alrededor de cada texto, con contexto: de 256 x 256 para un texto pequeño
 * (6 veces más rápido) a 512 x 512 (los textos grandes se reducen).
 */
class LamaInpainter private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    /** Cómo se está ejecutando (CPU o XNNPACK). */
    val acceleration: Acceleration,
    /** El modelo acepta recortes de cualquier tamaño (si no, siempre 512). */
    private val flexible: Boolean,
) : Inpainter, Closeable {

    /** Una pasada de prueba (para medir y preparar la memoria). */
    fun warmUp() {
        val img = PixelImage(MAX_SIZE, MAX_SIZE, IntArray(MAX_SIZE * MAX_SIZE) { -1 })
        val hole = BooleanArray(64 * 64) { it % 64 in 20..40 }
        inpaint(img, Box(200, 200, 264, 264), hole)
    }

    override fun inpaint(img: PixelImage, region: Box, hole: BooleanArray) {
        val rw = region.width
        val rh = region.height
        // Un poco más de margen: sin él quedan los bordes suavizados de las letras.
        val grown = dilate(hole, rw, rh, 3)
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (p in grown.indices) if (grown[p]) {
            minX = min(minX, p % rw); maxX = max(maxX, p % rw)
            minY = min(minY, p / rw); maxY = max(maxY, p / rw)
        }
        if (maxX < 0) return
        val holeBox = Box(region.left + minX, region.top + minY, region.left + maxX + 1, region.top + maxY + 1)

        // Recorte con contexto alrededor del texto (un 30 % de su lado mayor, al menos 64 px):
        // sin escalar si cabe en 512 px, y si no, reducido. Con el tamaño libre, el recorte
        // es tan alto y ancho como haga falta (de 64 en 64, al menos 256): un texto alto y
        // estrecho no gasta un cuadrado de 512.
        val context = max(64, (max(holeBox.width, holeBox.height) * 0.3).toInt())
        val wantW = holeBox.width + 2 * context
        val wantH = holeBox.height + 2 * context
        val scale = min(1f, MAX_SIZE.toFloat() / max(wantW, wantH))
        fun fit(v: Int) = ((max(MIN_SIZE, (v * scale).toInt()) + 63) / 64 * 64).coerceAtMost(MAX_SIZE)
        val mw = if (flexible) fit(wantW) else MAX_SIZE
        val mh = if (flexible) fit(wantH) else MAX_SIZE
        val cw = min((mw / scale).toInt(), img.width)
        val ch = min((mh / scale).toInt(), img.height)
        val left = (holeBox.centerX - cw / 2).coerceIn(0, img.width - cw)
        val top = (holeBox.centerY - ch / 2).coerceIn(0, img.height - ch)
        val crop = Box(left, top, left + cw, top + ch)

        val image = toChw(img, crop, mw, mh, mw, mh)
        val mask = FloatArray(mw * mh)
        val fx = cw.toFloat() / mw
        val fy = ch.toFloat() / mh
        for (oy in 0 until mh) {
            val y0 = crop.top + floor(oy * fy).toInt()
            val y1 = max(y0 + 1, crop.top + floor((oy + 1) * fy).toInt())
            for (ox in 0 until mw) {
                val x0 = crop.left + floor(ox * fx).toInt()
                val x1 = max(x0 + 1, crop.left + floor((ox + 1) * fx).toInt())
                var any = false
                for (y in y0 until y1) {
                    if (y < region.top || y >= region.bottom) continue
                    for (x in x0 until x1) {
                        if (x >= region.left && x < region.right && grown[(y - region.top) * rw + (x - region.left)]) any = true
                    }
                }
                if (any) mask[oy * mw + ox] = 1f
            }
        }

        val result = try {
            run(image, mask, mw, mh)
        } catch (e: OrtException) {
            // Si este móvil no puede con un tamaño (p. ej. con XNNPACK), el borrado sencillo.
            SimpleInpainter.inpaint(img, region, hole)
            return
        }

        // Sólo se sustituyen los píxeles del texto; el resto de la página queda igual.
        val plane = mw * mh
        for (p in grown.indices) if (grown[p]) {
            val x = region.left + p % rw
            val y = region.top + p / rw
            if (x < crop.left || x >= crop.right || y < crop.top || y >= crop.bottom) continue
            val sx = (x - crop.left + 0.5f) / fx - 0.5f
            val sy = (y - crop.top + 0.5f) / fy - 0.5f
            val r = (bilinear(result, 0, mw, mh, sx, sy) * 255).toInt().coerceIn(0, 255)
            val g = (bilinear(result, plane, mw, mh, sx, sy) * 255).toInt().coerceIn(0, 255)
            val b = (bilinear(result, 2 * plane, mw, mh, sx, sy) * 255).toInt().coerceIn(0, 255)
            img.argb[y * img.width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun run(image: FloatArray, mask: FloatArray, width: Int, height: Int): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(image), longArrayOf(1, 3, height.toLong(), width.toLong())).use { imageTensor ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(mask), longArrayOf(1, 1, height.toLong(), width.toLong())).use { maskTensor ->
                session.run(mapOf("image" to imageTensor, "mask" to maskTensor)).use {
                    (it.get(0) as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) }
                }
            }
        }

    override fun close() = session.close()

    companion object {
        private const val MAX_SIZE = 512
        private const val MIN_SIZE = 256

        fun load(model: File, threads: Int, acceleration: Acceleration = Acceleration.CPU): LamaInpainter {
            // El modelo viene exportado para 512 x 512: se libera el tamaño en memoria.
            val (buffer, freed) = OnnxPatcher.mapWithFreeImageSize(model)
            val (session, used) = Sessions.open(buffer, threads, acceleration)
            return LamaInpainter(OrtEnvironment.getEnvironment(), session, used, flexible = freed == 4)
        }

        /** Prueba la CPU y XNNPACK en este móvil y se queda con la más rápida. */
        fun loadFastest(model: File, threads: Int): LamaInpainter =
            Sessions.fastest({ load(model, threads, it) }, { it.acceleration }, { it.warmUp() })
    }
}
