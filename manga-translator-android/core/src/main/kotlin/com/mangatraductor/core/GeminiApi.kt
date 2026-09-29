package com.mangatraductor.core

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Traducción con Gemini en la nube (API de Google), que tiene un plan gratuito:
 * sólo hace falta una clave de Google AI Studio. Ve la página entera y usa la
 * memoria de la historia, como Claude. Si el modelo más nuevo no está
 * disponible para la clave, prueba los anteriores de [models].
 */
class GeminiApiTranslator(
    private val apiKey: String,
    targetLanguage: String = "en",
    private val source: SourceLanguage = SourceLanguage.JAPANESE,
    private val models: List<String> = DEFAULT_MODELS,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) : Translator {

    private val language = LANGUAGE_NAMES[targetLanguage] ?: targetLanguage
    private val mapper = ObjectMapper()

    override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> {
        if (texts.isEmpty()) return emptyList()
        val body = mapper.writeValueAsBytes(requestBody(texts, pageJpeg, story))

        var lastError: TranslationException? = null
        for (model in models) {
            val (code, response) = try {
                post("$baseUrl/models/$model:generateContent", body)
            } catch (e: IOException) {
                throw TranslationException("No se pudo conectar con Gemini: ${e.message}", e)
            }
            when {
                code == 200 -> return read(response, texts.size, story)
                // Modelo no disponible (nombre retirado o no incluido para esta clave): probar el siguiente.
                code == 404 -> lastError = TranslationException("Gemini: el modelo $model no está disponible.")
                else -> throw error(code, response)
            }
        }
        throw lastError ?: TranslationException("Gemini no respondió.")
    }

    private fun requestBody(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): Map<String, Any> {
        val parts = buildList {
            if (pageJpeg != null) {
                add(mapOf("inline_data" to mapOf(
                    "mime_type" to "image/jpeg",
                    "data" to Base64.getEncoder().encodeToString(pageJpeg),
                )))
            }
            add(mapOf("text" to ComicPrompt.user(source, texts, story)))
        }
        return mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to
                ComicPrompt.system(source, language) + "\n\n" + StoryPrompt.INSTRUCTIONS))),
            "contents" to listOf(mapOf("role" to "user", "parts" to parts)),
            "generationConfig" to mapOf(
                "temperature" to 0.4,
                "maxOutputTokens" to 8192,
                "responseMimeType" to "application/json",
                "responseSchema" to RESPONSE_SCHEMA,
            ),
        )
    }

    private fun post(url: String, body: ByteArray): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun read(response: String, count: Int, story: StoryContext?): List<String> {
        val root = mapper.readTree(response)
        root.path("promptFeedback").path("blockReason").asText("").takeIf { it.isNotEmpty() }?.let {
            throw TranslationException("Gemini no quiso traducir esta página (motivo: $it).")
        }
        val candidate = root.path("candidates").path(0)
        val finish = candidate.path("finishReason").asText("")
        // Con "thinking" puede haber partes de razonamiento marcadas con "thought": se ignoran.
        val json = candidate.path("content").path("parts")
            .filterNot { it.path("thought").asBoolean(false) }
            .joinToString("") { it.path("text").asText("") }
        if (json.isBlank() || (finish.isNotEmpty() && finish != "STOP")) {
            throw TranslationException("Gemini no terminó la traducción (motivo: ${finish.ifEmpty { "respuesta vacía" }}).")
        }
        return StoryPrompt.read(json, count, story)
    }

    private fun error(code: Int, response: String): TranslationException {
        val message = try {
            mapper.readTree(response).path("error").path("message").asText("")
        } catch (e: IOException) {
            ""
        }
        val lower = message.lowercase()
        return TranslationException(when {
            code == 429 -> "Se alcanzó el límite gratuito de Gemini por ahora; espera un minuto (o hasta mañana si es el límite diario)."
            "api key not valid" in lower || "api_key_invalid" in lower -> "La clave de Gemini no es válida."
            "location is not supported" in lower -> "El plan gratuito de Gemini no está disponible en tu país."
            code == 401 || code == 403 -> "La clave de Gemini no tiene permiso: revisa que esté bien copiada."
            code >= 500 -> "Gemini está saturado o caído ahora mismo ($code)."
            else -> "Error de Gemini ($code): ${message.ifEmpty { "sin detalles" }}"
        })
    }

    companion object {
        /** Del más nuevo al más antiguo; todos con plan gratuito. */
        val DEFAULT_MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-2.5-flash")

        /** Esquema de la respuesta en el formato de la API de Gemini (tipos en mayúsculas). */
        private val RESPONSE_SCHEMA = mapOf(
            "type" to "OBJECT",
            "properties" to mapOf(
                "translations" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf("id" to mapOf("type" to "INTEGER"), "text" to mapOf("type" to "STRING")),
                        "required" to listOf("id", "text"),
                    ),
                ),
                "summary" to mapOf("type" to "STRING"),
                "glossary" to mapOf(
                    "type" to "ARRAY",
                    "items" to mapOf(
                        "type" to "OBJECT",
                        "properties" to mapOf("original" to mapOf("type" to "STRING"), "translation" to mapOf("type" to "STRING")),
                        "required" to listOf("original", "translation"),
                    ),
                ),
            ),
            "required" to listOf("translations", "summary", "glossary"),
        )
    }
}
