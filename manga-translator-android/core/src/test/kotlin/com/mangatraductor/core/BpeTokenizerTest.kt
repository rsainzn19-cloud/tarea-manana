package com.mangatraductor.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class BpeTokenizerTest {

    /** Tokenizador de juguete: bytes sueltos + dos reglas de unión + un token especial. */
    private val tiny = BpeTokenizer.load(
        """
        {"version":"1.0","added_tokens":[{"id":9,"content":"<|end|>","special":true}],
         "normalizer":{"type":"NFC"},
         "pre_tokenizer":{"type":"Sequence","pretokenizers":[
           {"type":"Split","pattern":{"Regex":"[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\s+(?!\\S)|\\s+"},"behavior":"Isolated"},
           {"type":"ByteLevel","add_prefix_space":false,"use_regex":false}]},
         "model":{"type":"BPE","vocab":{"a":0,"b":1,"Ġ":2,"ab":3,"Ġab":4},
                  "merges":[["a","b"],"Ġ ab"]}}
        """.trimIndent().byteInputStream(),
    )

    @Test
    fun mergesByPriorityAndKeepsSpecialTokens() {
        assertContentEquals(intArrayOf(3, 4, 9, 0), tiny.encode("ab ab<|end|>a"))
        assertEquals("ab aba", tiny.decode(intArrayOf(3, 4, 9, 0))) // los especiales no se escriben
        // Espacio ideográfico (U+3000): Java por defecto no lo considera espacio; Qwen sí.
        assertTrue(BpeTokenizer.portable("""\s+(?!\S)|[^\s\p{L}]""").contains("\\u3000"))
    }

    /** Mismos números que la librería `tokenizers` de Hugging Face con el tokenizador real de Qwen 3.5. */
    @Test
    fun matchesHuggingFaceOnQwen() {
        val dir = System.getProperty("qwenDir").orEmpty()
        assumeTrue("QWEN_DIR no indicado: se omite la prueba con Qwen", dir.isNotEmpty() && File(dir, "tokenizer.json").exists())
        val start = System.nanoTime()
        val tok = BpeTokenizer.load(File(dir, "tokenizer.json"))
        println("tokenizer.json cargado en ${(System.nanoTime() - start) / 1_000_000} ms")

        val cases = listOf(
            "おはよう!今日はいい天気だね。" to intArrayOf(31171, 14876, 148564, 0, 191506, 149675, 247570, 247949, 1710),
            "ｷﾞｬｱｱｱ!!　ドドドド…　「待って」" to intArrayOf(247196, 169571, 14945, 105, 247172, 247172, 247172, 2834, 21742, 43332, 43332, 43332, 43332, 1873, 21742, 12512, 96739, 73256, 10119),
            "It's 12:30 — we're LATE, aren't we?\n\n  Hmm...   ok\t\tbye\r\n" to intArrayOf(2064, 579, 220, 16, 17, 25, 18, 15, 1892, 567, 2224, 436, 2260, 11, 7386, 914, 567, 30, 271, 220, 85152, 1076, 256, 5226, 197, 197, 27450, 317),
            // "か" + marca de sonoridad separada: se normaliza (NFC) a "が".
            "<|im_start|>user\nがんばって 🎉<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n" to intArrayOf(248045, 846, 198, 224433, 73256, 10838, 236, 231, 248046, 198, 248045, 74455, 198, 248068, 271, 248069, 271),
            "Café naïve résumé Ñandú 1234567 ０１２" to intArrayOf(34, 2492, 933, 91603, 571, 238976, 1624, 239, 429, 6445, 220, 16, 17, 18, 19, 20, 21, 22, 220, 25191, 19496, 24128),
        )
        for ((text, expected) in cases) {
            val ids = tok.encode(text)
            assertContentEquals(expected, ids, "«$text»")
        }
        assertEquals("ｷﾞｬｱｱｱ!!　ドドドド…　「待って」", tok.decode(cases[1].second))
        assertEquals("user\nがんばって 🎉\nassistant\n<think>\n\n</think>\n\n", tok.decode(cases[3].second))
        assertEquals(248046, tok.tokenId("<|im_end|>"))
    }
}
