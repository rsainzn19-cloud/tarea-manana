package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * El detector de líneas de PaddleOCR (PP-OCRv5, DBNet): marca cada línea
 * horizontal y cada columna vertical de texto por separado. Entrenado con
 * chino (y otros idiomas), encuentra lo que el OCR de ML Kit no ve, como el
 * texto vertical de un manga traducido al chino, y no junta las líneas de un
 * globo como hace el detector de manga (entrenado sobre todo con japonés).
 */
class PaddleDetector private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : Closeable {

    /** Las líneas de texto de la página (las tiras muy largas, por trozos). */
    fun detect(image: PixelImage): List<Box> {
        val found = mutableListOf<TextLayout.ScoredBox>()
        for ((top, height) in Tiles.vertical(image.width, image.height)) {
            found += detectTile(image, Box(0, top, image.width, top + height))
        }
        return ComicTextDetector.nms(found).map { it.box }
    }

    private fun detectTile(image: PixelImage, tile: Box): List<TextLayout.ScoredBox> {
        // El lado mayor a SIDE px y los dos múltiplos de 32, como en PaddleOCR.
        val scale = SIDE.toFloat() / max(tile.width, tile.height)
        val nw = max(32, (tile.width * scale / 32).roundToInt() * 32)
        val nh = max(32, (tile.height * scale / 32).roundToInt() * 32)
        val rgb = toChw(image, tile, nw, nh, nw, nh)
        // BGR normalizado con la media y la desviación de ImageNet (en ese orden de canales).
        val plane = nw * nh
        val input = FloatArray(3 * plane)
        for (c in 0 until 3) {
            val from = (2 - c) * plane
            for (p in 0 until plane) input[c * plane + p] = (rgb[from + p] - MEAN[c]) / STD[c]
        }
        val prob = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, nh.toLong(), nw.toLong())).use { x ->
            session.run(mapOf(session.inputNames.first() to x)).use { r ->
                (r.get(0) as OnnxTensor).floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) }
            }
        }

        // Zonas con probabilidad > 0,3; cada una es una línea si su media pasa de 0,6.
        return DbLines.find(prob, nw, nh, nw, nh, tile, image, tile.width.toFloat() / nw, tile.height.toFloat() / nh, BOX_THRESHOLD)
    }

    override fun close() = session.close()

    companion object {
        /** El lado mayor de la página para el detector (PaddleOCR usa 960; con 1280 se separan mejor las líneas cortas y juntas). */
        private const val SIDE = 1280
        private const val BOX_THRESHOLD = 0.6f
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        fun load(model: File, threads: Int): PaddleDetector {
            val (session, _) = Sessions.open(model, threads, Acceleration.CPU)
            return PaddleDetector(OrtEnvironment.getEnvironment(), session)
        }
    }
}
