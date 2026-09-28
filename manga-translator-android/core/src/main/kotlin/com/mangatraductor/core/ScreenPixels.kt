package com.mangatraductor.core

/** Conversión de los fotogramas de captura de pantalla (RGBA_8888) a píxeles ARGB. */
object ScreenPixels {

    /**
     * [bytes] contiene las filas de la imagen, cada una de [rowStride] bytes
     * (puede haber relleno al final de cada fila) y cada píxel de [pixelStride]
     * bytes en orden R, G, B, A. La última fila puede venir sin relleno.
     */
    fun rgbaToArgb(bytes: ByteArray, width: Int, height: Int, pixelStride: Int, rowStride: Int): IntArray {
        require(pixelStride >= 4 && rowStride >= width * pixelStride) { "formato de captura no soportado" }
        require(bytes.size >= rowStride * (height - 1) + width * pixelStride) { "captura incompleta" }
        val out = IntArray(width * height)
        for (y in 0 until height) {
            var i = y * rowStride
            val row = y * width
            for (x in 0 until width) {
                val r = bytes[i].toInt() and 0xFF
                val g = bytes[i + 1].toInt() and 0xFF
                val b = bytes[i + 2].toInt() and 0xFF
                // La pantalla es opaca: se ignora el alfa del fotograma.
                out[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                i += pixelStride
            }
        }
        return out
    }
}
