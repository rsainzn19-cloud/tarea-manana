package com.mangatraductor.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/** Traduce de verdad con Qwen 3.5 en el PC (el mismo código que en el móvil). Necesita QWEN_DIR. */
class QwenTranslatorTest {

    @Test
    fun translatesBubblesKeepingNamesFromTheStory() {
        val dir = File(System.getProperty("qwenDir").orEmpty())
        assumeTrue("QWEN_DIR no indicado: se omite la prueba con Qwen", File(dir, "decoder_model_merged_q4.onnx").exists())

        var start = System.nanoTime()
        LocalLlm.load(
            tokenizer = File(dir, "tokenizer.json"),
            // Con las multiplicaciones en int8, como en el móvil.
            decoder = OnnxPatcher.withInt8MatMul(File(dir, "decoder_model_merged_q4.onnx")),
            embed = File(dir, "embed_tokens_q4.onnx"),
            threads = Runtime.getRuntime().availableProcessors(),
        ).use { llm ->
            println("Qwen cargado en ${(System.nanoTime() - start) / 1_000_000} ms")
            val story = StoryContext().apply {
                remember(listOf("ハル、起きて！"), listOf("Haru, wake up!"))
                update("Aki wakes up her friend Haru for school.", mapOf("ハル" to "Haru", "アキ" to "Aki"))
            }
            val progress = mutableListOf<String>()
            start = System.nanoTime()
            val texts = listOf(
                "おはよう!今日はいい天気だね。",
                "やばい、学校に遅れちゃうよ!",
                "ちょっと待って、忘れ物した!",
                "うるさいなぁ、アキは。",
                "お腹すいた…何か食べたい。",
            )
            val result = QwenTranslator(llm, "en") { progress += it }.translate(texts, null, story)
            println("Qwen tradujo en ${(System.nanoTime() - start) / 1_000_000} ms:")
            texts.zip(result).forEach { (jp, tr) -> println("  $jp -> $tr") }

            assertEquals(texts.size, result.size)
            assertTrue(result.all { it.isNotBlank() }, "faltan traducciones: $result")
            assertTrue("morning" in result[0].lowercase(), result[0])
            assertTrue("school" in result[1].lowercase(), result[1])
            assertTrue("Aki" in result[3], result[3]) // el nombre de la memoria de la historia
            assertTrue("hungry" in result[4].lowercase() || "eat" in result[4].lowercase(), result[4])
            assertEquals("Qwen traduciendo… 5/5", progress.last())
        }
    }
}
