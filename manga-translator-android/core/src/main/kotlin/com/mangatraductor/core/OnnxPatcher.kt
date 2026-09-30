package com.mangatraductor.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Retoca modelos ONNX sin cargarlos. En Qwen pone `accuracy_level = 4` en las
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

    /**
     * [model] en memoria (proyectado del archivo, sin leerlo entero) con el
     * alto y el ancho de sus entradas de imagen libres (ver [freeImageSize]).
     * El archivo no cambia: la proyección es privada (copia al escribir).
     * Devuelve también cuántas dimensiones se liberaron.
     */
    fun mapWithFreeImageSize(model: File): Pair<ByteBuffer, Int> =
        FileChannel.open(model.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
            val buffer = channel.map(FileChannel.MapMode.PRIVATE, 0, channel.size())
            buffer to freeImageSize(buffer)
        }

    /**
     * El alto y el ancho (dimensiones 2 y 3) de cada entrada [lote, canales,
     * alto, ancho] pasan de un número fijo a libres ("h" y "w"), en [buf]
     * mismo: `dim_value = 512` y `dim_param = "h"` ocupan lo mismo (3 bytes),
     * así que no cambia ninguna longitud. Así LaMa, exportado para 512 x 512,
     * acepta recortes más pequeños (y mucho más rápidos).
     */
    fun freeImageSize(buf: ByteBuffer): Int {
        var patched = 0
        forEachField(buf, 0, buf.limit()) { field, start, end ->
            if (field != 7) return@forEachField // ModelProto.graph
            forEachField(buf, start, end) inputs@{ graphField, inputStart, inputEnd ->
                if (graphField != 11) return@inputs // GraphProto.input
                // ValueInfoProto.type -> TypeProto.tensor_type -> Tensor.shape
                val type = findField(buf, inputStart, inputEnd, 2) ?: return@inputs
                val tensor = findField(buf, type.first, type.second, 1) ?: return@inputs
                val shape = findField(buf, tensor.first, tensor.second, 2) ?: return@inputs
                var index = 0
                forEachField(buf, shape.first, shape.second) { dimField, dimStart, dimEnd ->
                    if (dimField != 1) return@forEachField // TensorShapeProto.dim
                    if (index == 2 || index == 3) {
                        // Dimension { dim_value (campo 1) de 2 bytes } -> Dimension { dim_param (campo 2) = "h" / "w" }
                        val fixed = dimEnd - dimStart == 3 && buf.get(dimStart).toInt() == 0x08 &&
                            buf.get(dimStart + 1).toInt() and 0x80 != 0 && buf.get(dimStart + 2).toInt() and 0x80 == 0
                        if (fixed) {
                            buf.put(dimStart, 0x12.toByte())
                            buf.put(dimStart + 1, 0x01.toByte())
                            buf.put(dimStart + 2, (if (index == 2) 'h' else 'w').code.toByte())
                            patched++
                        }
                    }
                    index++
                }
            }
        }
        return patched
    }

    /** Cada campo con longitud (tipo 2) entre [from] y [to]: su número y dónde empieza y acaba. */
    private fun forEachField(buf: ByteBuffer, from: Int, to: Int, action: (field: Int, start: Int, end: Int) -> Unit) {
        var pos = from
        while (pos < to) {
            val (tag, afterTag) = readVarint(buf, pos)
            pos = afterTag
            when ((tag and 7).toInt()) {
                0 -> pos = readVarint(buf, pos).second
                1 -> pos += 8
                5 -> pos += 4
                2 -> {
                    val (len, start) = readVarint(buf, pos)
                    val end = start + len.toInt()
                    action((tag ushr 3).toInt(), start, end)
                    pos = end
                }
                else -> return
            }
        }
    }

    private fun findField(buf: ByteBuffer, from: Int, to: Int, wanted: Int): Pair<Int, Int>? {
        var found: Pair<Int, Int>? = null
        forEachField(buf, from, to) { field, start, end -> if (found == null && field == wanted) found = start to end }
        return found
    }

    private fun readVarint(buf: ByteBuffer, at: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = at
        while (true) {
            val b = buf.get(pos++).toInt() and 0xff
            result = result or ((b and 0x7f).toLong() shl shift)
            if (b and 0x80 == 0) return result to pos
            shift += 7
        }
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
