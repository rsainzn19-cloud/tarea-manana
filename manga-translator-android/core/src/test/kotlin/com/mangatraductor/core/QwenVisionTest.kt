package com.mangatraductor.core

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/** Qwen 3.5 viendo imágenes: el orden de los parches, las posiciones y, con QWEN_DIR, el modelo de verdad. */
class QwenVisionTest {

    @Test
    fun pagesAreShrunkToAboutTwoHundredFiftyPiecesOf32Pixels() {
        // Una página de Black Jack ni Yoroshiku (1414 x 2000): 13 x 19 trozos.
        assertEquals(416 to 608, QwenVision.targetSize(1414, 2000))
        // Las imágenes pequeñas no se agrandan (sólo se ajustan a múltiplos de 32).
        assertEquals(96 to 64, QwenVision.targetSize(100, 70))
        assertEquals(32 to 32, QwenVision.targetSize(10, 10))
    }

    @Test
    fun patchesFollowTheModelOrder() {
        // Imagen de 64 x 32: dos trozos de 32 x 32, cada uno con 4 parches de 16 x 16.
        val w = 64
        val h = 32
        val plane = w * h
        // Valor de cada píxel: su columna en el rojo, su fila en el verde, 0,5 en el azul (0..1).
        val rgb = FloatArray(3 * plane) { i ->
            val p = i % plane
            when (i / plane) {
                0 -> (p % w) / 100f
                1 -> (p / w) / 100f
                else -> 0.5f
            }
        }
        val patches = QwenVision.patches(rgb, w, h)
        val size = 3 * 2 * 16 * 16
        assertEquals(8 * size, patches.size)
        fun value(patch: Int, color: Int, frame: Int, y: Int, x: Int) =
            patches[patch * size + ((color * 2 + frame) * 16 + y) * 16 + x]
        // Parche 0: arriba a la izquierda. Parche 1: a su derecha (x 16..31). Parche 2: debajo (y 16..31).
        // Parche 4: el primero del segundo trozo (x 32..47).
        assertEquals(2 * 0.05f - 1, value(0, 0, 0, 3, 5), 1e-6f)
        assertEquals(2 * 0.21f - 1, value(1, 0, 1, 3, 5), 1e-6f)
        assertEquals(2 * 0.19f - 1, value(2, 1, 0, 3, 5), 1e-6f)
        assertEquals(2 * 0.37f - 1, value(4, 0, 0, 0, 5), 1e-6f)
        assertEquals(0f, value(7, 2, 1, 15, 15), 1e-6f)
    }

    @Test
    fun imagePiecesShareTheirTurnAndCarryRowAndColumn() {
        val pad = 99
        // 2 piezas de texto, una imagen de 3 x 2 trozos y 1 pieza de texto.
        val prompt = intArrayOf(1, 2, pad, pad, pad, pad, pad, pad, 3)
        val image = ImageEmbedding(FloatArray(6), 6, 1, gridWidth = 3, gridHeight = 2)
        val (positions, next) = LocalLlm.ropePositions(prompt, pad, image)
        val n = prompt.size
        assertEquals(listOf<Long>(0, 1, 2, 2, 2, 2, 2, 2, 5), positions.copyOfRange(0, n).toList())
        assertEquals(listOf<Long>(0, 1, 2, 2, 2, 3, 3, 3, 5), positions.copyOfRange(n, 2 * n).toList())
        assertEquals(listOf<Long>(0, 1, 2, 3, 4, 2, 3, 4, 5), positions.copyOfRange(2 * n, 3 * n).toList())
        assertEquals(6L, next)
        // Sin imagen, las tres posiciones avanzan juntas.
        val (text, after) = LocalLlm.ropePositions(intArrayOf(5, 6, 7), pad, null)
        assertEquals(listOf<Long>(0, 1, 2, 0, 1, 2, 0, 1, 2), text.toList())
        assertEquals(3L, after)
    }

    @Test
    fun qwenSeesColoursAndShapes() {
        val dir = File(System.getProperty("qwenDir").orEmpty())
        assumeTrue("QWEN_DIR sin el codificador de imagen", File(dir, "vision_encoder_q4.onnx").exists())
        val img = BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 320, 320)
        g.color = Color.RED
        g.fillOval(60, 60, 200, 200)
        g.dispose()
        val image = PixelImage(320, 320, img.getRGB(0, 0, 320, 320, null, 0, 320))
        val threads = Runtime.getRuntime().availableProcessors()
        QwenVision.load(File(dir, "vision_encoder_q4.onnx"), threads).use { vision ->
            var start = System.nanoTime()
            val seen = vision.encode(image)
            println("imagen vista en ${(System.nanoTime() - start) / 1_000_000} ms: ${seen.tokens} trozos")
            assertEquals(100, seen.tokens) // 320 / 32 = 10 x 10
            load(dir, threads).use { llm ->
                assertTrue(llm.acceptsImages)
                start = System.nanoTime()
                val answer = llm.chat("You are a helpful assistant.", "What shape and colour is in this picture? Answer in a few words.", 20, seen)
                println("Qwen: \"$answer\" en ${(System.nanoTime() - start) / 1_000_000} ms")
                assertTrue("red" in answer.lowercase(), answer)
                assertTrue("circle" in answer.lowercase() || "round" in answer.lowercase() || "oval" in answer.lowercase(), answer)
            }
        }
    }

    /** Traducción viendo la página de prueba (con la imagen JPEG como la manda la app). */
    @Test
    fun translatesWhileSeeingThePage() {
        val dir = File(System.getProperty("qwenDir").orEmpty())
        assumeTrue("QWEN_DIR sin el codificador de imagen", File(dir, "vision_encoder_q4.onnx").exists())
        val page = SamplePage.load()
        val threads = Runtime.getRuntime().availableProcessors()
        QwenVision.load(File(dir, "vision_encoder_q4.onnx"), threads).use { vision ->
            load(dir, threads).use { llm ->
                val progress = mutableListOf<String>()
                val translator = QwenTranslator(llm, "en", vision = vision, decode = { page }) { progress += it }
                assertTrue(translator.seesPage)
                val start = System.nanoTime()
                val result = translator.translate(SamplePage.expectedTexts, ByteArray(1), null)
                println("Qwen viendo la página, ${(System.nanoTime() - start) / 1_000_000} ms:")
                SamplePage.expectedTexts.zip(result).forEach { (jp, tr) -> println("  $jp -> $tr") }
                assertEquals(SamplePage.expectedTexts.size, result.size)
                assertTrue(result.all { it.isNotBlank() }, "faltan traducciones: $result")
                assertTrue("Qwen mirando la página…" in progress)
            }
        }
    }

    private fun load(dir: File, threads: Int) = LocalLlm.load(
        tokenizer = File(dir, "tokenizer.json"),
        decoder = OnnxPatcher.withInt8MatMul(File(dir, "decoder_model_merged_q4.onnx")),
        embed = File(dir, "embed_tokens_q4.onnx"),
        threads = threads,
    )
}
