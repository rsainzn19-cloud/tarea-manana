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

/** Traduce los textos de una página (en orden de lectura). */
interface Translator {
    /** [pageJpeg] es la página entera, por si el motor puede usar el contexto visual. */
    fun translate(texts: List<String>, pageJpeg: ByteArray?): List<String>
}

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

val LANGUAGE_NAMES = mapOf("en" to "English", "es" to "Spanish", "pt" to "Portuguese", "fr" to "French")

/**
 * Traducción con Claude: toda la página de una vez, viendo la imagen, para
 * entender quién habla y el tono, y corregir errores del OCR.
 */
class ClaudeTranslator(
    apiKey: String,
    targetLanguage: String = "en",
    private val model: String = "claude-opus-5",
    private val effort: BetaOutputConfig.Effort = BetaOutputConfig.Effort.MEDIUM,
    baseUrl: String? = null,
) : Translator {

    private val language = LANGUAGE_NAMES[targetLanguage] ?: targetLanguage
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }
        .build()

    override fun translate(texts: List<String>, pageJpeg: ByteArray?): List<String> {
        if (texts.isEmpty()) return emptyList()

        val numbered = texts.withIndex().joinToString("\n") { (i, t) -> "$i: $t" }
        val content = buildList {
            if (pageJpeg != null) {
                add(BetaContentBlockParam.ofImage(
                    BetaImageBlockParam.builder()
                        .source(BetaBase64ImageSource.builder()
                            .mediaType(BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                            .data(Base64.getEncoder().encodeToString(pageJpeg))
                            .build())
                        .build()
                ))
            }
            add(BetaContentBlockParam.ofText("Japanese text on this page, in reading order:\n$numbered"))
        }

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT.replace("{language}", language))
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
        val byId = ObjectMapper().readTree(json)["translations"]
            .associate { it["id"].asInt() to it["text"].asText() }
        // Si faltara algún id se queda vacío en vez de desordenarse.
        return texts.indices.map { byId[it] ?: "" }
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are a professional manga translator and typesetter.
            You receive the OCR'd Japanese text of every speech bubble / caption on one
            manga page (numbered in reading order) and, when available, the page image.

            Translate each item into natural, fluent {language} as a published
            localization would:
            - Keep each character's voice and tone (casual, polite, rough, cute...).
            - Use the page image to work out who is speaking and what is going on.
            - The OCR can contain mistakes; silently fix obvious ones using context.
            - Sound effects: give a short {language} equivalent (e.g. ドキドキ -> "Ba-dump").
            - Keep translations concise: they must fit inside the original bubble.
            - Return exactly one translation per id, same ids as the input.
        """.trimIndent()

        private val OUTPUT_SCHEMA: BetaJsonOutputFormat.Schema = BetaJsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(mapOf(
                "translations" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "id" to mapOf("type" to "integer"),
                            "text" to mapOf("type" to "string"),
                        ),
                        "required" to listOf("id", "text"),
                        "additionalProperties" to false,
                    ),
                ),
            )))
            .putAdditionalProperty("required", JsonValue.from(listOf("translations")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}
