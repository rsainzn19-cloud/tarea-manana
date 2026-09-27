package com.mangatraductor.core

import java.awt.BasicStroke
import java.awt.Color
import java.io.File
import javax.imageio.ImageIO

/**
 * La página de prueba de manga-translator/examples/sample_page.png (dibujo y
 * diálogos originales) y unas detecciones que imitan a ML Kit: una caja por
 * columna de texto vertical, calculadas con la misma geometría que usó
 * make_sample.py para escribir el texto.
 */
object SamplePage {
    private const val FONT = 30
    private const val COL_W = 37 // int(30 * 1.25)

    private val bubbles = listOf(
        Box(560, 70, 800, 330) to listOf("おはよう！", "今日はいい", "天気だね。"),
        Box(90, 90, 300, 330) to listOf("学校に", "遅れちゃうよ！"),
        Box(560, 520, 820, 800) to listOf("ちょっと", "待って、", "忘れ物した！"),
        Box(110, 900, 330, 1170) to listOf("お腹", "すいた…", "何か食べたいな。"),
    )

    val expectedTexts = listOf(
        "おはよう！今日はいい天気だね。",
        "学校に遅れちゃうよ！",
        "ちょっと待って、忘れ物した！",
        "その日の午後…",
        "お腹すいた…何か食べたいな。",
    )

    /** ¿Está (x, y) dentro de algún globo, lejos de su contorno? */
    fun insideBubble(x: Int, y: Int, margin: Int = 6): Boolean = bubbles.any { (b, _) ->
        val cx = (b.left + b.right) / 2.0
        val cy = (b.top + b.bottom) / 2.0
        val a = b.width / 2.0 - margin
        val c = b.height / 2.0 - margin
        ((x - cx) / a) * ((x - cx) / a) + ((y - cy) / c) * ((y - cy) / c) < 1
    }

    /** ¿Está (x, y) en el cartel de narración, lejos de su borde? */
    fun insideNarration(x: Int, y: Int): Boolean = x in 506..814 && y in 886..934

    fun detections(): List<DetectedText> {
        val out = mutableListOf<DetectedText>()
        for ((bubble, columns) in bubbles) {
            val cx = (bubble.left + bubble.right) / 2
            val cy = (bubble.top + bubble.bottom) / 2
            var x = cx + COL_W * columns.size / 2 - COL_W
            for (column in columns) {
                val top = cy - column.length * FONT / 2
                out += DetectedText(Box(x, top + 4, x + FONT, top + column.length * FONT), column)
                x -= COL_W
            }
        }
        out += DetectedText(Box(565, 894, 754, 930), "その日の午後…")
        return out
    }

    fun load(): PixelImage {
        val file = File(System.getProperty("samplePage"))
        val img = ImageIO.read(file)
        val argb = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        return PixelImage(img.width, img.height, argb)
    }

    fun save(image: PixelImage, file: File, boxes: List<Pair<Box, Color>> = emptyList()) {
        val out = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        out.setRGB(0, 0, image.width, image.height, image.argb, 0, image.width)
        val g = out.createGraphics()
        g.stroke = BasicStroke(2f)
        for ((b, color) in boxes) {
            g.color = color
            g.drawRect(b.left, b.top, b.width, b.height)
        }
        g.dispose()
        file.parentFile.mkdirs()
        ImageIO.write(out, "png", file)
    }
}
