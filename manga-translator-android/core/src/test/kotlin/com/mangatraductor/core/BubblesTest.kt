package com.mangatraductor.core

import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Globos partidos por el detector, zonas de escritura que no se pisan y cómics chinos. */
class BubblesTest {

    private val echo = object : Translator {
        override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) = texts.map { "T($it)" }
    }

    /** Página blanca con globos ovalados y "letras" (cuadrados negros) en columnas o en líneas. */
    private class Page(val width: Int, val height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        private val g = img.createGraphics().apply {
            color = Color.WHITE
            fillRect(0, 0, width, height)
        }

        fun bubble(cx: Int, cy: Int, rx: Int, ry: Int) {
            g.color = Color.BLACK
            g.stroke = BasicStroke(3f)
            g.drawOval(cx - rx, cy - ry, 2 * rx, 2 * ry)
        }

        /** Columna vertical de [chars] letras de 24 px; devuelve su caja (como la daría ML Kit). */
        fun column(x: Int, top: Int, chars: Int): Box {
            g.color = Color.BLACK
            for (i in 0 until chars) g.fillRect(x + 3, top + i * 30 + 3, 18, 22)
            return Box(x, top, x + 24, top + chars * 30)
        }

        /** Línea horizontal de [chars] letras. */
        fun line(left: Int, y: Int, chars: Int): Box {
            g.color = Color.BLACK
            for (i in 0 until chars) g.fillRect(left + i * 28 + 3, y + 3, 22, 20)
            return Box(left, y, left + chars * 28, y + 26)
        }

        fun pixels(): PixelImage = PixelImage(width, height, img.getRGB(0, 0, width, height, null, 0, width))
    }

    @Test
    fun twoPiecesOfOneBubbleAreReadAndWrittenAsOne() {
        val page = Page(600, 600)
        page.bubble(250, 250, 110, 140)
        // Dos columnas con un hueco grande entre ellas: el detector las da como dos textos.
        val right = page.column(290, 160, 6)
        val left = page.column(190, 190, 4)
        val detections = listOf(DetectedText(left, "左の列"), DetectedText(right, "右の列"))
        assertEquals(2, BlockMerger.merge(detections).size) // separadas para el agrupador...

        val image = page.pixels()
        val result = PageProcessor(null, echo).process(image, detections)
        // ...pero están en el mismo globo: una sola frase, leída de derecha a izquierda.
        assertEquals(listOf("右の列左の列"), result.blocks.map { it.text })
        assertEquals(listOf("T(右の列左の列)"), result.blocks.map { it.translation })

        // Se escribe dentro del globo (elipse 140..360 x 110..390), más ancho que las columnas.
        val r = result.blocks.single().renderBox!!
        assertTrue(r.left >= 140 && r.right <= 360 && r.top >= 110 && r.bottom <= 390, "fuera del globo: $r")
        assertTrue(r.width > 100, "zona de escritura estrecha: $r")
        SamplePage.save(result.cleaned, File("build/test-output/split-bubble.png"), listOf(r to Color.BLUE))
    }

    @Test
    fun twoTouchingBubblesKeepTheirOwnSentences() {
        val page = Page(700, 400)
        // Dos globos que se tocan: una sola zona blanca, pero cada texto es de un personaje.
        page.bubble(200, 200, 150, 120)
        page.bubble(470, 200, 150, 120)
        val g = page.img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(330, 150, 20, 100) // abre el paso entre los dos
        val a = page.column(190, 140, 4)
        val b = page.column(460, 140, 4)
        val result = PageProcessor(null, echo).process(page.pixels(), listOf(DetectedText(a, "あいうえ"), DetectedText(b, "かきくけ")))
        assertEquals(listOf("かきくけ", "あいうえ"), result.blocks.map { it.text }) // de derecha a izquierda
        val (r1, r2) = result.blocks.map { it.renderBox!! }
        assertTrue(!r1.intersects(r2), "las zonas se pisan: $r1 y $r2")
    }

    @Test
    fun neighbouringTextsNeverShareTheirWritingArea() {
        val page = Page(700, 400)
        // Textos sobre el dibujo (sin globo) muy juntos: antes cada uno se ensanchaba sobre el otro.
        val drawing = page.img.createGraphics()
        drawing.color = Color(150, 150, 150)
        drawing.fillRect(0, 0, 700, 400)
        val a = page.column(118, 80, 7)
        val b = page.column(218, 80, 7)
        val detections = listOf(DetectedText(a, "一二三四五六七"), DetectedText(b, "八九十百千万億"))
        val result = PageProcessor(null, echo).process(page.pixels(), detections)
        assertEquals(2, result.blocks.size)
        val (r1, r2) = result.blocks.map { it.renderBox!! }
        assertTrue(!r1.intersects(r2), "las zonas se pisan: $r1 y $r2")
    }

    @Test
    fun chineseIsReadLeftToRightAndLineByLine() {
        val page = Page(800, 400)
        page.bubble(200, 200, 150, 90)
        page.bubble(600, 200, 150, 90)
        // Burbuja izquierda: dos líneas; la segunda, partida en dos trozos por el detector.
        val l1 = page.line(110, 160, 6)
        val l2a = page.line(110, 200, 3)
        val l2b = page.line(200, 200, 3)
        val r1 = page.line(520, 185, 5)
        val detections = listOf(
            DetectedText(r1, "右边的话"),
            DetectedText(l2b, "三四"),
            DetectedText(l1, "第一行"),
            DetectedText(l2a, "一二"),
        )
        val result = PageProcessor(null, echo, SourceLanguage.CHINESE).process(page.pixels(), detections)
        assertEquals(listOf("第一行一二三四", "右边的话"), result.blocks.map { it.text })
    }

    @Test
    fun koreanKeepsSpacesAndOtherScriptsAreIgnored() {
        val page = Page(600, 300)
        page.bubble(300, 150, 200, 90)
        val a = page.line(160, 120, 5)
        val b = page.line(160, 160, 5)
        val detections = listOf(DetectedText(a, "안녕하세요"), DetectedText(b, "반가워요"))
        val result = PageProcessor(null, echo, SourceLanguage.KOREAN).process(page.pixels(), detections)
        assertEquals(listOf("안녕하세요 반가워요"), result.blocks.map { it.text })
        // Un texto japonés no se toma por coreano.
        val none = PageProcessor(null, echo, SourceLanguage.KOREAN)
            .process(page.pixels(), listOf(DetectedText(a, "こんにちは")))
        assertTrue(none.blocks.isEmpty())
    }

    @Test
    fun shortKanaLooseOnTheDrawingIsASoundEffect() {
        val page = Page(800, 600)
        page.bubble(150, 200, 110, 140)
        val bubbleText = page.column(140, 130, 5)
        val sfx = page.column(450, 100, 4) // sin globo
        val result = PageProcessor(null, echo).process(page.pixels(),
            listOf(DetectedText(bubbleText, "ドキドキ"), DetectedText(sfx, "ドキドキ")))
        assertEquals(listOf(TextKind.SFX, TextKind.DIALOGUE), result.blocks.map { it.kind }) // de derecha a izquierda
    }

    @Test
    fun anEmptyTranslationMeansNoiseAndIsNotDrawn() {
        val page = Page(500, 500)
        page.bubble(250, 250, 110, 140)
        val col = page.column(238, 180, 5)
        val noise = page.column(20, 20, 1) // "marca de agua" en una esquina
        val skipNoise = object : Translator {
            override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) =
                texts.map { if (it == "印") "" else "Hola" }
        }
        val result = PageProcessor(null, skipNoise)
            .process(page.pixels(), listOf(DetectedText(col, "こんにちは"), DetectedText(noise, "印")))
        assertEquals(listOf("Hola"), result.blocks.map { it.translation })
    }
}
