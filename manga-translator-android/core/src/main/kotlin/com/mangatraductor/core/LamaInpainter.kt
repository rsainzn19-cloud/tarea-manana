package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
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
 * dibujo bajo el texto en vez de emborronarlo. Trabaja en recortes de
 * 512x512 alrededor de cada texto (con algo de contexto alrededor).
 */
class LamaInpainter private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : Inpainter, Closeable {

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

        // Recorte con contexto: 512 px sin escalar si el texto es pequeño; si no, algo más grande que el texto.
        val side = max(SIZE, (max(holeBox.width, holeBox.height) * 1.6).toInt())
        val cw = min(side, img.width)
        val ch = min(side, img.height)
        val left = (holeBox.centerX - cw / 2).coerceIn(0, img.width - cw)
        val top = (holeBox.centerY - ch / 2).coerceIn(0, img.height - ch)
        val crop = Box(left, top, left + cw, top + ch)

        val image = toChw(img, crop, SIZE, SIZE, SIZE)
        val mask = FloatArray(SIZE * SIZE)
        val fx = cw.toFloat() / SIZE
        val fy = ch.toFloat() / SIZE
        for (oy in 0 until SIZE) {
            val y0 = crop.top + floor(oy * fy).toInt()
            val y1 = max(y0 + 1, crop.top + floor((oy + 1) * fy).toInt())
            for (ox in 0 until SIZE) {
                val x0 = crop.left + floor(ox * fx).toInt()
                val x1 = max(x0 + 1, crop.left + floor((ox + 1) * fx).toInt())
                var any = false
                for (y in y0 until y1) {
                    if (y < region.top || y >= region.bottom) continue
                    for (x in x0 until x1) {
                        if (x >= region.left && x < region.right && grown[(y - region.top) * rw + (x - region.left)]) any = true
                    }
                }
                if (any) mask[oy * SIZE + ox] = 1f
            }
        }

        val shape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
        val result = OnnxTensor.createTensor(env, FloatBuffer.wrap(image), shape).use { imageTensor ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(mask), longArrayOf(1, 1, SIZE.toLong(), SIZE.toLong())).use { maskTensor ->
                session.run(mapOf("image" to imageTensor, "mask" to maskTensor)).use {
                    (it.get(0) as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) }
                }
            }
        }

        // Sólo se sustituyen los píxeles del texto; el resto de la página queda igual.
        val plane = SIZE * SIZE
        for (p in grown.indices) if (grown[p]) {
            val x = region.left + p % rw
            val y = region.top + p / rw
            if (x < crop.left || x >= crop.right || y < crop.top || y >= crop.bottom) continue
            val sx = (x - crop.left + 0.5f) / fx - 0.5f
            val sy = (y - crop.top + 0.5f) / fy - 0.5f
            val r = (bilinear(result, 0, SIZE, SIZE, sx, sy) * 255).toInt().coerceIn(0, 255)
            val g = (bilinear(result, plane, SIZE, SIZE, sx, sy) * 255).toInt().coerceIn(0, 255)
            val b = (bilinear(result, 2 * plane, SIZE, SIZE, sx, sy) * 255).toInt().coerceIn(0, 255)
            img.argb[y * img.width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    override fun close() = session.close()

    companion object {
        private const val SIZE = 512

        fun load(model: File, threads: Int): LamaInpainter {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                addConfigEntry("session.set_denormal_as_zero", "1")
            }
            return LamaInpainter(env, env.createSession(model.path, options))
        }
    }
}
