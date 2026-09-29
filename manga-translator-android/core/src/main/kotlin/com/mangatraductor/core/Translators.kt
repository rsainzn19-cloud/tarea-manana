package com.mangatraductor.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.MessageCreateParams
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64

/** Tipo de texto: se rotula distinto (las onomatopeyas, más grandes y con otra letra). */
enum class TextKind { DIALOGUE, NARRATION, SFX }

/** Una traducción, con el original corregido y el tipo de texto si la IA los da. */
class Translation(val text: String, val original: String? = null, val kind: TextKind? = null)

/** Los textos de una página (en orden de lectura) y la página en JPEG, si el motor la ve. */
class PageTexts(val texts: List<String>, val jpeg: ByteArray?)

/** Traduce los textos de una página (en orden de lectura). */
interface Translator {
    /**
     * [pageJpeg] es la página entera, por si el motor puede usar el contexto
     * visual. [story] es la memoria de la historia (páginas anteriores): los
     * motores que la entienden la usan y la actualizan con esta página.
     */
    fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext? = null): List<String>

    /**
     * Varias páginas seguidas. Los motores que ven la página (Claude, Gemini)
     * las traducen en una sola petición, con más contexto, y devuelven también
     * el original corregido y el tipo de cada texto; el resto, de una en una.
     */
    fun translatePages(pages: List<PageTexts>, story: StoryContext? = null): List<List<Translation>> =
        pages.map { page -> translate(page.texts, page.jpeg, story).map { Translation(it) } }
}

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

val LANGUAGE_NAMES = mapOf("en" to "English", "es" to "Spanish", "pt" to "Portuguese", "fr" to "French")

/** Instrucciones comunes a los motores de IA que ven la página (Claude, Gemini). */
internal object ComicPrompt {
    fun system(source: SourceLanguage, language: String) = """
        You are a professional ${source.comic} translator and typesetter.
        You receive the OCR'd ${source.englishName} text of every speech bubble / caption on one
        ${source.comic} page (or on a few consecutive pages), numbered in reading order, and usually
        the page images. In each image, every item is marked with a small red tag with its number,
        next to its text. The numbers continue from one page to the next.

        Translate each item into natural, fluent $language as a published localization would:
        - First read the whole page: work out who is speaking to whom and what is going on, so
          that the lines follow on from each other and make sense together.
        - Keep each character's voice and tone (casual, polite, rough, cute...).
        - The OCR can contain mistakes or miss characters: check every item against its bubble in
          the image and translate what is really written there.
        - Sound effects: give a short $language equivalent (e.g. a heartbeat -> "Ba-dump").
        - If an item is not real text (OCR noise from the drawing, a watermark, a website logo),
          return an empty text for it.
        - Keep translations concise: they must fit inside the original bubble.
        - Return exactly one translation per id, same ids as the input.
        - For each item also return "original": the ${source.englishName} text really written in that
          bubble (the OCR text, corrected by reading the image), and "kind": "dialogue" (speech and
          thought bubbles), "narration" (captions, text boxes) or "sfx" (sound effects).
    """.trimIndent()

    fun user(source: SourceLanguage, pages: List<PageTexts>, story: StoryContext?): String {
        if (pages.size == 1) {
            return StoryPrompt.context(story) + "${source.englishName} text on this page, in reading order:\n" +
                NumberedLines.format(pages[0].texts)
        }
        var first = 0
        return StoryPrompt.context(story) + pages.withIndex().joinToString("\n\n") { (i, page) ->
            "Page ${i + 1}, ${source.englishName} text in reading order:\n" + NumberedLines.format(page.texts, first).also {
                first += page.texts.size
            }
        }
    }

    /** Reparte la lista de todas las páginas en una por página. */
    fun <T> split(all: List<T>, pages: List<PageTexts>): List<List<T>> {
        var start = 0
        return pages.map { page -> all.subList(start, start + page.texts.size).also { start += page.texts.size } }
    }
}

/** Instrucciones y respuesta comunes a los motores de IA que llevan la memoria de la historia. */
internal object StoryPrompt {
    val INSTRUCTIONS = """
        Story memory: you may receive "Story so far", a glossary of names/terms and the
        previous lines already translated from earlier pages of the same story.
        - Use them to keep the story coherent: who is who, how characters talk to each
          other, running jokes, and translate names and recurring terms exactly as in
          the glossary.
        - Also return "summary": an updated summary of the whole story so far in English
          (at most 120 words), including what happens on this page.
        - And "glossary": character names, places and special terms that appear on this
          page, with the translation you used (empty list if none).
    """.trimIndent()

    /** Bloque con la memoria para el mensaje, o "" si no hay nada todavía. */
    fun context(story: StoryContext?): String {
        val text = story?.describe().orEmpty()
        return if (text.isEmpty()) "" else "Memory of the story (earlier pages):\n$text\n\n"
    }

