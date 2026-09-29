package com.mangatraductor.core

import kotlin.math.max
import kotlin.math.min

/**
 * Agrupa los trozos de texto del detector en bloques (≈ un globo cada uno) y
 * los ordena como se lee el cómic: de arriba a abajo y, en cada fila, de
 * derecha a izquierda (manga) o de izquierda a derecha (manhua, manhwa).
 */
object BlockMerger {

    fun merge(detections: List<DetectedText>, rightToLeft: Boolean = true, gapFactor: Double = 0.6): List<TextBlock> {
        if (detections.isEmpty()) return emptyList()

        // Tamaño típico de un carácter: la mediana del lado corto de cada caja.
        val charSize = detections.map { min(it.box.width, it.box.height) }.sorted()[detections.size / 2]
        val gap = max(2, (charSize * gapFactor).toInt())

        val parent = IntArray(detections.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        for (i in detections.indices) {
            val a = detections[i].box.expand(gap)
            for (j in i + 1 until detections.size) {
                if (a.intersects(detections[j].box)) parent[find(i)] = find(j)
            }
        }

        val minArea = (charSize * 0.8) * (charSize * 0.8)
        return detections.indices
            .groupBy { find(it) }
            .values
            .map { members -> members.map { detections[it] } }
            .map { parts -> TextBlock(parts.map { it.box }.reduce(Box::union), parts) }
            .filter { it.box.area >= minArea } // ruido: más pequeño que un carácter
            .let { readingOrder(it, rightToLeft) }
    }

    /** Filas de bloques que se solapan en vertical; dentro de cada fila, en el sentido de lectura. */
    fun readingOrder(blocks: List<TextBlock>, rightToLeft: Boolean = true): List<TextBlock> {
        val rows = mutableListOf<MutableList<TextBlock>>()
        for (block in blocks.sortedBy { it.box.top }) {
            val row = rows.firstOrNull { row ->
                val top = row.minOf { it.box.top }
                val bottom = row.maxOf { it.box.bottom }
                val overlap = min(bottom, block.box.bottom) - max(top, block.box.top)
                overlap > 0.3 * min(block.box.height, bottom - top)
            }
            if (row != null) row += block else rows += mutableListOf(block)
        }
        return rows.flatMap { row -> if (rightToLeft) row.sortedByDescending { it.box.right } else row.sortedBy { it.box.left } }
    }
}
