package com.mangatraductor.core

/**
 * Las líneas de texto de un mapa de probabilidad de DBNet (la cabeza "det"
 * de comic-text-detector y el detector de PaddleOCR): las zonas con
 * probabilidad > 0,3 cuya media pasa de [minScore], agrandadas como hace
 * DBNet (el mapa marca el centro de la línea, algo más estrecho que las letras).
 */
internal object DbLines {
    private const val THRESHOLD = 0.3f
    private const val UNCLIP = 1.5f

    /**
     * [prob] es un mapa de [mapW] x [mapH] (sólo cuentan las primeras [usedW] x
     * [usedH]); [fx] y [fy] pasan de sus píxeles a los de [tile] en [image].
     */
    fun find(
        prob: FloatArray, mapW: Int, mapH: Int, usedW: Int, usedH: Int,
        tile: Box, image: PixelImage, fx: Float, fy: Float, minScore: Float = 0.5f,
    ): List<TextLayout.ScoredBox> {
        val on = BooleanArray(mapW * mapH) { p -> p % mapW < usedW && p / mapW < usedH && prob[p] > THRESHOLD }
        val comps = connectedComponents(on, mapW, mapH, eightConnected = true)
        val sums = FloatArray(comps.count + 1)
        val counts = IntArray(comps.count + 1)
        for (p in on.indices) {
            val l = comps.labels[p]
            if (l > 0) {
                sums[l] += prob[p]
                counts[l]++
            }
        }
        val out = mutableListOf<TextLayout.ScoredBox>()
        for (l in 1..comps.count) {
            val bw = comps.maxX[l] - comps.minX[l] + 1
            val bh = comps.maxY[l] - comps.minY[l] + 1
            if (bw < 3 || bh < 3) continue
            val score = sums[l] / counts[l]
            if (score < minScore) continue
            val d = bw * bh * UNCLIP / (2f * (bw + bh))
            val box = Box(
                tile.left + ((comps.minX[l] - d) * fx).toInt(), tile.top + ((comps.minY[l] - d) * fy).toInt(),
                tile.left + ((comps.maxX[l] + 1 + d) * fx).toInt(), tile.top + ((comps.maxY[l] + 1 + d) * fy).toInt(),
            ).clip(image.width, image.height)
            if (box.width > 4 && box.height > 4) out += TextLayout.ScoredBox(box, score)
        }
        return out
    }
}
