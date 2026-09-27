package com.mangatraductor.core

import kotlin.math.max
import kotlin.math.min

/** Componentes conexas de una máscara binaria, con su caja envolvente. */
internal class Components(
    val labels: IntArray, // 0 = fondo, 1..count = componente
    val count: Int,
    val minX: IntArray,
    val minY: IntArray,
    val maxX: IntArray,
    val maxY: IntArray,
)

internal fun connectedComponents(mask: BooleanArray, w: Int, h: Int, eightConnected: Boolean): Components {
    val labels = IntArray(w * h)
    val stack = IntArray(w * h)
    var count = 0
    for (start in mask.indices) {
        if (!mask[start] || labels[start] != 0) continue
        count++
        labels[start] = count
        var top = 0
        stack[top++] = start
        while (top > 0) {
            val p = stack[--top]
            val x = p % w
            val y = p / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                if (!eightConnected && dx != 0 && dy != 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val q = ny * w + nx
                if (mask[q] && labels[q] == 0) {
                    labels[q] = count
                    stack[top++] = q
                }
            }
        }
    }
    val minX = IntArray(count + 1) { Int.MAX_VALUE }
    val minY = IntArray(count + 1) { Int.MAX_VALUE }
    val maxX = IntArray(count + 1) { -1 }
    val maxY = IntArray(count + 1) { -1 }
    for (p in labels.indices) {
        val l = labels[p]
        if (l == 0) continue
        val x = p % w
        val y = p / w
        minX[l] = min(minX[l], x); maxX[l] = max(maxX[l], x)
        minY[l] = min(minY[l], y); maxY[l] = max(maxY[l], y)
    }
    return Components(labels, count, minX, minY, maxX, maxY)
}

/** Dilatación con un cuadrado de lado 2*radius+1 (separable, con sumas acumuladas). */
internal fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
    if (radius <= 0) return mask.copyOf()
    val horizontal = BooleanArray(w * h)
    val prefix = IntArray(max(w, h) + 1)
    for (y in 0 until h) {
        for (x in 0 until w) prefix[x + 1] = prefix[x] + if (mask[y * w + x]) 1 else 0
        for (x in 0 until w) {
            horizontal[y * w + x] = prefix[min(w, x + radius + 1)] - prefix[max(0, x - radius)] > 0
        }
    }
    val out = BooleanArray(w * h)
    for (x in 0 until w) {
        for (y in 0 until h) prefix[y + 1] = prefix[y] + if (horizontal[y * w + x]) 1 else 0
        for (y in 0 until h) {
            out[y * w + x] = prefix[min(h, y + radius + 1)] - prefix[max(0, y - radius)] > 0
        }
    }
    return out
}

internal fun erode(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
    val inverted = BooleanArray(mask.size) { !mask[it] }
    val grown = dilate(inverted, w, h, radius)
    return BooleanArray(mask.size) { !grown[it] }
}

/** Mediana de valores 0..255 usando un histograma. */
internal fun median255(values: IntArray, count: Int = values.size): Int {
    if (count == 0) return 0
    val hist = IntArray(256)
    for (i in 0 until count) hist[values[i].coerceIn(0, 255)]++
    var acc = 0
    for (v in 0..255) {
        acc += hist[v]
        if (acc * 2 >= count) return v
    }
    return 255
}

/**
 * Rellena los píxeles marcados en [hole] (dentro de [region]) desde el borde
 * hacia dentro, con la media de los vecinos ya conocidos ("pelado de cebolla").
 * Es un inpainting sencillo pero suficiente para tapar letras sobre tramas.
 */
internal fun inpaint(img: PixelImage, region: Box, hole: BooleanArray) {
    val rw = region.width
    val rh = region.height
    val known = BooleanArray(hole.size) { !hole[it] }
    var remaining = hole.count { it }
    val updates = IntArray(hole.size)
    val updateColor = IntArray(hole.size)

    while (remaining > 0) {
        var n = 0
        for (p in hole.indices) {
            if (known[p]) continue
            val x = p % rw
            val y = p / rw
            var r = 0; var g = 0; var b = 0; var k = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= rw || ny >= rh) continue
                val q = ny * rw + nx
                if (!known[q]) continue
                val c = img.argb[(region.top + ny) * img.width + region.left + nx]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; k++
            }
            if (k > 0) {
                updates[n] = p
                updateColor[n] = (0xFF shl 24) or ((r / k) shl 16) or ((g / k) shl 8) or (b / k)
                n++
            }
        }
        if (n == 0) break // región aislada sin vecinos conocidos
        for (i in 0 until n) {
            val p = updates[i]
            known[p] = true
            img.argb[(region.top + p / rw) * img.width + region.left + p % rw] = updateColor[i]
        }
        remaining -= n
    }
}
