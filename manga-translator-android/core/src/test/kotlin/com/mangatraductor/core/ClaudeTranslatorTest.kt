package com.mangatraductor.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Prueba el traductor de Claude contra un servidor local que imita la API. */
class ClaudeTranslatorTest {

    private val mapper = ObjectMapper()
    private var lastBody: JsonNode? = null
    private var lastBeta: String? = null
    private var stopReason = "end_turn"

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/v1/messages") { exchange ->
            val body = mapper.readTree(exchange.requestBody.readAllBytes())
            lastBody = body
            lastBeta = exchange.requestHeaders.getFirst("anthropic-beta")
            val prompt = body["messages"][0]["content"].last()["text"].asText()
            val n = prompt.lines().size - 1
            // ids desordenados, para comprobar que se reordenan
            val translations = (n - 1 downTo 0).map { mapOf("id" to it, "text" to "EN$it") }
            val response = mapOf(
                "id" to "msg_test", "type" to "message", "role" to "assistant", "model" to body["model"].asText(),
                "content" to listOf(mapOf("type" to "text", "text" to mapper.writeValueAsString(mapOf("translations" to translations)))),
                "stop_reason" to stopReason, "stop_sequence" to null,
                "usage" to mapOf("input_tokens" to 1, "output_tokens" to 1),
            )
            val bytes = mapper.writeValueAsBytes(response)
            exchange.responseHeaders.add("content-type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    private val translator = ClaudeTranslator(
        apiKey = "test-key",
        targetLanguage = "es",
        baseUrl = "http://127.0.0.1:${server.address.port}",
    )

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun sendsPageAndTextAndReordersAnswer() {
        val result = translator.translate(listOf("おはよう", "学校に遅れちゃうよ", "待って"), byteArrayOf(1, 2, 3))
        assertEquals(listOf("EN0", "EN1", "EN2"), result)

        val body = lastBody!!
        assertEquals("claude-opus-5", body["model"].asText())
        assertEquals("default", body["fallbacks"].asText())
        assertEquals("server-side-fallback-2026-07-01", lastBeta)
        assertEquals("adaptive", body["thinking"]["type"].asText())
        assertEquals("medium", body["output_config"]["effort"].asText())
        assertEquals("json_schema", body["output_config"]["format"]["type"].asText())
        val content = body["messages"][0]["content"]
        assertEquals(listOf("image", "text"), content.map { it["type"].asText() })
        assertEquals("image/jpeg", content[0]["source"]["media_type"].asText())
        assertEquals(true, body["system"].asText().contains("Spanish"))
    }

    @Test
    fun incompleteAnswerIsAnError() {
        stopReason = "max_tokens"
        assertFailsWith<TranslationException> { translator.translate(listOf("おはよう"), null) }
    }
}
