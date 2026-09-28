package com.mangatraductor.core

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Tokenizador BPE a nivel de bytes (el de Qwen) leído del `tokenizer.json` de
 * Hugging Face. Da los mismos números que la librería `tokenizers`.
 *
 * El archivo de Qwen 3.5 pesa ~19 MB (248 000 piezas): se lee en streaming y
 * las reglas de unión se guardan en una tabla compacta para no llenar la
 * memoria de Java del móvil.
 */
class BpeTokenizer private constructor(
    private val tokens: Array<String?>,
    private val byteIds: IntArray,
    private val merges: PairTable,
    private val added: Map<String, Int>,
    private val special: Set<Int>,
    private val pieces: Pattern,
    private val nfc: Boolean,
) {
    private val addedPattern: Pattern? = if (added.isEmpty()) null else
        Pattern.compile(added.keys.sortedByDescending { it.length }.joinToString("|") { Pattern.quote(it) })
    private val cache = HashMap<String, IntArray>()

    /** Número de un token especial (p. ej. `<|im_end|>`), o null si no existe. */
    fun tokenId(content: String): Int? = added[content]

    fun encode(text: String): IntArray {
        val out = IntList()
        var start = 0
        val m = addedPattern?.matcher(text)
        while (m != null && m.find()) {
            encodeText(text.substring(start, m.start()), out)
            out.add(added.getValue(m.group()))
            start = m.end()
        }
        encodeText(text.substring(start), out)
        return out.toArray()
    }

    /** Texto de [ids]; los tokens especiales (`<|im_end|>`...) se omiten. */
    fun decode(ids: IntArray, count: Int = ids.size): String {
        val bytes = ByteArrayOutputStream()
        for (i in 0 until count) {
            val id = ids[i]
            if (id in special) continue
            val token = tokens.getOrNull(id) ?: continue
            if (added.containsKey(token)) {
                bytes.write(token.toByteArray(Charsets.UTF_8))
            } else {
                for (c in token) {
                    val b = if (c.code < CHAR_TO_BYTE.size) CHAR_TO_BYTE[c.code] else -1
                    if (b >= 0) bytes.write(b)
                }
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun encodeText(raw: String, out: IntList) {
        if (raw.isEmpty()) return
        val text = if (nfc) Normalizer.normalize(raw, Normalizer.Form.NFC) else raw
        val m = pieces.matcher(text)
        while (m.find()) {
            val piece = m.group()
            if (piece.isEmpty()) continue
            val ids = cache[piece] ?: bpe(piece).also {
                if (cache.size > CACHE_SIZE) cache.clear()
                cache[piece] = it
            }
            for (id in ids) out.add(id)
        }
    }

    /** Une pares de piezas por orden de prioridad hasta que no quede ninguna regla aplicable. */
    private fun bpe(piece: String): IntArray {
        val utf8 = piece.toByteArray(Charsets.UTF_8)
        var word = IntArray(utf8.size) {
            val b = utf8[it].toInt() and 0xff
            byteIds[b].also { id -> require(id >= 0) { "el byte $b no está en el vocabulario" } }
        }
        var size = word.size
        while (size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestMerged = -1
            var first = -1
            var second = -1
            for (i in 0 until size - 1) {
                val v = merges.get(word[i], word[i + 1])
                if (v != PairTable.MISSING && (v ushr 32).toInt() < bestRank) {
                    bestRank = (v ushr 32).toInt()
                    bestMerged = v.toInt()
                    first = word[i]
                    second = word[i + 1]
                }
            }
            if (bestMerged < 0) break
            // Se unen todas las apariciones de ese par, de izquierda a derecha.
            val next = IntArray(size)
            var n = 0
            var i = 0
            while (i < size) {
                if (i < size - 1 && word[i] == first && word[i + 1] == second) {
                    next[n++] = bestMerged
                    i += 2
                } else {
                    next[n++] = word[i++]
                }
            }
            word = next
            size = n
        }
        return word.copyOf(size)
    }

    companion object {
        private const val CACHE_SIZE = 20_000

        /** Mapa de GPT-2 entre bytes y caracteres imprimibles (`bytes_to_unicode`). */
        private val BYTE_TO_CHAR = IntArray(256)
        private val CHAR_TO_BYTE: IntArray

        init {
            val printable = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toSet()
            var extra = 0
            for (b in 0 until 256) BYTE_TO_CHAR[b] = if (b in printable) b else 256 + extra++
            CHAR_TO_BYTE = IntArray(256 + extra) { -1 }
            for (b in 0 until 256) CHAR_TO_BYTE[BYTE_TO_CHAR[b]] = b
        }

        /** Espacios en blanco de Unicode: `\s` no significa lo mismo en Java, Android y Rust. */
        private const val WHITESPACE = "\\t\\n\\x0B\\f\\r\\x20\\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"

        /** Expresión del pretokenizador de GPT-2 (si el tokenizer.json no trae una propia). */
        private const val GPT2_PATTERN = """'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+(?!\S)|\s+"""

        fun load(file: File): BpeTokenizer = file.inputStream().buffered(1 shl 16).use { load(it) }

        fun load(input: InputStream): BpeTokenizer {
            val mapper = ObjectMapper()
            val added = LinkedHashMap<String, Int>()
            val special = HashSet<Int>()
            var normalizer: JsonNode? = null
            var preTokenizer: JsonNode? = null
            var model: ModelData? = null
            mapper.factory.createParser(input).use { p ->
                check(p.nextToken() == JsonToken.START_OBJECT) { "tokenizer.json no válido" }
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    val field = p.currentName()
                    p.nextToken()
                    when (field) {
                        "added_tokens" -> while (p.nextToken() == JsonToken.START_OBJECT) {
                            val t: JsonNode = mapper.readTree(p)
                            added[t["content"].asText()] = t["id"].asInt()
                            if (t["special"]?.asBoolean() == true) special += t["id"].asInt()
                        }
                        "normalizer" -> normalizer = mapper.readTree(p)
                        "pre_tokenizer" -> preTokenizer = mapper.readTree(p)
                        "model" -> model = readModel(p)
                        else -> p.skipChildren()
                    }
                }
            }
            val data = model ?: error("tokenizer.json sin modelo")
            val size = maxOf(data.vocab.values.maxOrNull() ?: 0, added.values.maxOrNull() ?: 0) + 1
            val tokens = arrayOfNulls<String>(size)
            for ((token, id) in data.vocab) tokens[id] = token
            for ((token, id) in added) tokens[id] = token

            val byteIds = IntArray(256) { b -> data.vocab[BYTE_TO_CHAR[b].toChar().toString()] ?: -1 }
            // Uniones leídas antes que el vocabulario (no pasa en los tokenizer.json normales).
            data.pending?.forEach { (a, b) -> data.addMerge(a, b) }
            val nfc = normalizer?.let { n -> n["type"]?.asText() == "NFC" || n["normalizers"]?.any { it["type"]?.asText() == "NFC" } == true } ?: false
            return BpeTokenizer(tokens, byteIds, data.merges, added, special, Pattern.compile(portable(splitPattern(preTokenizer))), nfc)
        }

        /** Vocabulario y reglas de unión (cada regla se guarda como números según se lee). */
        private class ModelData {
            val vocab = HashMap<String, Int>(300_000)
            val merges = PairTable(1 shl 18)
            var pending: MutableList<Pair<String, String>>? = null
            private var rank = 0L

            fun addMerge(a: String, b: String) {
                val first = vocab[a]
                val second = vocab[b]
                val merged = vocab[a + b]
                if (first != null && second != null && merged != null) {
                    merges.putIfAbsent(first, second, (rank shl 32) or merged.toLong())
                }
                rank++
            }

            fun readMerge(a: String, b: String) {
                if (vocab.isEmpty()) (pending ?: ArrayList<Pair<String, String>>().also { pending = it }) += a to b
                else addMerge(a, b)
            }
        }

        private fun readModel(p: JsonParser): ModelData {
            val data = ModelData()
            val vocab = data.vocab
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                val field = p.currentName()
                val start = p.nextToken()
                when (field) {
                    "type" -> check(p.text == "BPE") { "tokenizador ${p.text} no soportado" }
                    "vocab" -> while (p.nextToken() == JsonToken.FIELD_NAME) {
                        val token = p.currentName()
                        p.nextToken()
                        vocab[token] = p.intValue
                    }
                    "merges" -> while (true) {
                        when (p.nextToken()) {
                            JsonToken.VALUE_STRING -> { // formato antiguo: "a b"
                                val s = p.text
                                val space = s.indexOf(' ', 1)
                                data.readMerge(s.substring(0, space), s.substring(space + 1))
                            }
                            JsonToken.START_ARRAY -> { // formato nuevo: ["a", "b"]
                                p.nextToken()
                                val a = p.text
                                p.nextToken()
                                val b = p.text
                                p.nextToken() // END_ARRAY
                                data.readMerge(a, b)
                            }
                            else -> break
                        }
                    }
                    else -> if (start == JsonToken.START_OBJECT || start == JsonToken.START_ARRAY) p.skipChildren()
                }
            }
            return data
        }

        /** La expresión con la que se trocea el texto antes del BPE. */
        private fun splitPattern(pre: JsonNode?): String {
            val steps = if (pre?.get("type")?.asText() == "Sequence") pre["pretokenizers"].toList() else listOfNotNull(pre)
            steps.firstOrNull { it["type"]?.asText() == "Split" }?.let { return it["pattern"]["Regex"].asText() }
            return GPT2_PATTERN
        }

        /** Cambia `\s` y `\S` por la lista explícita de espacios de Unicode. */
        internal fun portable(regex: String): String {
            val out = StringBuilder()
            var inClass = false
            var i = 0
            while (i < regex.length) {
                val c = regex[i]
                if (c == '\\' && i + 1 < regex.length) {
                    when (val n = regex[i + 1]) {
                        's' -> out.append(if (inClass) WHITESPACE else "[$WHITESPACE]")
                        'S' -> {
                            check(!inClass) { "\\S dentro de [] no soportado" }
                            out.append("[^$WHITESPACE]")
                        }
                        else -> out.append(c).append(n)
                    }
                    i += 2
                    continue
                }
                if (c == '[' && !inClass) inClass = true else if (c == ']' && inClass) inClass = false
                out.append(c)
                i++
            }
            return out.toString()
        }
    }
}

/** Tabla hash compacta (sin objetos) de pares de enteros a un Long. */
internal class PairTable(initialCapacity: Int) {
    private var capacity = Integer.highestOneBit(maxOf(16, initialCapacity - 1)) shl 1
    private var keys = LongArray(capacity) { EMPTY }
    private var values = LongArray(capacity)
    private var size = 0

    fun putIfAbsent(a: Int, b: Int, value: Long) {
        if (size * 2 >= capacity) grow()
        insert((a.toLong() shl 32) or (b.toLong() and 0xffffffffL), value)
    }

    private fun insert(key: Long, value: Long) {
        var i = slot(key)
        while (keys[i] != EMPTY) {
            if (keys[i] == key) return
            i = (i + 1) and (capacity - 1)
        }
        keys[i] = key
        values[i] = value
        size++
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        capacity *= 2
        keys = LongArray(capacity) { EMPTY }
        values = LongArray(capacity)
        size = 0
        for (i in oldKeys.indices) if (oldKeys[i] != EMPTY) insert(oldKeys[i], oldValues[i])
    }

    fun get(a: Int, b: Int): Long {
        val key = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == EMPTY) return MISSING
            i = (i + 1) and (capacity - 1)
        }
    }

    private fun slot(key: Long): Int {
        var h = key * -0x61c8864680b583ebL
        h = h xor (h ushr 29)
        return h.toInt() and (capacity - 1)
    }

    companion object {
        private const val EMPTY = Long.MIN_VALUE
        const val MISSING = -1L
    }
}

/** Lista de enteros que crece sola (sin cajas). */
internal class IntList {
    private var data = IntArray(64)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    fun toArray(): IntArray = data.copyOf(size)
}