    /**
     * Lee {"translations":[{id,original,kind,text}], "summary", "glossary":[{original,translation}]},
     * actualiza [story] y devuelve [count] traducciones en orden (vacías las que falten).
     */
    fun read(json: String, count: Int, story: StoryContext?): List<Translation> {
        val root = ObjectMapper().readTree(json)
        val byId = root.path("translations").associate { item ->
            item.path("id").asInt(-1) to Translation(
                text = item.path("text").asText(""),
                original = item.path("original").asText("").ifBlank { null },
                kind = when (item.path("kind").asText("").lowercase()) {
                    "sfx" -> TextKind.SFX
                    "narration" -> TextKind.NARRATION
                    "dialogue" -> TextKind.DIALOGUE
                    else -> null
                },
            )
        }
        val glossary = root.path("glossary").associate { it.path("original").asText("") to it.path("translation").asText("") }
        story?.update(root.path("summary").asText(""), glossary)
        return (0 until count).map { byId[it] ?: Translation("") }
    }
}

/**
 * Traducción con Claude: toda la página de una vez, viendo la imagen, para
 * entender quién habla y el tono, y corregir errores del OCR.
 */
class ClaudeTranslator(
    apiKey: String,
    targetLanguage: String = "en",
    private val source: SourceLanguage = SourceLanguage.JAPANESE,
    private val model: String = "claude-opus-5",
    private val effort: BetaOutputConfig.Effort = BetaOutputConfig.Effort.MEDIUM,
    baseUrl: String? = null,
) : Translator {

    private val language = LANGUAGE_NAMES[targetLanguage] ?: targetLanguage
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> =
        translatePages(listOf(PageTexts(texts, pageJpeg)), story).single().map { it.text }

    override fun translatePages(pages: List<PageTexts>, story: StoryContext?): List<List<Translation>> {
        val total = pages.sumOf { it.texts.size }
        if (total == 0) return pages.map { emptyList() }

        val content = buildList {
            pages.forEachIndexed { i, page ->
                val jpeg = page.jpeg ?: return@forEachIndexed
                if (pages.size > 1) add(BetaContentBlockParam.ofText("Page ${i + 1}:"))
                add(BetaContentBlockParam.ofImage(
                    BetaImageBlockParam.builder()
                        .source(BetaBase64ImageSource.builder()
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .data(Base64.getEncoder().encodeToString(jpeg))
                            .build())
                        .build()
                ))
            }
            add(BetaContentBlockParam.ofText(ComicPrompt.user(source, pages, story)))
        }

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(ComicPrompt.system(source, language) + "\n\n" + StoryPrompt.INSTRUCTIONS)
            .addUserMessageOfBetaContentBlockParams(content)
            .thinking(BetaThinkingConfigAdaptive.builder().build())
            .outputConfig(BetaOutputConfig.builder()
                .effort(effort)
                .format(BetaJsonOutputFormat.builder().schema(OUTPUT_SCHEMA).build())
                .build())
            // Si el modelo rechaza la petición, la API la repite sola con el
            // modelo de respaldo recomendado.
            .addBeta("server-side-fallback-2026-07-01")
            .fallbacksDefault()
            .build()

        val response = try {
            client.beta().messages().create(params)
        } catch (e: UnauthorizedException) {
            throw TranslationException("La clave de Anthropic no es válida.", e)
        } catch (e: AnthropicServiceException) {
            throw TranslationException("Error de la API de Claude (${e.statusCode()}): ${e.message}", e)
        } catch (e: Exception) {
            throw TranslationException("No se pudo conectar con Claude: ${e.message}", e)
        }

        val stop = response.stopReason().orElse(null)
        if (stop != BetaStopReason.END_TURN) {
            throw TranslationException("Claude no terminó la traducción (motivo: $stop).")
        }
        val json = response.content().firstNotNullOfOrNull { it.text().orElse(null) }?.text()
            ?: throw TranslationException("Claude no devolvió texto.")
        // Si faltara algún id se queda vacío en vez de desordenarse.
        return ComicPrompt.split(StoryPrompt.read(json, total, story), pages)
    }

    companion object {
        private val OUTPUT_SCHEMA: BetaJsonOutputFormat.Schema = BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(mapOf(
                "translations" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "id" to mapOf("type" to "integer"),
                            "original" to mapOf("type" to "string"),
                            "kind" to mapOf("type" to "string", "enum" to listOf("dialogue", "narration", "sfx")),
                            "text" to mapOf("type" to "string"),
                        ),
                        "required" to listOf("id", "original", "kind", "text"),
                        "additionalProperties" to false,
                    ),
                ),
                "summary" to mapOf("type" to "string"),
                "glossary" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "original" to mapOf("type" to "string"),
                            "translation" to mapOf("type" to "string"),
                        ),
                        "required" to listOf("original", "translation"),
                        "additionalProperties" to false,
                    ),
                ),
            )))
            .putAdditionalProperty("required", JsonValue.from(listOf("translations", "summary", "glossary")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
