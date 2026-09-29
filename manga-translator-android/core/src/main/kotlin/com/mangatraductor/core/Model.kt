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

    /** Área en común con [o] (0 si no se tocan). */
    fun overlapArea(o: Box): Int =
        max(0, min(right, o.right) - max(left, o.left)) * max(0, min(bottom, o.bottom) - max(top, o.top))

    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
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

    /** Diálogo, narración u onomatopeya (se rotula distinto). */
    var kind: TextKind = TextKind.DIALOGUE

    /** El texto está en un globo cerrado (si no, va suelto sobre el dibujo). */
    var inBubble: Boolean = false

    /** Forma del interior del globo (para que las líneas la sigan), si está en uno. */
    var shape: BubbleShape? = null

    /**
     * Zonas donde se puede escribir la traducción (rectángulos dentro del
     * globo, de distintas formas); la primera es la principal.
     */
    var renderOptions: List<Box> = emptyList()

    /** Zona principal donde se escribe la traducción (el interior del globo). */
    val renderBox: Box? get() = renderOptions.firstOrNull()

    /** Máscara de los trazos del texto original, relativa a [maskRegion]. */
    var mask: BooleanArray? = null
    var maskRegion: Box? = null

    /**
     * Texto en columnas verticales (japonés o chino tradicional). Se decide por
     * los trozos del detector: la caja de un globo con varias columnas puede
     * ser más ancha que alta aunque el texto sea vertical.
     */
    val isVertical: Boolean
        get() {
            val shaped = parts.filter { it.box.height > it.box.width * 1.2 || it.box.width > it.box.height * 1.2 }
            if (shaped.isEmpty()) return box.height > box.width * 1.2
            return shaped.count { it.box.height > it.box.width } * 2 > shaped.size
        }

    /**
     * Texto del detector unido en orden de lectura (cuando no hay manga-ocr, o
     * en chino y coreano). [textOf] puede volver a leer cada línea (PaddleOCR). Las columnas verticales se leen de derecha a
     * izquierda; las líneas horizontales, de arriba abajo y cada una de
     * izquierda a derecha (aunque el detector la haya partido en trozos).
     */
    fun detectorText(textOf: (DetectedText) -> String = { it.text }): String {
        val ordered = if (isVertical) {
            parts.sortedWith(compareBy({ -it.box.right }, { it.box.top }))
        } else {
            val lines = mutableListOf<MutableList<DetectedText>>()
            for (part in parts.sortedBy { it.box.centerY }) {
                val line = lines.lastOrNull()?.takeIf { line ->
                    val top = line.minOf { it.box.top }
                    val bottom = line.maxOf { it.box.bottom }
                    min(bottom, part.box.bottom) - max(top, part.box.top) > 0.5 * min(part.box.height, bottom - top)
                }
                if (line != null) line += part else lines += mutableListOf(part)
            }
            lines.flatMap { line -> line.sortedBy { it.box.left } }
        }
        val texts = ordered.map(textOf)
        val separator = if (texts.any { SPACED.containsMatchIn(it) }) " " else ""
        return texts.joinToString(separator) { it.replace("\n", separator) }.trim()
    }

    private companion object {
        /** Escrituras que separan las palabras con espacios (coreano, letras latinas). */
        val SPACED = Regex("[\\uac00-\\ud7afA-Za-z]")
    }
}

/**
 * Interior de un globo fila a fila: para cada fila desde [top], de qué x a
 * qué x (incluida) llega el globo pasando por el centro del texto.
 */
class BubbleShape(val top: Int, val left: IntArray, val right: IntArray) {
    val bottom: Int get() = top + left.size

    /** Tramo libre común a las filas [y0, y1): (izquierda, derecha), o null si se sale del globo. */
    fun span(y0: Int, y1: Int): Pair<Int, Int>? {
        if (y0 < top || y1 > bottom || y1 <= y0) return null
        var l = Int.MIN_VALUE
        var r = Int.MAX_VALUE
        for (y in y0 until y1) {
            l = max(l, left[y - top]); r = min(r, right[y - top])
        }
        return if (r > l) l to r else null
    }
}
