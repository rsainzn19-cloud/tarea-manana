package com.mangatraductor.core

/**
 * Traducción con Qwen 3.5 funcionando dentro del móvil ([LocalLlm]): sin
 * internet y sin clave. Recibe la memoria de la historia (nombres y últimas
 * frases) para mantener los nombres y el tono entre páginas.
 */
class QwenTranslator(
    private val llm: LocalLlm,
    targetLanguage: String,
    private val source: SourceLanguage = SourceLanguage.JAPANESE,
    private val onProgress: (String) -> Unit = {},
) : Translator {

    private val language = LANGUAGE_NAMES[targetLanguage] ?: targetLanguage
    private val system = """
        You are a professional ${source.comic} translator. Translate ${source.englishName} speech bubbles into natural, casual $language, like a published ${source.comic}.
        The text comes from OCR and may have small mistakes: fix them from context. The bubbles are in reading order: they are a conversation, so make them follow on from each other.
        Answer only with one line per bubble, "number: translation", with the same numbers, and nothing else.
    """.trimIndent()

    override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> {
        // Memoria corta: cada palabra de la petición hay que leerla en el móvil.
        val memory = story?.describe(maxLines = MEMORY_LINES).orEmpty()
        val chunks = texts.chunked(MAX_BUBBLES)
        var done = 0
        return try {
            chunks.flatMap { chunk ->
                translateChunk(chunk, memory, done, texts.size).also { done += chunk.size }
            }
        } catch (e: Exception) {
            // Errores de ONNX Runtime (p. ej. falta de memoria): se usa la traducción de reserva.
            throw TranslationException("Qwen falló (${e.message ?: e.javaClass.simpleName}).", e)
        }
    }

    private fun translateChunk(texts: List<String>, memory: String, before: Int, total: Int): List<String> {
        val memoryBlock = if (memory.isEmpty()) "" else "Memory of the story (earlier pages; keep the same names):\n$memory\n\n"
        val user = memoryBlock + "Bubbles on this page, in reading order:\n" + NumberedLines.format(texts)
        val budget = (texts.sumOf { it.length * 4 + 12 } + 24).coerceAtMost(MAX_NEW_TOKENS)
        var shown = -1
        val reply = llm.chat(system, user, budget) { partial ->
            val lines = partial.count { it == '\n' }.coerceAtMost(texts.size)
            if (lines != shown) {
                shown = lines
                onProgress("Qwen traduciendo… ${before + lines}/$total")
            }
            // Si se enrolla (una línea larguísima o más líneas de la cuenta), se corta.
            partial.lineSequence().none { it.length > MAX_LINE } && partial.count { it == '\n' } <= texts.size + 1
        }
        onProgress("Qwen traduciendo… ${before + texts.size}/$total")
        return NumberedLines.parse(reply, texts.size)
    }

    companion object {
        const val MEMORY_LINES = 10
        const val MAX_BUBBLES = 20
        const val MAX_NEW_TOKENS = 1500
        private const val MAX_LINE = 400
    }
}
