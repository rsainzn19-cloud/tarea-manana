package com.mangatraductor.core

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Modelo de lenguaje que funciona dentro del móvil con ONNX Runtime (el mismo
 * motor que manga-ocr). Pensado para Qwen 3.5 en la exportación de
 * onnx-community: `embed_tokens` (texto → vectores) y un decodificador que
 * guarda su estado entre pasos (caché de atención y estado de las capas
 * lineales). Las formas del estado se leen del propio modelo.
 */
class LocalLlm private constructor(
    private val env: OrtEnvironment,
    private val embed: OrtSession?,
    private val decoder: OrtSession,
    val tokenizer: BpeTokenizer,
) : AutoCloseable {

    private val inputs: Map<String, NodeInfo> = decoder.inputInfo
    private val pastNames = inputs.keys.filter { it.startsWith("past_") }
    private val usesEmbeds = "inputs_embeds" in inputs
    private val positionRank = (inputs["position_ids"]?.info as? TensorInfo)?.shape?.size ?: 0
    private val stopIds = listOf("<|im_end|>", "<|endoftext|>").mapNotNull { tokenizer.tokenId(it) }.toSet()
    private val imagePad = tokenizer.tokenId("<|image_pad|>")

    /** ¿Puede ver imágenes? (hace falta además el codificador, [QwenVision]) */
    val acceptsImages: Boolean get() = usesEmbeds && positionRank == 3 && imagePad != null

    /**
     * Conversación de un solo turno con la plantilla de Qwen, sin «pensar»
     * (responde directamente), con una [image] delante del texto si se da.
     * [onText] recibe el texto que lleva generado; si devuelve false se para ahí.
     */
    fun chat(
        system: String,
        user: String,
        maxNewTokens: Int,
        image: ImageEmbedding? = null,
        onText: (String) -> Boolean = { true },
    ): String {
        val prompt = if (image == null) {
            tokenizer.encode(
                "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n" +
                    "<|im_start|>assistant\n<think>\n\n</think>\n\n",
            )
        } else {
            require(acceptsImages) { "este modelo no ve imágenes" }
            // Como la plantilla de Qwen: <|vision_start|>, un <|image_pad|> por vector y <|vision_end|>.
            tokenizer.encode("<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n") +
                tokenizer.encode("<|vision_start|>") + IntArray(image.tokens) { imagePad!! } + tokenizer.encode("<|vision_end|>") +
                tokenizer.encode("$user<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n")
        }
        val out = generate(prompt, maxNewTokens, image) { ids, n -> onText(tokenizer.decode(ids, n)) }
        return tokenizer.decode(out)
    }

    /**
     * Genera con búsqueda voraz (siempre la pieza más probable): lo más fiel
     * para traducir. Los <|image_pad|> de [prompt] se sustituyen por los
     * vectores de [image], en orden.
     */
    fun generate(prompt: IntArray, maxNewTokens: Int, image: ImageEmbedding? = null, onToken: (IntArray, Int) -> Boolean): IntArray {
        require(prompt.isNotEmpty())
        val (positions, after) = ropePositions(prompt, imagePad ?: -1, image)
        val feed = image?.let { ImageFeed(it) }
        val state = HashMap<String, OnnxTensor>()
        try {
            for (name in pastNames) state[name] = zeros(inputs.getValue(name).info as TensorInfo)
            // Lectura del texto de entrada por trozos (menos memoria); la última pieza va aparte
            // para que sólo haga falta copiar las probabilidades de un paso.
            var start = 0
            fun slice(from: Int, to: Int) = LongArray(3 * (to - from)) { positions[(it / (to - from)) * prompt.size + from + it % (to - from)] }
            while (start < prompt.size - 1) {
                val end = minOf(start + PREFILL_CHUNK, prompt.size - 1)
                step(prompt.copyOfRange(start, end), slice(start, end), end, state, feed, wantLogits = false)
                start = end
            }
            val out = IntArray(maxNewTokens)
            var count = 0
            var logits = step(intArrayOf(prompt.last()), slice(prompt.size - 1, prompt.size), prompt.size, state, feed, wantLogits = true)!!
            var position = after
            while (count < maxNewTokens) {
                val next = argmax(logits)
                if (next in stopIds) break
                out[count++] = next
                if (!onToken(out, count) || count == maxNewTokens) break
                logits = step(intArrayOf(next), LongArray(3) { position }, prompt.size + count, state, null, wantLogits = true)!!
                position++
            }
            return out.copyOf(count)
        } finally {
            state.values.forEach { it.close() }
        }
    }

    /** Los vectores de la imagen que faltan por meter en la conversación. */
    private class ImageFeed(val image: ImageEmbedding) {
        var next = 0
    }

    /**
     * Un paso del decodificador con [ids] nuevos en las posiciones [positions]
     * (3 ejes seguidos); [total] es cuántas piezas lleva leídas con estas.
     * Actualiza [state] y devuelve las probabilidades de la última.
     */
    private fun step(
        ids: IntArray,
        positions: LongArray,
        total: Int,
        state: MutableMap<String, OnnxTensor>,
        feed: ImageFeed?,
        wantLogits: Boolean,
    ): FloatArray? {
        val n = ids.size
        val owned = ArrayList<OnnxTensor>()
        try {
            val idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { ids[it].toLong() }), longArrayOf(1, n.toLong()))
            owned += idsTensor
            val feeds = HashMap<String, OnnxTensor>(state)
            if (usesEmbeds) {
                val embedded = embed!!.run(mapOf(embed.inputNames.first() to idsTensor))
                var vectors = embedded.get(0) as OnnxTensor
                owned += vectors
                if (feed != null && ids.any { it == imagePad }) vectors = withImage(vectors, ids, feed).also { owned += it }
                feeds["inputs_embeds"] = vectors
            } else {
                feeds["input_ids"] = idsTensor
            }
            feeds["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(total) { 1L }), longArrayOf(1, total.toLong()))
                .also { owned += it }
            if (positionRank > 0) {
                // Qwen 3.5 usa posiciones en 3 ejes (orden, alto y ancho en la imagen); para texto son iguales.
                val tensor = if (positionRank == 3) {
                    OnnxTensor.createTensor(env, LongBuffer.wrap(positions), longArrayOf(3, 1, n.toLong()))
                } else {
                    OnnxTensor.createTensor(env, LongBuffer.wrap(positions.copyOf(n)), longArrayOf(1, n.toLong()))
                }
                feeds["position_ids"] = tensor.also { owned += it }
            }

            val result = decoder.run(feeds)
            // El nuevo estado sustituye al anterior (no se cierra el resultado entero: se queda con él).
            for (name in pastNames) {
                val present = result.get(presentName(name)).get() as OnnxTensor
                state.put(name, present)?.close()
            }
            val logitsTensor = result.get("logits").get() as OnnxTensor
            try {
                if (!wantLogits) return null
                val all = logitsTensor.floatBuffer
                val vocab = logitsTensor.info.shape.last().toInt()
                val last = FloatArray(vocab)
                all.position((n - 1) * vocab)
                all.get(last)
                return last
            } finally {
                logitsTensor.close()
            }
        } finally {
            owned.forEach { it.close() }
        }
    }

    /** Los vectores de [ids] con los de la imagen en el lugar de cada <|image_pad|>. */
    private fun withImage(vectors: OnnxTensor, ids: IntArray, feed: ImageFeed): OnnxTensor {
        val buffer = vectors.floatBuffer
        val values = FloatArray(buffer.remaining()).also(buffer::get)
        val hidden = values.size / ids.size
        val image = feed.image
        require(image.hidden == hidden) { "la imagen no es de este modelo (${image.hidden} en vez de $hidden)" }
        for (i in ids.indices) {
            if (ids[i] != imagePad || feed.next >= image.tokens) continue
            System.arraycopy(image.features, feed.next++ * hidden, values, i * hidden, hidden)
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1, ids.size.toLong(), hidden.toLong()))
    }

    private fun zeros(info: TensorInfo): OnnxTensor {
        // Lote = 1; la longitud de lo ya leído empieza en 0.
        val shape = LongArray(info.shape.size) { i -> if (info.shape[i] >= 0) info.shape[i] else if (i == 0) 1 else 0 }
        val bytes = shape.fold(1L) { acc, d -> acc * d } * elementSize(info.type)
        // Un búfer vacío también necesita una dirección válida: se reserva al menos un poco.
        val buffer = ByteBuffer.allocateDirect(maxOf(bytes, 16L).toInt()).order(ByteOrder.nativeOrder())
        buffer.limit(bytes.toInt())
        return OnnxTensor.createTensor(env, buffer, shape, info.type)
    }

    override fun close() {
        decoder.close()
        embed?.close()
    }

    companion object {
        /** Piezas que se leen a la vez al procesar la petición. */
        const val PREFILL_CHUNK = 64

        /**
         * Carga el modelo. [decoder] y [embed] son los .onnx (sus .onnx_data van
         * en la misma carpeta); [threads] hilos del procesador.
         */
        fun load(tokenizer: File, decoder: File, embed: File?, threads: Int): LocalLlm {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val tok = BpeTokenizer.load(tokenizer)
            val embedSession = embed?.let { env.createSession(it.path, options) }
            val decoderSession = try {
                env.createSession(decoder.path, options)
            } catch (e: Exception) {
                embedSession?.close()
                throw e
            }
            return LocalLlm(env, embedSession, decoderSession, tok)
        }

        /**
         * Posiciones de cada pieza en los 3 ejes de Qwen (orden, fila, columna),
         * seguidas eje tras eje, y la siguiente posición libre. El texto avanza
         * igual en los 3; los vectores de la imagen comparten el orden y llevan
         * su fila y su columna, y después el texto sigue tras el lado más largo.
         */
        internal fun ropePositions(prompt: IntArray, imagePad: Int, image: ImageEmbedding?): Pair<LongArray, Long> {
            val n = prompt.size
            val out = LongArray(3 * n)
            var next = 0L
            var i = 0
            while (i < n) {
                if (image != null && prompt[i] == imagePad) {
                    val base = next
                    var k = 0
                    while (i < n && prompt[i] == imagePad) {
                        out[i] = base
                        out[n + i] = base + k / image.gridWidth
                        out[2 * n + i] = base + k % image.gridWidth
                        i++
                        k++
                    }
                    next = base + maxOf(image.gridWidth, image.gridHeight)
                } else {
                    out[i] = next
                    out[n + i] = next
                    out[2 * n + i] = next
                    next++
                    i++
                }
            }
            return out to next
        }

        internal fun presentName(past: String) =
            if (past.startsWith("past_key_values")) past.replaceFirst("past_key_values", "present")
            else past.replaceFirst("past_", "present_")

        private fun elementSize(type: OnnxJavaType): Int = when (type) {
            OnnxJavaType.FLOAT, OnnxJavaType.INT32 -> 4
            OnnxJavaType.FLOAT16, OnnxJavaType.BFLOAT16, OnnxJavaType.INT16 -> 2
            OnnxJavaType.DOUBLE, OnnxJavaType.INT64 -> 8
            else -> 1
        }

        private fun argmax(values: FloatArray): Int {
            var best = 0
            for (i in 1 until values.size) if (values[i] > values[best]) best = i
            return best
        }
    }
}
