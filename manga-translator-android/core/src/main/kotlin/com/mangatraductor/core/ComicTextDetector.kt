package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lo que el detector de manga ve en la página: los bloques de texto (≈ el
 * texto de un globo, completo) y la probabilidad de que cada píxel sea letra.
 */
class TextLayout(val blocks: List<ScoredBox>, val width: Int, val height: Int, private val mask: ByteArray) {
    class ScoredBox(val box: Box, val score: Float)

    /** Probabilidad 0..255 de que (x, y) sea parte de una letra. */
    fun ink(x: Int, y: Int): Int = mask[y * width + x].toInt() and 0xFF

    /** Fracción de [box] que es texto (para descartar bloques que no lo son). */
    fun inkFraction(box: Box): Float {
        val b = box.clip(width, height)
        if (b.area <= 0) return 0f
        var n = 0
        for (y in b.top until b.bottom) for (x in b.left until b.right) if (ink(x, y) >= INK) n++
        return n.toFloat() / b.area
    }

    companion object {
        /** Umbral de la máscara (probabilidad 0,5). */
        const val INK = 128
    }
}

/**
 * comic-text-detector (dmMaze), el detector que usan manga-image-translator,
 * BallonsTranslator y Koharu, en ONNX: una red YOLOv5 que encuentra los
 * bloques de texto y una U-Net que marca los píxeles de las letras. Está
 * entrenado con manga y cómics, así que no parte los globos como un OCR
 * general y encuentra también texto estilizado.
 */
class ComicTextDetector private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : Closeable {

    fun detect(image: PixelImage): TextLayout {
        val w = image.width
        val h = image.height
        val mask = ByteArray(w * h)
        val found = mutableListOf<TextLayout.ScoredBox>()
        for ((top, height) in Tiles.vertical(w, h)) {
            found += detectTile(image, Box(0, top, w, top + height), mask)
        }
        return TextLayout(nms(found), w, h, mask)
    }

    private fun detectTile(image: PixelImage, tile: Box, mask: ByteArray): List<TextLayout.ScoredBox> {
        val scale = SIZE.toFloat() / max(tile.width, tile.height)
        val nw = max(1, (tile.width * scale).roundToInt())
        val nh = max(1, (tile.height * scale).roundToInt())
        val input = toChw(image, tile, nw, nh, SIZE)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
        val (blk, seg) = tensor.use {
            session.run(mapOf("images" to it)).use { result ->
                (result.get("blk").get() as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) } to
                    (result.get("seg").get() as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) }
            }
        }

        // Máscara de las letras, a la resolución de la página (la mayor si dos trozos se solapan).
        for (y in 0 until tile.height) {
            val sy = (y + 0.5f) * scale - 0.5f
            val row = (tile.top + y) * image.width
            for (x in 0 until tile.width) {
                val p = bilinear(seg, 0, SIZE, SIZE, (x + 0.5f) * scale - 0.5f, sy)
                val v = (p.coerceIn(0f, 1f) * 255).toInt()
                if (v > (mask[row + x].toInt() and 0xFF)) mask[row + x] = v.toByte()
            }
        }

        // Bloques: [cx, cy, w, h, objeto, clase 1, clase 2] en coordenadas de 1024.
        val out = mutableListOf<TextLayout.ScoredBox>()
        var i = 0
        while (i + 7 <= blk.size) {
            val score = blk[i + 4] * max(blk[i + 5], blk[i + 6])
            if (score >= MIN_SCORE) {
                val cx = blk[i] / scale
                val cy = blk[i + 1] / scale
                val bw = blk[i + 2] / scale
                val bh = blk[i + 3] / scale
                val box = Box(
                    (cx - bw / 2).toInt(), tile.top + (cy - bh / 2).toInt(),
                    (cx + bw / 2).toInt(), tile.top + (cy + bh / 2).toInt(),
                ).clip(image.width, image.height)
                if (box.width > 2 && box.height > 2) out += TextLayout.ScoredBox(box, score)
            }
            i += 7
        }
        return out
    }

    override fun close() = session.close()

    companion object {
        private const val SIZE = 1024
        const val MIN_SCORE = 0.4f
        private const val NMS_IOU = 0.35f

        fun load(model: File, threads: Int): ComicTextDetector {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                // Sin esto la red pasa por números "desnormales" y va ~20 veces más lenta.
                addConfigEntry("session.set_denormal_as_zero", "1")
            }
            return ComicTextDetector(env, env.createSession(model.path, options))
        }

        /** Quita las cajas repetidas: se queda con la de más confianza. */
        fun nms(boxes: List<TextLayout.ScoredBox>): List<TextLayout.ScoredBox> {
            val kept = mutableListOf<TextLayout.ScoredBox>()
            for (b in boxes.sortedByDescending { it.score }) {
                if (kept.none { iou(it.box, b.box) > NMS_IOU }) kept += b
            }
            return kept
        }

        private fun iou(a: Box, b: Box): Float {
            val inter = a.overlapArea(b).toFloat()
            return inter / (a.area + b.area - inter)
        }
    }
}
