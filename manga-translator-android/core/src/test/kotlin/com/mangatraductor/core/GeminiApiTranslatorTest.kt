package com.mangatraductor.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Prueba el traductor de Gemini contra un servidor local que imita la API de Google. */
class GeminiApiTranslatorTest {

    private val mapper = ObjectMapper()
    private val calls = mutableListOf<String>()
    private var lastBody: JsonNode? = null
    private var lastKey: String? = null
    private var status = 200

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1beta/models/") { exchange ->
            val path = exchange.requestURI.path
            calls += path
            lastBody = mapper.readTree(exchange.requestBody.readAllBytes())
            lastKey = exchange.requestHeaders.getFirst("x-goog-api-key")
            val (code, body) = when {
                "gemini-new" in path -> 404 to """{"error":{"code":404,"message":"models/gemini-new is not found"}}"""
                status != 200 -> status to """{"error":{"code":$status,"message":"Resource has been exhausted"}}"""
                else -> 200 to mapper.writeValueAsString(mapOf("candidates" to listOf(mapOf(
                    "finishReason" to "STOP",
                    "content" to mapOf("parts" to listOf(
                        mapOf("text" to "pensando...", "thought" to true),
                        mapOf("text" to mapper.writeValueAsString(mapOf(
                            // desordenadas, para comprobar que se reordenan
                            "translations" to listOf(mapOf("id" to 1, "text" to "Wait, Haru!"), mapOf("id" to 0, "text" to "Good morning")),
                            "summary" to "Haru and Aki go to school.",
                            "glossary" to listOf(mapOf("original" to "ハル", "translation" to "Haru")),
                        ))),
                    )),
                ))))
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("content-type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private fun translator(models: List<String>) = GeminiApiTranslator(
        apiKey = "clave-gratis",
        targetLanguage = "es",
        models = models,
        baseUrl = "http://127.0.0.1:${server.address.port}/v1beta",
    )

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun usesNextModelSeesPageRemembersStoryAndReorders() {
        val story = StoryContext().apply { update("Aki meets Haru.", mapOf("アキ" to "Aki")) }
        val result = translator(listOf("gemini-new", "gemini-ok"))
            .translate(listOf("おはよう", "待って、ハル！"), byteArrayOf(1, 2, 3), story)

        assertEquals(listOf("Good morning", "Wait, Haru!"), result)
        assertEquals(2, calls.size) // el primer modelo no existe: se probó el segundo
        assertTrue(calls[1].endsWith("/models/gemini-ok:generateContent"))
        assertEquals("clave-gratis", lastKey)

        val body = lastBody!!
        val parts = body["contents"][0]["parts"]
        assertEquals("image/jpeg", parts[0]["inline_data"]["mime_type"].asText())
        val prompt = parts[1]["text"].asText()
        assertTrue("Aki meets Haru." in prompt && "アキ = Aki" in prompt) // la memoria va en la petición
        assertEquals("application/json", body["generationConfig"]["responseMimeType"].asText())
        assertTrue("Spanish" in body["systemInstruction"]["parts"][0]["text"].asText())

        // La historia se actualiza con lo que devuelve la IA.
        assertEquals("Haru and Aki go to school.", story.summary)
        assertEquals("Haru", story.glossary["ハル"])
        assertEquals("Aki", story.glossary["アキ"])
    }

    @Test
    fun freeLimitReachedIsAClearError() {
        status = 429
        val error = assertFailsWith<TranslationException> { translator(listOf("gemini-ok")).translate(listOf("おはよう"), null) }
        assertTrue("límite gratuito" in error.message!!)
    }
}
