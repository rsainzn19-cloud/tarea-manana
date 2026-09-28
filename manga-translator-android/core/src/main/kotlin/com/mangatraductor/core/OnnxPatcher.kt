package com.mangatraductor.core

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Retoca un modelo ONNX sin cargarlo: pone `accuracy_level = 4` en las
 * multiplicaciones de pesos de 4 bits (`MatMulNBits`). Así ONNX Runtime las
 * calcula en int8 (instrucciones de enteros del procesador ARM) en vez de en
 * float: bastante más rápido y con la misma traducción.
 *
 * Sólo se reescribe el grafo (el .onnx de ~1 MB); los pesos siguen en los
 * .onnx_data de al lado. Se lee el formato protobuf a mano: basta con recorrer
 * modelo → grafo → nodos → atributos (y los subgrafos de los `If`).
 */
object OnnxPatcher {

    /**
     * Devuelve una copia retocada de [model] en la misma carpeta (para que
     * encuentre sus .onnx_data). Si ya existe y es más nueva, no se rehace.
     */
    fun withInt8MatMul(model: File): File {
        val patched = File(model.parentFile, model.name.removeSuffix(".onnx") + ".int8.onnx")
        if (patched.exists() && patched.lastModified() >= model.lastModified()) return patched
        val tmp = File(patched.path + ".tmp")
        tmp.writeBytes(setMatMulNBitsAccuracy(model.readBytes(), 4))
        if (!tmp.renameTo(patched)) {
            tmp.copyTo(patched, overwrite = true)
            tmp.delete()
        }
        return patched
    }

    private enum class Kind { MODEL, GRAPH, NODE, ATTRIBUTE }

    fun setMatMulNBitsAccuracy(model: ByteArray, level: Int): ByteArray = rewrite(model, 0, model.size, Kind.MODEL, level)

    private fun rewrite(buf: ByteArray, from: Int, to: Int, kind: Kind, level: Int): ByteArray {
        val out = ByteArrayOutputStream(to - from + 64)
        val patchNode = kind == Kind.NODE && opType(buf, from, to) == "MatMulNBits"
        var pos = from
        while (pos < to) {
            val fieldStart = pos
            val tag = readVarint(buf, pos).also { pos = it.second }.first
            val field = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            when (wire) {
                0 -> pos = readVarint(buf, pos).second
                1 -> pos += 8
                5 -> pos += 4
                2 -> {
                    val (len, start) = readVarint(buf, pos)
                    val end = start + len.toInt()
                    val child = when {
                        kind == Kind.MODEL && field == 7 -> Kind.GRAPH // ModelProto.graph
                        kind == Kind.GRAPH && field == 1 -> Kind.NODE // GraphProto.node
                        kind == Kind.NODE && field == 5 -> Kind.ATTRIBUTE // NodeProto.attribute
                        kind == Kind.ATTRIBUTE && (field == 6 || field == 11) -> Kind.GRAPH // AttributeProto.g / graphs
                        else -> null
                    }
                    if (patchNode && field == 5 && attributeName(buf, start, end) == "accuracy_level") {
                        pos = end // se sustituye por el nuevo
                        continue
                    }
                    if (child != null) {
                        writeLengthDelimited(out, field, rewrite(buf, start, end, child, level))
                        pos = end
                        continue
                    }
                    pos = end
                }
                else -> error("formato ONNX no soportado (tipo $wire)")
            }
            out.write(buf, fieldStart, pos - fieldStart)
        }
        if (patchNode) {
            // AttributeProto { name = "accuracy_level", i = level, type = INT (2) }
            val attr = ByteArrayOutputStream()
            writeLengthDelimited(attr, 1, "accuracy_level".toByteArray())
            writeVarint(attr, (3L shl 3) or 0)
            writeVarint(attr, level.toLong())
            writeVarint(attr, (20L shl 3) or 0)
            writeVarint(attr, 2)
            writeLengthDelimited(out, 5, attr.toByteArray())
        }
        return out.toByteArray()
    }

    /** NodeProto.op_type (campo 4). */
    private fun opType(buf: ByteArray, from: Int, to: Int) = stringField(buf, from, to, 4)

    /** AttributeProto.name (campo 1). */
    private fun attributeName(buf: ByteArray, from: Int, to: Int) = stringField(buf, from, to, 1)

    private fun stringField(buf: ByteArray, from: Int, to: Int, wanted: Int): String? {
        var pos = from
        while (pos < to) {
            val tag = readVarint(buf, pos).also { pos = it.second }.first
            when ((tag and 7).toInt()) {
                0 -> pos = readVarint(buf, pos).second
                1 -> pos += 8
                5 -> pos += 4
                2 -> {
                    val (len, start) = readVarint(buf, pos)
                    if ((tag ushr 3).toInt() == wanted) return String(buf, start, len.toInt(), Charsets.UTF_8)
                    pos = start + len.toInt()
                }
                else -> return null
            }
        }
        return null
    }

    private fun readVarint(buf: ByteArray, at: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = at
        while (true) {
            val b = buf[pos++].toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return result to pos
            shift += 7
        }
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v and 0x7fL.inv() != 0L) {
            out.write(((v and 0x7f) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun writeLengthDelimited(out: ByteArrayOutputStream, field: Int, bytes: ByteArray) {
        writeVarint(out, (field.toLong() shl 3) or 2)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }
}
