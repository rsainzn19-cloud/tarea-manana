package com.mangatraductor.core

import java.awt.Color
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * comic-text-detector y LaMa de verdad (los modelos de manga-image-translator
 * y Koharu), en el PC. Necesita QUALITY_DIR con los dos .onnx.
 */
class QualityModelsTest {

    private val dir = File(System.getProperty("qualityDir").orEmpty())
    private val echo = object : Translator {
        override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) = texts.map { "T" }
    }

    @Test
    fun detectorFindsEveryBubbleAndTheExactLetters() {
        assumeTrue("QUALITY_DIR no indicado", File(dir, "comic-text-detector.onnx").exists())
        val page = SamplePage.load()
        ComicTextDetector.load(File(dir, "comic-text-detector.onnx"), 4).use { detector ->
            var start = System.nanoTime()
            val layout = detector.detect(page)
            println("comic-text-detector: ${(System.nanoTime() - start) / 1_000_000} ms, ${layout.blocks.size} bloques")
            // Los cuatro globos, cada uno en un solo bloque que abarca todas sus columnas.
            val bubbles = BlockMerger.merge(SamplePage.detections()).filter { it.isVertical }
            assertEquals(4, bubbles.size)
            for (b in bubbles) {
                val hits = layout.blocks.filter { it.box.overlapArea(b.box) > b.box.area * 0.8 }
                assertEquals(1, hits.size, "globo ${b.box}: ${layout.blocks.map { it.box }}")
            }
            // La máscara marca las letras (también las de la narración, sin bloque propio).
            val narration = Box(565, 894, 754, 930)
            assertTrue(layout.inkFraction(narration) > 0.15f, "narración: ${layout.inkFraction(narration)}")
            assertTrue(layout.inkFraction(Box(380, 350, 500, 430)) < 0.01f) // el cuerpo del personaje no es texto

            // Toda la página con el detector: los mismos 5 textos, y dentro de los globos no queda tinta.
            start = System.nanoTime()
            val result = PageProcessor(null, echo).process(page, SamplePage.detections(), layout)
            println("página con el detector: ${(System.nanoTime() - start) / 1_000_000} ms")
            assertEquals(SamplePage.expectedTexts, result.blocks.map { it.text })
            val gray = result.cleaned.gray()
            var dark = 0
            for (y in 0 until page.height) for (x in 0 until page.width) {
                if ((SamplePage.insideBubble(x, y) || SamplePage.insideNarration(x, y)) && gray[y * page.width + x] < 128) dark++
            }
            assertEquals(0, dark)
            SamplePage.save(result.cleaned, File("build/test-output/cleaned-ctd.png"),
                result.blocks.map { it.box to Color.RED })
        }
    }

    @Test
    fun lamaRebuildsScreentoneUnderTheText() {
        assumeTrue("QUALITY_DIR no indicado", File(dir, "lama-manga.onnx").exists())
        // Trama de puntos con líneas diagonales, y "letras" negras encima.
        val w = 400
        val h = 400
        fun background(x: Int, y: Int): Int {
            val dot = (x % 8 - 4) * (x % 8 - 4) + (y % 8 - 4) * (y % 8 - 4) < 6
            val line = (x + y) % 40 < 3
            val v = if (line) 0 else if (dot) 40 else 255
            return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val truth = PixelImage(w, h, IntArray(w * h) { background(it % w, it / w) })
        val page = truth.copy()
        val letters = Box(170, 100, 230, 300)
        for (y in letters.top until letters.bottom) for (x in letters.left until letters.right) {
            if ((y - letters.top) % 34 < 26 && (x - letters.left) % 32 < 24) page.argb[y * w + x] = 0xFF000000.toInt()
        }
        val detections = listOf(DetectedText(letters, "文字"))
        // La máscara de las letras la daría comic-text-detector (sobre trama, el método sencillo no las separa).
        val ink = ByteArray(w * h) { p -> if (page.argb[p] == 0xFF000000.toInt() && truth.argb[p] != page.argb[p]) -1 else 0 }
        val layout = TextLayout(emptyList(), w, h, ink)

        fun error(result: PixelImage): Double {
            var sum = 0.0
            for (y in letters.top until letters.bottom) for (x in letters.left until letters.right) {
                sum += abs(PixelImage.luma(result.argb[y * w + x]) - PixelImage.luma(truth.argb[y * w + x]))
            }
            return sum / letters.area
        }
        val simple = PageProcessor(null, echo).process(page, detections, layout).cleaned
        LamaInpainter.load(File(dir, "lama-manga.onnx"), 4).use { lama ->
            val start = System.nanoTime()
            val result = PageProcessor(null, echo, inpainter = lama).process(page, detections, layout).cleaned
            println("LaMa: ${(System.nanoTime() - start) / 1_000_000} ms; error ${"%.1f".format(error(result))} (sencillo: ${"%.1f".format(error(simple))})")
            SamplePage.save(result, File("build/test-output/lama.png"))
            SamplePage.save(simple, File("build/test-output/lama-sin.png"))
            assertTrue(error(result) < error(simple) * 0.7, "LaMa no mejora el relleno sencillo")
        }
    }
}
