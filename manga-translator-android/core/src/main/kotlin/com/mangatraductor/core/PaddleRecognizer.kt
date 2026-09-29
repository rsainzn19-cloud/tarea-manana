package com.mangatraductor.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil

/**
 * Lee una línea de texto con el reconocedor de PaddleOCR (PP-OCRv5, de
 * Baidu, en su ONNX oficial): muy bueno con el chino y el coreano, también
 * con letra estilizada. La línea la encuentra ML Kit; esto sólo la lee. Las
 * columnas verticales se giran 90° a la izquierda, como hace PaddleOCR.
 */
class PaddleRecognizer private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    /** Símbolo de cada clase: 0 es el "hueco" del CTC y la última, el espacio. */
    private val symbols: List<String>,
) : Closeable {

    /** Texto de la línea [box] y la confianza media (0..1), o null si es muy pequeña. */
    fun read(image: PixelImage, box: Box): Pair<String, Float>? {
        var crop = box.expand(2).clip(image.width, image.height)
        if (crop.width < 4 || crop.height < 4) return null
        var source = image
        if (isVertical(crop)) {
            source = rotateLeft(image, crop)
            crop = Box(0, 0, source.width, source.height)
        }
        val width = ceil(HEIGHT * crop.width.toFloat() / crop.height).toInt().coerceIn(16, 3200)
        val rgb = toChw(source, crop, width, HEIGHT, width, HEIGHT)
        // El modelo espera BGR normalizado a [-1, 1].
        val plane = width * HEIGHT
        val input = FloatArray(3 * plane)
        for (c in 0 until 3) {
            val from = (2 - c) * plane
            for (p in 0 until plane) input[c * plane + p] = rgb[from + p] * 2f - 1f
        }
        val scores = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, HEIGHT.toLong(), width.toLong())).use { x ->
            session.run(mapOf(session.inputNames.first() to x)).use { r ->
                val t = r.get(0) as OnnxTensor
                val steps = t.info.shape[1].toInt()
                val classes = t.info.shape[2].toInt()
                Triple(t.floatBuffer.let { b -> FloatArray(b.remaining()).also(b::get) }, steps, classes)
            }
        }
        val (probs, steps, classes) = scores
        val text = StringBuilder()
        var last = 0
        var sum = 0f
        var n = 0
        for (s in 0 until steps) {
            var best = 0
            for (c in 1 until classes) if (probs[s * classes + c] > probs[s * classes + best]) best = c
            if (best != 0 && best != last && best < symbols.size) {
                text.append(symbols[best])
                sum += probs[s * classes + best]
                n++
            }
            last = best
        }
        return text.toString().trim() to if (n == 0) 0f else sum / n
    }

    override fun close() = session.close()

    companion object {
        private const val HEIGHT = 48

        fun load(model: File, dictionary: File, threads: Int): PaddleRecognizer {
            val symbols = listOf("") + parseDictionary(dictionary.readText()) + " "
            require(symbols.size > 2) { "diccionario de PaddleOCR vacío" }
            val (session, _) = Sessions.open(model, threads, Acceleration.CPU)
            return PaddleRecognizer(OrtEnvironment.getEnvironment(), session, symbols)
        }

        /** Los caracteres de `PostProcess.character_dict` del inference.yml de PaddleOCR. */
        fun parseDictionary(yml: String): List<String> {
            val out = mutableListOf<String>()
            var inside = false
            for (line in yml.lines()) {
                if (line.trimEnd() == "  character_dict:") {
                    inside = true
                    continue
                }
                if (!inside) continue
                // Un carácter en blanco que algún editor quitó: se deja vacío para no descolocar los demás.
                if (line.trimEnd() == "  -") {
                    out += ""
                    continue
                }
                if (!line.startsWith("  - ")) break
                out += unquote(line.substring(4))
            }
            return out
        }

        /** Escalar de YAML: 'a', "a" (con escapes) o tal cual. */
        private fun unquote(v: String): String = when {
            v.length >= 2 && v.startsWith("'") && v.endsWith("'") -> v.substring(1, v.length - 1).replace("''", "'")
            v.length >= 2 && v.startsWith("\"") && v.endsWith("\"") -> {
                val body = v.substring(1, v.length - 1)
                val sb = StringBuilder()
                var i = 0
                while (i < body.length) {
                    val ch = body[i]
                    if (ch == '\\' && i + 1 < body.length) {
                        when (val e = body[i + 1]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); '"' -> sb.append('"'); '\\' -> sb.append('\\')
                            'u' -> if (i + 6 <= body.length) {
                                sb.append(body.substring(i + 2, i + 6).toInt(16).toChar()); i += 4
                            }
                            else -> sb.append(e)
                        }
                        i += 2
                    } else {
                        sb.append(ch)
                        i++
                    }
                }
                sb.toString()
            }
            else -> v
        }

        /** Columna vertical: bastante más alta que ancha (el mismo criterio que PaddleOCR). */
        fun isVertical(box: Box) = box.height >= box.width * 1.5

        /** [crop] girado 90° a la izquierda: lo de arriba queda a la izquierda. */
        internal fun rotateLeft(image: PixelImage, crop: Box): PixelImage {
            val w = crop.height
            val h = crop.width
            val out = IntArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                out[y * w + x] = image.argb[(crop.top + x) * image.width + crop.right - 1 - y]
            }
            return PixelImage(w, h, out)
        }
    }
}

/** Lee cada línea de un bloque (chino, coreano) antes de unirlas. */
fun interface LineReader {
    /** Texto de la línea, o null para quedarse con el del detector. */
    fun read(image: PixelImage, line: DetectedText): String?
}

/** [LineReader] con PaddleOCR: sólo si está bastante seguro (si no, el texto de ML Kit). */
fun PaddleRecognizer.asLineReader(minConfidence: Float = 0.6f) = LineReader { image, line ->
    val (text, confidence) = read(image, line.box) ?: return@LineReader null
    text.takeIf { confidence >= minConfidence && it.isNotBlank() }
}

