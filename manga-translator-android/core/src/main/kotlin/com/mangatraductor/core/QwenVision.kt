package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Una imagen ya vista por [QwenVision]: un vector por cada trozo de 32 x 32 píxeles. */
class ImageEmbedding(
    /** [tokens] vectores de [hidden] números, seguidos. */
    val features: FloatArray,
    val tokens: Int,
    val hidden: Int,
    /** Trozos a lo ancho y a lo alto (tokens = gridWidth * gridHeight). */
    val gridWidth: Int,
    val gridHeight: Int,
)

/**
 * Los "ojos" de Qwen 3.5 (el codificador de imagen de la exportación de
 * onnx-community): convierte la página en vectores que el modelo lee como
 * si fueran palabras. Así ve quién habla y qué pasa en cada viñeta.
 */
class QwenVision private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
) : Closeable {

    /** La página reducida a unos [maxTokens] trozos (más trozos: ve más detalle, pero tarda más). */
    fun encode(image: PixelImage, maxTokens: Int = MAX_TOKENS): ImageEmbedding {
        val (w, h) = targetSize(image.width, image.height, maxTokens)
        val rgb = toChw(image, Box(0, 0, image.width, image.height), w, h, w, h)
        val gh = h / PATCH
        val gw = w / PATCH
        val pixels = patches(rgb, w, h)
        val feeds = mapOf(
            "pixel_values" to OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), longArrayOf((gh * gw).toLong(), PATCH_VALUES.toLong())),
            "image_grid_thw" to OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(1, gh.toLong(), gw.toLong())), longArrayOf(1, 3)),
        )
        try {
            session.run(feeds).use { result ->
                val out = result.get(0) as OnnxTensor
                val shape = (out.info as TensorInfo).shape
                val buffer = out.floatBuffer
                val features = FloatArray(buffer.remaining()).also(buffer::get)
                return ImageEmbedding(features, shape[0].toInt(), shape[1].toInt(), gw / MERGE, gh / MERGE)
            }
        } finally {
            feeds.values.forEach { it.close() }
        }
    }

    override fun close() = session.close()

    companion object {
        const val PATCH = 16
        const val MERGE = 2

        /** Cada vector de Qwen sale de 2 x 2 parches de 16 píxeles. */
        const val FACTOR = PATCH * MERGE

        /** ~250 trozos: una página de manga a unos 416 x 608 píxeles (legible sin tardar mucho). */
        const val MAX_TOKENS = 256

        /** 3 colores x 2 "fotogramas" (una imagen fija se repite) x 16 x 16. */
        private const val PATCH_VALUES = 3 * 2 * PATCH * PATCH

        /** Tamaño (ancho, alto), múltiplo de 32, con como mucho [maxTokens] trozos y sin agrandar. */
        fun targetSize(width: Int, height: Int, maxTokens: Int = MAX_TOKENS): Pair<Int, Int> {
            val scale = min(1.0, sqrt(maxTokens.toDouble() * FACTOR * FACTOR / (width.toDouble() * height)))
            val w = max(FACTOR, (floor(width * scale / FACTOR) * FACTOR).toInt())
            val h = max(FACTOR, (floor(height * scale / FACTOR) * FACTOR).toInt())
            return w to h
        }

        /**
         * Los parches en el orden del modelo: por cada trozo de 32 x 32 (en
         * filas), sus 4 parches de 16 x 16, cada uno con sus 3 colores, 2
         * veces, normalizados a [-1, 1].
         */
        internal fun patches(rgb: FloatArray, width: Int, height: Int): FloatArray {
            val gw = width / FACTOR
            val gh = height / FACTOR
            val plane = width * height
            val out = FloatArray(gh * gw * MERGE * MERGE * PATCH_VALUES)
            var o = 0
            for (by in 0 until gh) for (bx in 0 until gw) for (my in 0 until MERGE) for (mx in 0 until MERGE) {
                val top = (by * MERGE + my) * PATCH
                val left = (bx * MERGE + mx) * PATCH
                for (c in 0 until 3) for (t in 0 until 2) for (py in 0 until PATCH) {
                    val row = c * plane + (top + py) * width + left
                    for (px in 0 until PATCH) out[o++] = rgb[row + px] * 2f - 1f
                }
            }
            return out
        }

        fun load(model: File, threads: Int): QwenVision {
            val (session, _) = Sessions.open(model, threads, Acceleration.CPU)
            return QwenVision(OrtEnvironment.getEnvironment(), session)
        }
    }
}
