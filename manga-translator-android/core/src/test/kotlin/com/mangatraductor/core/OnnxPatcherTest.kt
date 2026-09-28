package com.mangatraductor.core

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue

class OnnxPatcherTest {

    // Protobuf mínimo para construir un modelo de prueba.
    private fun varint(out: ByteArrayOutputStream, v: Long) {
        var x = v
        while (x >= 0x80) { out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
    }
    private fun msg(vararg fields: Pair<Int, Any>): ByteArray = ByteArrayOutputStream().also { out ->
        for ((field, value) in fields) when (value) {
            is Int -> { varint(out, (field.toLong() shl 3)); varint(out, value.toLong()) }
            is String -> { varint(out, (field.toLong() shl 3) or 2); varint(out, value.toByteArray().size.toLong()); out.write(value.toByteArray()) }
            is ByteArray -> { varint(out, (field.toLong() shl 3) or 2); varint(out, value.size.toLong()); out.write(value) }
        }
    }.toByteArray()

    private fun attr(name: String, i: Int) = msg(1 to name, 3 to i, 20 to 2)
    private fun node(op: String, vararg attrs: ByteArray) = msg(1 to "x", 4 to op, *attrs.map { 5 to it }.toTypedArray(), 7 to "com.microsoft")

    @Test
    fun setsAccuracyLevelOnEveryMatMulNBitsIncludingSubgraphs() {
        val inner = msg(1 to node("MatMulNBits", attr("bits", 4)))
        val ifNode = node("If", msg(1 to "then_branch", 6 to inner, 20 to 5))
        val model = msg(1 to 8, 7 to msg(1 to node("MatMulNBits", attr("bits", 4), attr("accuracy_level", 0)), 1 to ifNode, 1 to node("Gather")))

        val patched = OnnxPatcher.setMatMulNBitsAccuracy(model, 4)
        val text = String(patched, Charsets.ISO_8859_1)
        // Dos MatMulNBits (uno dentro del If), cada uno con un solo accuracy_level = 4.
        assertEquals(2, Regex("accuracy_level\u0018\u0004").findAll(text).count())
        assertEquals(2, Regex("accuracy_level").findAll(text).count())
        // Aplicarlo otra vez no cambia nada.
        assertEquals(patched.toList(), OnnxPatcher.setMatMulNBitsAccuracy(patched, 4).toList())
    }

    @Test
    fun patchesTheRealQwenGraph() {
        val dir = File(System.getProperty("qwenDir").orEmpty())
        val original = File(dir, "decoder_model_merged_q4.onnx")
        assumeTrue("QWEN_DIR no indicado", original.exists())
        val patched = OnnxPatcher.withInt8MatMul(original)
        val text = String(patched.readBytes(), Charsets.ISO_8859_1)
        assertEquals(187, Regex("accuracy_level\u0018\u0004").findAll(text).count())
    }
}
