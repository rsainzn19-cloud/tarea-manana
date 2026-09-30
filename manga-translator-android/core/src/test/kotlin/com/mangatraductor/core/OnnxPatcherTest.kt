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

    /** Una entrada [lote, canales, 512, 512] como la de LaMa: ValueInfo { name, type { tensor_type { shape } } }. */
    private fun imageInput(name: String, channels: Int): ByteArray {
        val dims = listOf(msg(2 to "batch"), msg(1 to channels), msg(1 to 512), msg(1 to 512))
        val shape = msg(*dims.map { 1 to it }.toTypedArray())
        return msg(1 to name, 2 to msg(1 to msg(1 to 1, 2 to shape)))
    }

    @Test
    fun freesHeightAndWidthOfImageInputsWithoutChangingTheSize() {
        val output = msg(1 to "output", 2 to msg(1 to msg(1 to 1, 2 to msg(1 to msg(2 to "batch")))))
        val model = msg(1 to 8, 7 to msg(1 to node("Conv"), 11 to imageInput("image", 3), 11 to imageInput("mask", 1), 12 to output))
        val buffer = java.nio.ByteBuffer.wrap(model.copyOf())
        assertEquals(4, OnnxPatcher.freeImageSize(buffer))
        val patched = buffer.array()
        assertEquals(model.size, patched.size)
        // Las dimensiones 2 y 3 pasan a llamarse "h" y "w"; el lote y los canales no cambian.
        val expected = msg(1 to 8, 7 to msg(1 to node("Conv"), 11 to freeInput("image", 3), 11 to freeInput("mask", 1), 12 to output))
        assertEquals(expected.toList(), patched.toList())
        // Una segunda vez ya no hay nada que cambiar.
        assertEquals(0, OnnxPatcher.freeImageSize(java.nio.ByteBuffer.wrap(patched)))
    }

    private fun freeInput(name: String, channels: Int): ByteArray {
        val dims = listOf(msg(2 to "batch"), msg(1 to channels), msg(2 to "h"), msg(2 to "w"))
        val shape = msg(*dims.map { 1 to it }.toTypedArray())
        return msg(1 to name, 2 to msg(1 to msg(1 to 1, 2 to shape)))
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
