package com.mangatraductor.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Trozos (arriba, alto) en que se analizan las imágenes muy altas (tiras tipo webtoon). */
object Tiles {
    /**
     * Si la imagen es más de 2,5 veces más alta que ancha, trozos de 1,5 veces
     * el ancho, solapados un 20 % para que ningún texto quede cortado en todos.
     */
    fun vertical(width: Int, height: Int): List<Pair<Int, Int>> {
        if (height <= width * 5 / 2) return listOf(0 to height)
        val size = width * 3 / 2
        val step = size * 4 / 5
        val out = mutableListOf<Pair<Int, Int>>()
        var top = 0
        while (true) {
            if (top + size >= height) {
                out += max(0, height - size) to min(size, height)
                break
            }
            out += top to size
            top += step
        }
        return out
    }
}

/**
 * [crop] de la imagen escalado a [outW] x [outH], como RGB 0..1 en planos
 * (R, G, B), dentro de un lienzo de [canvas] x [canvasHeight] relleno de
 * negro (arriba a la izquierda). Al reducir se promedian los píxeles de cada zona.
 */
internal fun toChw(
    img: PixelImage, crop: Box, outW: Int, outH: Int,
    canvas: Int = max(outW, outH), canvasHeight: Int = canvas,
): FloatArray {
    val plane = canvas * canvasHeight
    val out = FloatArray(3 * plane)
    val fx = crop.width.toFloat() / outW
    val fy = crop.height.toFloat() / outH
    for (oy in 0 until outH) {
        val y0 = crop.top + floor(oy * fy).toInt()
        val y1 = max(y0 + 1, min(crop.bottom, crop.top + floor((oy + 1) * fy).toInt()))
        for (ox in 0 until outW) {
            val x0 = crop.left + floor(ox * fx).toInt()
            val x1 = max(x0 + 1, min(crop.right, crop.left + floor((ox + 1) * fx).toInt()))
            var r = 0; var g = 0; var b = 0; var n = 0
            for (y in y0 until min(y1, img.height)) for (x in x0 until min(x1, img.width)) {
                val c = img.argb[y * img.width + x]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
            }
            if (n == 0) continue
            val p = oy * canvas + ox
            out[p] = r / (255f * n)
            out[plane + p] = g / (255f * n)
            out[2 * plane + p] = b / (255f * n)
        }
    }
    return out
}

/** Valor de un plano [w] x [h] en (x, y) con interpolación bilineal (coordenadas de píxel). */
internal fun bilinear(src: FloatArray, offset: Int, w: Int, h: Int, x: Float, y: Float): Float {
    val cx = x.coerceIn(0f, (w - 1).toFloat())
    val cy = y.coerceIn(0f, (h - 1).toFloat())
    val x0 = cx.toInt()
    val y0 = cy.toInt()
    val x1 = min(x0 + 1, w - 1)
    val y1 = min(y0 + 1, h - 1)
    val ax = cx - x0
    val ay = cy - y0
    val top = src[offset + y0 * w + x0] * (1 - ax) + src[offset + y0 * w + x1] * ax
    val bottom = src[offset + y1 * w + x0] * (1 - ax) + src[offset + y1 * w + x1] * ax
    return top * (1 - ay) + bottom * ay
}
