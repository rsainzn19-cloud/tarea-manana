package com.mangatraductor.core

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * El lector de PaddleOCR (chino y coreano). Las pruebas con el modelo de
 * verdad necesitan QUALITY_DIR con paddle-zh/ko.onnx y .yml.
 */
class PaddleRecognizerTest {

    private val dir = File(System.getProperty("qualityDir").orEmpty())

    @Test
    fun dictionaryKeepsEveryCharacterAndUnquotesYaml() {
        val yml = """
            PreProcess:
              transform_ops:
              - DecodeImage:
                  channel_first: false
            PostProcess:
              name: CTCLabelDecode
              character_dict:
              -
              - 你
              - '!'
              - ''''
              - '"'
              - "\\"
              - "${'\\'}u00e9"
              - 한
            Global:
              model_name: test
        """.trimIndent()
        assertEquals(listOf("", "你", "!", "'", "\"", "\\", "é", "한"), PaddleRecognizer.parseDictionary(yml))
    }

    @Test
    fun verticalColumnsAreTurnedToTheLeft() {
        // Columna de 1 x 3: arriba rojo, abajo azul. Girada queda en fila, el rojo a la izquierda.
        val red = 0xFFFF0000.toInt()
        val green = 0xFF00FF00.toInt()
        val blue = 0xFF0000FF.toInt()
        val image = PixelImage(2, 3, intArrayOf(0, red, 0, green, 0, blue))
        val turned = PaddleRecognizer.rotateLeft(image, Box(1, 0, 2, 3))
        assertEquals(3, turned.width)
        assertEquals(1, turned.height)
        assertEquals(listOf(red, green, blue), turned.argb.toList())
        assertTrue(PaddleRecognizer.isVertical(Box(0, 0, 40, 300)))
        assertTrue(!PaddleRecognizer.isVertical(Box(0, 0, 300, 40)))
    }

    @Test
    fun readsChineseLinesAcrossAndDown() {
        load("zh").use {
            val across = render("今天天气真好！", cjkFont("SC"), vertical = false)
            assertEquals("今天天气真好！", it.read(across, Box(0, 0, across.width, across.height))!!.first)
            val down = render("我们走吧", cjkFont("SC"), vertical = true)
            val (text, confidence) = it.read(down, Box(0, 0, down.width, down.height))!!
            assertEquals("我们走吧", text)
            assertTrue(confidence > 0.8f, "confianza $confidence")
        }
    }

    @Test
    fun readsKoreanWithSpaces() {
        load("ko").use {
            val line = render("안녕하세요 반가워요!", cjkFont("KR"), vertical = false)
            assertEquals("안녕하세요 반가워요!", it.read(line, Box(0, 0, line.width, line.height))!!.first)
        }
    }

    /** ML Kit leyó mal una línea (una letra cambiada): PaddleOCR la corrige al preparar la página. */
    @Test
    fun pageProcessorRereadsEveryLine() {
        load("zh").use { recognizer ->
            val font = cjkFont("SC")
            val lines = listOf("你好，朋友。", "我们走吧！")
            val img = BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, 500, 400)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color.BLACK
            g.font = font
            val detections = lines.mapIndexed { i, text ->
                val y = 150 + i * 50
                g.drawString(text, 150, y)
                val w = g.fontMetrics.stringWidth(text)
                DetectedText(Box(148, y - 38, 152 + w, y + 10), text.replace("朋", "明").replace("走", "去"))
            }
            g.dispose()
            val image = PixelImage(500, 400, img.getRGB(0, 0, 500, 400, null, 0, 500))
            val echo = object : Translator {
                override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) = texts
            }
            val withoutPaddle = PageProcessor(null, echo, SourceLanguage.CHINESE).prepare(image, detections)
            assertEquals(listOf("你好，明友。我们去吧！"), withoutPaddle.texts)
            val page = PageProcessor(null, echo, SourceLanguage.CHINESE, lineReader = recognizer.asLineReader())
                .prepare(image, detections)
            assertEquals(listOf("你好，朋友。我们走吧！"), page.texts)
            page.cancel()
        }
    }

    /** El detector de líneas de PaddleOCR y el lector, como en la app: líneas horizontales y una columna. */
    @Test
    fun detectorAndReaderFindLinesThatMlKitDidNotSee() {
        val detectorFile = File(dir, "paddle-det.onnx")
        assumeTrue("QUALITY_DIR sin el detector de PaddleOCR", detectorFile.exists())
        val font = cjkFont("SC")
        val img = BufferedImage(700, 500, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 700, 500)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.font = font
        // Tres líneas cortas y juntas (un globo estrecho) y una columna vertical.
        listOf("你好朋友", "我们走吧", "明天见！").forEachIndexed { i, t -> g.drawString(t, 60, 100 + i * 46) }
        "今天天气真好".forEachIndexed { i, ch -> g.drawString(ch.toString(), 500, 100 + i * 40) }
        g.dispose()
        val image = PixelImage(700, 500, img.getRGB(0, 0, 700, 500, null, 0, 700))
        PaddleDetector.load(detectorFile, 4).use { detector ->
            load("zh").use { recognizer ->
                val lines = detector.detect(image)
                val found = PageProcessor.readLines(image, recognizer.asLineReader(), emptyList(), null, lines)
                assertEquals(setOf("你好朋友", "我们走吧", "明天见！", "今天天气真好"), found.map { it.text }.toSet(), "$lines")
            }
        }
    }

    private fun load(language: String): PaddleRecognizer {
        val model = File(dir, "paddle-$language.onnx")
        assumeTrue("QUALITY_DIR sin PaddleOCR", model.exists())
        return PaddleRecognizer.load(model, File(dir, "paddle-$language.yml"), 4)
    }

    private fun cjkFont(): File {
        val file = File("/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc")
        assumeTrue("falta la fuente Noto CJK", file.exists())
        return file
    }

    /** La fuente Noto CJK de esa región (SC, KR…), a 36 px. */
    private fun cjkFont(region: String): Font =
        Font.createFonts(cjkFont()).first { it.family.endsWith(region) }.deriveFont(36f)

    private fun render(text: String, font: Font, vertical: Boolean): PixelImage {
        val size = font.size
        val w = if (vertical) size + 20 else size * text.length + 20
        val h = if (vertical) (size + 6) * text.length + 20 else size + 20
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.font = font
        if (vertical) {
            text.forEachIndexed { i, ch -> g.drawString(ch.toString(), 10, 10 + size + i * (size + 6) - 4) }
        } else {
            g.drawString(text, 10, 10 + size - 6)
        }
        g.dispose()
        return PixelImage(w, h, img.getRGB(0, 0, w, h, null, 0, w))
    }
}
