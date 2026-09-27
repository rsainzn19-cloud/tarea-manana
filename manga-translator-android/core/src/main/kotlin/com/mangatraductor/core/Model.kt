package com.mangatraductor.core

import kotlin.math.max
import kotlin.math.min

/** Rectángulo en píxeles: [left, right) x [top, bottom). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width * height

    fun union(o: Box) = Box(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))

    fun expand(d: Int) = Box(left - d, top - d, right + d, bottom + d)

    fun clip(w: Int, h: Int) = Box(left.coerceIn(0, w), top.coerceIn(0, h), right.coerceIn(0, w), bottom.coerceIn(0, h))

    fun intersects(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/** Imagen en memoria como píxeles ARGB (el mismo formato que Bitmap.getPixels en Android). */
class PixelImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "tamaño de píxeles incorrecto" }
    }

    fun copy() = PixelImage(width, height, argb.copyOf())

    /** Luminancia 0..255 de cada píxel. */
    fun gray(): IntArray = IntArray(argb.size) { luma(argb[it]) }

    companion object {
        fun luma(c: Int): Int {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000
        }
    }
}

/** Un trozo de texto encontrado por el detector (ML Kit en el móvil). */
data class DetectedText(val box: Box, val text: String = "")

/** Un bloque de texto (normalmente un globo) con su lectura y traducción. */
class TextBlock(var box: Box, val parts: List<DetectedText> = emptyList()) {
    var text: String = ""
    var translation: String = ""

    /** Zona donde se escribe la traducción (el interior del globo). */
    var renderBox: Box? = null

    /** Máscara de los trazos del texto original, relativa a [maskRegion]. */
    var mask: BooleanArray? = null
    var maskRegion: Box? = null

    val isVertical: Boolean get() = box.height > box.width * 1.2

    /** Texto del detector unido en orden de lectura (por si no hay manga-ocr). */
    fun detectorText(): String {
        val ordered = if (isVertical) {
            parts.sortedWith(compareBy({ -it.box.right }, { it.box.top }))
        } else {
            parts.sortedWith(compareBy({ it.box.top }, { it.box.left }))
        }
        return ordered.joinToString("") { it.text.replace("\n", "") }
    }
}
