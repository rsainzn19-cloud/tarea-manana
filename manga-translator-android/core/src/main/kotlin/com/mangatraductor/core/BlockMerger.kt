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

    /**
     * Agrupa con los bloques del detector de manga ([layout]): cada línea del
     * OCR va al bloque que la contiene, así un globo nunca se parte. Los
     * bloques en los que el OCR no vio nada se añaden igual (manga-ocr los
     * lee), y las líneas fuera de todo bloque se agrupan como siempre.
     */
    fun mergeWithLayout(detections: List<DetectedText>, layout: TextLayout, rightToLeft: Boolean = true): List<TextBlock> {
        val boxes = layout.blocks.map { it.box }
        val parts = boxes.map { mutableListOf<DetectedText>() }
        val rest = mutableListOf<DetectedText>()
        for (d in detections) {
            val best = boxes.indices.maxByOrNull { boxes[it].overlapArea(d.box) }
            if (best != null && boxes[best].overlapArea(d.box) >= d.box.area / 2) parts[best] += d else rest += d
        }
        val blocks = boxes.indices.mapNotNull { i ->
            val inside = parts[i]
            when {
                inside.isNotEmpty() -> TextBlock(inside.map { it.box }.fold(boxes[i], Box::union), inside)
                // Sin OCR dentro: sólo si el detector está seguro y de verdad hay letras.
                layout.blocks[i].score >= 0.6f && layout.inkFraction(boxes[i]) >= 0.03f -> TextBlock(boxes[i])
                else -> null
            }
        }
        return readingOrder(blocks + merge(rest, rightToLeft), rightToLeft)
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
