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

    /**
     * Conversación de un solo turno con la plantilla de Qwen, sin «pensar»
     * (responde directamente). [onText] recibe el texto que lleva generado;
     * si devuelve false se para ahí.
     */
    fun chat(system: String, user: String, maxNewTokens: Int, onText: (String) -> Boolean = { true }): String {
        val prompt = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"
        val out = generate(tokenizer.encode(prompt), maxNewTokens) { ids, n -> onText(tokenizer.decode(ids, n)) }
        return tokenizer.decode(out)
    }

    /** Genera con búsqueda voraz (siempre la pieza más probable): lo más fiel para traducir. */
    fun generate(prompt: IntArray, maxNewTokens: Int, onToken: (IntArray, Int) -> Boolean): IntArray {
        require(prompt.isNotEmpty())
        val state = HashMap<String, OnnxTensor>()
        try {
            for (name in pastNames) state[name] = zeros(inputs.getValue(name).info as TensorInfo)
            var position = 0
            // Lectura del texto de entrada por trozos (menos memoria); la última pieza va aparte
            // para que sólo haga falta copiar las probabilidades de un paso.
            var start = 0
            while (start < prompt.size - 1) {
                val end = minOf(start + PREFILL_CHUNK, prompt.size - 1)
                step(prompt.copyOfRange(start, end), position, state, wantLogits = false)
                position += end - start
                start = end
            }
            val out = IntArray(maxNewTokens)
            var count = 0
            var next = prompt.last()
            while (count < maxNewTokens) {
                val logits = step(intArrayOf(next), position, state, wantLogits = true)!!
                position++
                next = argmax(logits)
                if (next in stopIds) break
                out[count++] = next
                if (!onToken(out, count)) break
            }
            return out.copyOf(count)
        } finally {
            state.values.forEach { it.close() }
        }
    }

    /** Un paso del decodificador con [ids] nuevos; actualiza [state] y devuelve las probabilidades del último. */
    private fun step(ids: IntArray, position: Int, state: MutableMap<String, OnnxTensor>, wantLogits: Boolean): FloatArray? {
        val n = ids.size
        val owned = ArrayList<OnnxTensor>()
        try {
            val idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(n) { ids[it].toLong() }), longArrayOf(1, n.toLong()))
            owned += idsTensor
            val feeds = HashMap<String, OnnxTensor>(state)
            if (usesEmbeds) {
                val embedded = embed!!.run(mapOf(embed.inputNames.first() to idsTensor))
                val vectors = embedded.get(0) as OnnxTensor
                owned += vectors
                feeds["inputs_embeds"] = vectors
            } else {
                feeds["input_ids"] = idsTensor
            }
            val total = position + n
            feeds["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(total) { 1L }), longArrayOf(1, total.toLong()))
                .also { owned += it }
            if (positionRank > 0) {
                // Qwen 3.5 usa posiciones en 3 ejes (texto, alto y ancho de imagen); para texto son iguales.
                val axes = if (positionRank == 3) 3 else 1
                val shape = if (positionRank == 3) longArrayOf(3, 1, n.toLong()) else longArrayOf(1, n.toLong())
                val values = LongArray(axes * n) { (position + it % n).toLong() }
                feeds["position_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape).also { owned += it }
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
