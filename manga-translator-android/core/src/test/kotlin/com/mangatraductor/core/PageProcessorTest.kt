package com.mangatraductor.core

import java.awt.Color
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageProcessorTest {

    private val outDir = File("build/test-output")

    /** Traductor falso: devuelve el texto marcado, para no depender de la red. */
    private val echo = object : Translator {
        override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) = texts.map { "EN($it)" }
    }

    @Test
    fun mergesColumnsIntoBubblesInReadingOrder() {
        val blocks = BlockMerger.merge(SamplePage.detections())
        assertEquals(5, blocks.size)
        assertEquals(SamplePage.expectedTexts, blocks.map { it.detectorText() })
    }

    @Test
    fun cleansTextAndFindsBubbles() {
        val page = SamplePage.load()
        val result = PageProcessor(reader = null, translator = echo).process(page, SamplePage.detections())

        assertEquals(SamplePage.expectedTexts.map { "EN($it)" }, result.blocks.map { it.translation })

        val gray = result.cleaned.gray()
        for (block in result.blocks) {
            // Dentro de cada globo ya no queda tinta (el contorno sí debe quedar).
            val b = block.box
            val around = b.expand(12).clip(page.width, page.height)
            var dark = 0
            for (y in around.top until around.bottom) for (x in around.left until around.right) {
                val inside = SamplePage.insideBubble(x, y) || SamplePage.insideNarration(x, y)
                if (inside && gray[y * page.width + x] < 128) dark++
            }
            assertEquals(0, dark, "quedan restos de texto en $b")

            // La zona de escritura es más ancha que la columna japonesa y contiene el bloque.
            val r = block.renderBox!!
            assertTrue(r.left <= b.left && r.right >= b.right && r.top <= b.top && r.bottom >= b.bottom)
        }
        // Los globos verticales ganan ancho para el texto horizontal.
        assertTrue(result.blocks[0].renderBox!!.width > result.blocks[0].box.width + 20)

        // El contorno del primer globo (elipse 560..800 x 70..330) sigue ahí.
        assertTrue(gray[200 * page.width + 561] < 100 || gray[200 * page.width + 562] < 100)

        SamplePage.save(result.cleaned, File(outDir, "cleaned.png"),
            result.blocks.flatMap { listOf(it.box to Color.RED, it.renderBox!! to Color.BLUE) })
    }

    /** Carpeta con el modelo manga-ocr en ONNX (variable MANGA_OCR_DIR), o null para saltar la prueba. */
    private fun modelDir(): File? {
        val dir = File(System.getProperty("mangaOcrDir").orEmpty())
        if (File(dir, "encoder_model_quantized.onnx").exists()) return dir
        println("SKIP: define MANGA_OCR_DIR con el modelo ONNX para probar el OCR")
        return null
    }

    private fun vocab() = File(System.getProperty("mangaOcrVocab")).readLines()

    @Test
    fun mangaOcrLoadsFromMemoryMappedModel() {
        // Igual que en el móvil: el modelo se mapea en memoria (allí, desde dentro del APK).
        val dir = modelDir() ?: return
        fun map(name: String) = RandomAccessFile(File(dir, name), "r").use {
            it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
        }
        MangaOcr.fromBuffers(map("encoder_model_quantized.onnx"), map("decoder_model_quantized.onnx"), vocab()).use { ocr ->
            val page = SamplePage.load()
            val first = BlockMerger.merge(SamplePage.detections()).first()
            Cleaner.refineBlocks(page, listOf(first))
            assertEquals("おはよう!今日はいい天気だね。", ocr.read(page, first.box))
        }
    }

    @Test
    fun mangaOcrReadsTheBubbles() {
        val dir = modelDir() ?: return
        MangaOcr.fromFiles(
            File(dir, "encoder_model_quantized.onnx").path,
            File(dir, "decoder_model_quantized.onnx").path,
            vocab(),
        ).use { ocr ->
            val page = SamplePage.load()
            val start = System.currentTimeMillis()
            val result = PageProcessor(ocr, echo).process(page, SamplePage.detections())
            println("OCR de ${result.blocks.size} globos en ${System.currentTimeMillis() - start} ms")
            result.blocks.forEach { println("  ${it.text}") }

            val normalize = { s: String -> s.replace("！", "!").replace("…", "...") }
            val expected = SamplePage.expectedTexts.map(normalize)
            val hits = result.blocks.count { normalize(it.text) in expected }
            assertTrue(hits >= 4, "manga-ocr sólo acertó $hits de 5")
        }
    }
}
