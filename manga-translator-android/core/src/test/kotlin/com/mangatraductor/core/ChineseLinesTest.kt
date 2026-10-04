package com.mangatraductor.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chino (y coreano): líneas que se pegan en globos estrechos, la fuente de
 * líneas que mejor se lee en cada globo, el orden de lectura y los globos
 * abiertos. "Caracteres" = cuadrados negros de 30 px.
 */
class ChineseLinesTest {

    private class Page(width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, width, height)
            color = Color.BLACK
        }

        /** Caracteres de 30 px: [rows] filas de [cols], con [charGap] entre caracteres y [lineGap] entre filas. */
        fun grid(left: Int, top: Int, rows: Int, cols: Int, charGap: Int, lineGap: Int) {
            for (r in 0 until rows) for (c in 0 until cols) g.fillRect(left + c * (30 + charGap), top + r * (30 + lineGap), 30, 30)
        }

        fun pixels() = PixelImage(img.width, img.height, img.getRGB(0, 0, img.width, img.height, null, 0, img.width))
    }

    @Test
    fun shortLinesSqueezedTogetherAreSplitIntoLines() {
        // Tres líneas horizontales de dos caracteres (como "牛田 / 先生 / ！！"): más hueco entre líneas que entre caracteres.
        val page = Page(200, 200)
        page.grid(40, 30, rows = 3, cols = 2, charGap = 3, lineGap = 12)
        val parts = assertNotNull(LineSplitter.split(page.pixels(), Box(30, 20, 120, 150)))
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.width > it.height }, "$parts")
        assertEquals(parts.sortedBy { it.top }, parts)
    }

    @Test
    fun columnsSqueezedTogetherAreSplitIntoColumns() {
        val page = Page(200, 200)
        page.grid(40, 30, rows = 3, cols = 2, charGap = 14, lineGap = 3)
        val parts = assertNotNull(LineSplitter.split(page.pixels(), Box(30, 20, 130, 140)))
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.height > it.width }, "$parts")
    }

    @Test
    fun singleLinesAndBubbleOutlinesAreLeftAlone() {
        val page = Page(300, 200)
        page.grid(40, 40, rows = 1, cols = 5, charGap = 3, lineGap = 0)
        assertNull(LineSplitter.split(page.pixels(), Box(30, 30, 220, 80)))
        // Dos líneas con el borde de la viñeta cruzando la caja: el borde no tapa el hueco entre líneas.
        val framed = Page(200, 200)
        framed.grid(40, 30, rows = 2, cols = 2, charGap = 3, lineGap = 12)
        framed.g.stroke = BasicStroke(4f)
        framed.g.drawLine(110, 0, 110, 200)
        val parts = assertNotNull(LineSplitter.split(framed.pixels(), Box(30, 20, 116, 120)))
        assertEquals(2, parts.size)
    }

    /** Lector de prueba: lo que "lee" en cada caja, con su seguridad. */
    private fun reader(readings: Map<Box, LineReading>) = LineReader { _, box -> readings[box] }

    @Test
    fun eachBubbleUsesTheLinesThatReadBest() {
        val image = Page(600, 400).pixels()
        val bubble = Box(100, 100, 300, 300)
        // El detector de manga junta tres líneas en una (se lee mal); el de PaddleOCR las separa.
        val merged = Box(120, 120, 280, 280)
        val lines = listOf(Box(120, 120, 280, 170), Box(120, 175, 280, 225), Box(120, 230, 280, 280))
        val far = Box(400, 100, 500, 140)
        val readings = mapOf(
            merged to LineReading("乱", 0.4f),
            lines[0] to LineReading("你好", 0.99f), lines[1] to LineReading("朋友", 0.98f), lines[2] to LineReading("！！", 0.9f),
            far to LineReading("再见", 0.95f),
        )
        val layout = TextLayout(
            listOf(TextLayout.ScoredBox(bubble, 0.9f)), 600, 400, ByteArray(600 * 400),
            lines = listOf(TextLayout.ScoredBox(merged, 0.8f)),
        )
        // ML Kit vio la línea de fuera del globo (con su texto) y algo que no se puede leer.
        val noise = Box(400, 300, 420, 320)
        val mlKit = listOf(DetectedText(far, "再見"), DetectedText(noise, ""))
        val found = PageProcessor.readLines(image, reader(readings), mlKit, layout, lines + far)
        assertEquals(setOf("你好", "朋友", "！！", "再见"), found.map { it.text }.toSet())
        assertEquals(4, found.size) // ni la línea juntada, ni la de fuera dos veces, ni el ruido
    }

    @Test
    fun whenPaddleIsUnsureTheMlKitTextStays() {
        val image = Page(300, 200).pixels()
        val line = Box(20, 20, 200, 60)
        val found = PageProcessor.readLines(
            image, reader(mapOf(line to LineReading("口口", 0.3f))), listOf(DetectedText(line, "你好")), null, emptyList(),
        )
        assertEquals(listOf("你好"), found.map { it.text })
    }

    @Test
    fun verticalChineseIsReadFromRightToLeft() {
        val echo = object : Translator {
            override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) = texts
        }
        val page = Page(600, 400)
        page.grid(100, 100, rows = 4, cols = 1, charGap = 0, lineGap = 4)
        page.grid(450, 100, rows = 4, cols = 1, charGap = 0, lineGap = 4)
        val left = DetectedText(Box(98, 98, 132, 236), "左边的话")
        val right = DetectedText(Box(448, 98, 482, 236), "右边的话")
        // Texto vertical: de derecha a izquierda aunque el chino se lea, por defecto, al revés.
        val vertical = PageProcessor(null, echo, SourceLanguage.CHINESE).prepare(page.pixels(), listOf(left, right))
        assertEquals(listOf("右边的话", "左边的话"), vertical.texts)
        vertical.cancel()
        // En horizontal, según el ajuste: izquierda a derecha (manhua) o derecha a izquierda (manga).
        val flat = Page(600, 400)
        flat.grid(100, 100, rows = 1, cols = 4, charGap = 4, lineGap = 0)
        flat.grid(400, 100, rows = 1, cols = 4, charGap = 4, lineGap = 0)
        val a = DetectedText(Box(98, 98, 236, 132), "左边的话")
        val b = DetectedText(Box(398, 98, 536, 132), "右边的话")
        val manhua = PageProcessor(null, echo, SourceLanguage.CHINESE).prepare(flat.pixels(), listOf(a, b))
        assertEquals(listOf("左边的话", "右边的话"), manhua.texts)
        manhua.cancel()
        val manga = PageProcessor(null, echo, SourceLanguage.CHINESE, rightToLeft = true).prepare(flat.pixels(), listOf(a, b))
        assertEquals(listOf("右边的话", "左边的话"), manga.texts)
        manga.cancel()
    }

    @Test
    fun aBubbleCutByThePanelBorderStillGivesRoomToWrite() {
        val echo = object : Translator {
            override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) = texts.map { "T" }
        }
        // Globo blanco que se sale de la viñeta (abierto por la derecha, hacia el blanco del
        // margen): no es un globo cerrado.
        val page = Page(600, 500)
        page.g.stroke = BasicStroke(3f)
        page.g.drawArc(150, 100, 300, 300, 60, 240)
        page.grid(270, 190, rows = 4, cols = 1, charGap = 0, lineGap = 6)
        val text = DetectedText(Box(268, 188, 302, 330), "说话")
        val result = PageProcessor(null, echo, SourceLanguage.CHINESE).process(page.pixels(), listOf(text))
        val block = result.blocks.single()
        assertTrue(!block.inBubble)
        val area = block.renderOptions.first()
        // Más sitio que el hueco del texto original (34 px de ancho), sin pasar del contorno del globo.
        assertTrue(area.width > 120, "$area")
        assertTrue(area.left >= 150, "$area")
    }
}
