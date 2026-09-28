package com.mangatraductor.core

import kotlin.test.Test
import kotlin.test.assertContentEquals

class ScreenPixelsTest {

    @Test
    fun convertsRowsWithPaddingAndShortLastRow() {
        // 2x2 píxeles, 4 bytes por píxel y 12 bytes por fila (4 de relleno);
        // la última fila viene sin relleno, como hacen algunos móviles.
        val bytes = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(), /**/ 0, 255.toByte(), 0, 255.toByte(), /* relleno */ 9, 9, 9, 9,
            0, 0, 255.toByte(), 0, /**/ 16, 32, 48, 255.toByte(),
        )
        val argb = ScreenPixels.rgbaToArgb(bytes, width = 2, height = 2, pixelStride = 4, rowStride = 12)
        assertContentEquals(
            intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF102030.toInt()),
            argb,
        )
    }
}
