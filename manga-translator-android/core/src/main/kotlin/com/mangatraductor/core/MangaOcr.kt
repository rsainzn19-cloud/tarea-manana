package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Lee el texto de un bloque de la página. */
fun interface TextReader {
    fun read(image: PixelImage, box: Box): String
}

/**
 * manga-ocr (kha-white/manga-ocr-base) exportado a ONNX: un codificador ViT
 * que "mira" el recorte de 224x224 y un decodificador BERT que escribe el
 * texto carácter a carácter. Entiende texto vertical, horizontal y furigana.
 *
 * Se crea con [fromFiles] (archivos .onnx) o [fromBuffers] (p. ej. el modelo
 * mapeado en memoria directamente desde los assets del APK).
 */
class MangaOcr private constructor(
    private val env: OrtEnvironment,
    private val options: OrtSession.SessionOptions,
    private val encoder: OrtSession,
    private val decoder: OrtSession,
    private val vocab: List<String>,
) : TextReader, Closeable {

    override fun read(image: PixelImage, box: Box): String {
        val crop = box.expand(4).clip(image.width, image.height)
        if (crop.width < 2 || crop.height < 2) return ""
        val pixels = preprocess(image, crop)

        OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { input ->
            encoder.run(mapOf("pixel_values" to input)).use { encoded ->
                val hidden = encoded[0] as OnnxTensor
                return decode(hidden)
            }
        }
    }

    /** Decodificación voraz, sin repetir trigramas (como no_repeat_ngram_size=3). */
    private fun decode(hidden: OnnxTensor): String {
        val ids = mutableListOf(START_ID)
        while (ids.size < MAX_LENGTH) {
            val idBuffer = LongBuffer.wrap(LongArray(ids.size) { ids[it].toLong() })
            val next = OnnxTensor.createTensor(env, idBuffer, longArrayOf(1, ids.size.toLong())).use { inputIds ->
                decoder.run(mapOf("input_ids" to inputIds, "encoder_hidden_states" to hidden)).use { out ->
                    val logits = (out[0] as OnnxTensor).floatBuffer
                    val vocabSize = logits.capacity() / ids.size
                    val offset = (ids.size - 1) * vocabSize
                    val banned = bannedTokens(ids)
                    var best = -1
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (t in 0 until vocabSize) {
                        val s = logits.get(offset + t)
                        if (s > bestScore && t !in banned) { bestScore = s; best = t }
                    }
                    best
                }
            }
            if (next == END_ID || next < 0) break
            ids += next
        }
        return postprocess(ids.drop(1))
    }

    private fun bannedTokens(ids: List<Int>): Set<Int> {
        if (ids.size < 3) return emptySet()
        val a = ids[ids.size - 2]
        val b = ids[ids.size - 1]
        val banned = HashSet<Int>()
        for (i in 0 until ids.size - 2) if (ids[i] == a && ids[i + 1] == b) banned += ids[i + 2]
        return banned
    }

    private fun postprocess(ids: List<Int>): String {
        val text = ids.filter { it > 4 && it < vocab.size } // 0-4: [PAD] [UNK] [CLS] [SEP] [MASK]
            .joinToString("") { vocab[it].removePrefix("##") }
            .filterNot { it.isWhitespace() }
            .replace("…", "...")
        return Regex("[・.]{2,}").replace(text) { ".".repeat(it.value.length) }
    }

    override fun close() {
        encoder.close()
        decoder.close()
        options.close()
    }

    companion object {
        const val SIZE = 224

        fun fromFiles(encoderPath: String, decoderPath: String, vocab: List<String>, threads: Int = 4): MangaOcr {
            val env = OrtEnvironment.getEnvironment()
            val options = sessionOptions(threads)
            return MangaOcr(env, options, env.createSession(encoderPath, options), env.createSession(decoderPath, options), vocab)
        }

        /** Los búferes deben ser directos (por ejemplo, un MappedByteBuffer). */
        fun fromBuffers(encoder: ByteBuffer, decoder: ByteBuffer, vocab: List<String>, threads: Int = 4): MangaOcr {
            val env = OrtEnvironment.getEnvironment()
            val options = sessionOptions(threads)
            return MangaOcr(env, options, env.createSession(encoder, options), env.createSession(decoder, options), vocab)
        }

        private fun sessionOptions(threads: Int) = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
        private const val START_ID = 2
        private const val END_ID = 3
        private const val MAX_LENGTH = 300

        /**
         * Recorte -> gris -> 224x224 (bilineal con antialias, como PIL) ->
         * valores en [-1, 1], repetidos en los 3 canales. Formato NCHW.
         */
        fun preprocess(image: PixelImage, crop: Box): FloatArray {
            val src = FloatArray(crop.area)
            for (y in 0 until crop.height) for (x in 0 until crop.width) {
                src[y * crop.width + x] = PixelImage.luma(image.argb[(crop.top + y) * image.width + crop.left + x]).toFloat()
            }
            val horizontal = resizeAxis(src, crop.width, crop.height, SIZE, horizontal = true)
            val resized = resizeAxis(horizontal, SIZE, crop.height, SIZE, horizontal = false)
            val plane = SIZE * SIZE
            val out = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val v = (resized[i].coerceIn(0f, 255f) / 255f - 0.5f) / 0.5f
                out[i] = v; out[plane + i] = v; out[2 * plane + i] = v
            }
            return out
        }

        /** Remuestreo de un eje con filtro triangular (igual que Image.BILINEAR de PIL). */
        private fun resizeAxis(src: FloatArray, w: Int, h: Int, target: Int, horizontal: Boolean): FloatArray {
            val inSize = if (horizontal) w else h
            val scale = inSize.toDouble() / target
            val support = max(scale, 1.0)
            val out = if (horizontal) FloatArray(target * h) else FloatArray(w * target)
            for (o in 0 until target) {
                val center = (o + 0.5) * scale
                val lo = max(0, floor(center - support).toInt())
                val hi = min(inSize, ceil(center + support).toInt())
                val weights = DoubleArray(hi - lo) { i ->
                    max(0.0, 1.0 - abs((lo + i + 0.5 - center) / support))
                }
                val total = weights.sum().takeIf { it > 0 } ?: 1.0
                if (horizontal) {
                    for (y in 0 until h) {
                        var acc = 0.0
                        for (i in weights.indices) acc += weights[i] * src[y * w + lo + i]
                        out[y * target + o] = (acc / total).toFloat()
                    }
                } else {
                    for (x in 0 until w) {
                        var acc = 0.0
                        for (i in weights.indices) acc += weights[i] * src[(lo + i) * w + x]
                        out[o * w + x] = (acc / total).toFloat()
                    }
                }
            }
            return out
        }
    }
}
