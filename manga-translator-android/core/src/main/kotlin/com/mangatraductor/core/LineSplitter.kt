package com.mangatraductor.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Parte una caja que en realidad son varias líneas pegadas (p. ej. un globo
 * estrecho con líneas horizontales de dos caracteres, que los detectores
 * toman por una sola). Se miran los huecos sin tinta entre filas y entre
 * columnas: en un texto chino, japonés o coreano, el espacio entre líneas es
 * mayor que el espacio entre caracteres, así que la dirección con los huecos
 * más grandes es la que separa las líneas.
 */
internal object LineSplitter {

    /** Las líneas dentro de [box], o null si parece una sola (o no está claro). */
    fun split(image: PixelImage, box: Box): List<Box>? {
        val b = box.clip(image.width, image.height)
        if (b.width < 8 || b.height < 8) return null
        // Tinta: lo que se aparta mucho del tono del fondo (la mediana del borde de la caja).
        val border = IntArray(2 * (b.width + b.height))
        var n = 0
        for (x in b.left until b.right) {
            border[n++] = PixelImage.luma(image.argb[b.top * image.width + x])
            border[n++] = PixelImage.luma(image.argb[(b.bottom - 1) * image.width + x])
        }
        for (y in b.top until b.bottom) {
            border[n++] = PixelImage.luma(image.argb[y * image.width + b.left])
            border[n++] = PixelImage.luma(image.argb[y * image.width + b.right - 1])
        }
        val bg = median255(border, n)
        val ink = BooleanArray(b.area) { p ->
            abs(PixelImage.luma(image.argb[(b.top + p / b.width) * image.width + b.left + p % b.width]) - bg) > INK
        }
        val rowInk = IntArray(b.height)
        val colInk = IntArray(b.width)
        for (p in ink.indices) if (ink[p]) {
            rowInk[p / b.width]++
            colInk[p % b.width]++
        }
        // Las rayas que cruzan la caja de lado a lado (el borde de la viñeta o del globo) no son
        // texto: no cuentan al buscar los huecos en la otra dirección.
        val borderCol = BooleanArray(b.width) { colInk[it] > b.height * 0.8 }
        val borderRow = BooleanArray(b.height) { rowInk[it] > b.width * 0.8 }
        val rows = IntArray(b.height)
        val cols = IntArray(b.width)
        for (p in ink.indices) if (ink[p]) {
            val x = p % b.width
            val y = p / b.width
            if (!borderCol[x]) rows[y]++
            if (!borderRow[y]) cols[x]++
        }
        // Un contorno de globo dentro de la caja pone algo de tinta en cada fila (o columna):
        // sólo cuentan las que tienen bastante.
        val (rowBands, rowGap) = lineBands(rows) ?: return null
        val (colBands, colGap) = lineBands(cols) ?: return null
        return when {
            rowGap > colGap * 1.3f + 1 -> rowBands.map { (s, e) ->
                val pad = min(4, rowGap / 2)
                trim(Box(b.left, b.top + s - pad, b.right, b.top + e + pad), image, bg)
            }
            colGap > rowGap * 1.3f + 1 -> colBands.map { (s, e) ->
                val pad = min(4, colGap / 2)
                trim(Box(b.left + s - pad, b.top, b.left + e + pad, b.bottom), image, bg)
            }
            else -> null
        }?.filter { it.width > 2 && it.height > 2 }?.takeIf { it.size >= 2 }
    }

    /**
     * Los tramos con tinta que pueden ser caracteres o líneas, y el hueco típico
     * entre ellos; null si no hay al menos dos (sólo una rejilla, varias filas y
     * varias columnas, puede ser varias líneas). Los tramos mucho más finos que
     * el resto (el contorno del globo, el punto de un «！») no cuentan: se pegan
     * al de al lado si están junto a él.
     */
    private fun lineBands(profile: IntArray): Pair<List<Pair<Int, Int>>, Int>? {
        // Un contorno de globo dentro de la caja pone algo de tinta en cada fila (o columna):
        // sólo cuentan las que tienen bastante.
        val all = bands(profile, max(1, (profile.max() * 0.08f).toInt()))
        if (all.size < 2) return null
        val sizes = all.map { it.second - it.first }.sorted()
        val typical = sizes[sizes.size / 2]
        val kept = all.filter { it.second - it.first >= typical * 0.35f }.toMutableList()
        if (kept.size < 2) return null
        val gaps = kept.zipWithNext { a, b -> b.first - a.second }.sorted()
        val gap = gaps[(gaps.size - 1) / 2]
        for (thin in all - kept.toSet()) {
            val i = kept.indices.minBy { k -> max(kept[k].first - thin.second, thin.first - kept[k].second) }
            val k = kept[i]
            if (max(k.first - thin.second, thin.first - k.second) <= gap) kept[i] = min(k.first, thin.first) to max(k.second, thin.second)
        }
        return kept to gap
    }

    /** Tramos seguidos con tinta (de al menos 3 px), como (inicio, fin). */
    private fun bands(profile: IntArray, minInk: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (i in 0..profile.size) {
            val on = i < profile.size && profile[i] >= minInk
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                if (i - start >= 3) out += start to i
                start = -1
            }
        }
        return out
    }

    /** La caja ajustada a su tinta (con 3 px de margen). */
    private fun trim(box: Box, image: PixelImage, bg: Int): Box {
        val b = box.clip(image.width, image.height)
        var l = b.right; var r = b.left; var t = b.bottom; var d = b.top
        for (y in b.top until b.bottom) for (x in b.left until b.right) {
            if (abs(PixelImage.luma(image.argb[y * image.width + x]) - bg) > INK) {
                l = min(l, x); r = max(r, x + 1); t = min(t, y); d = max(d, y + 1)
            }
        }
        if (r <= l || d <= t) return b
        return Box(l - 3, t - 3, r + 3, d + 3).clip(image.width, image.height)
    }

    /** Diferencia de tono para contar un píxel como tinta. */
    private const val INK = 80
}
