package com.mangatraductor.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Máscara del texto original, borrado y cálculo del espacio dentro de cada globo. */
object Cleaner {

    /**
     * Calcula la máscara de los trazos del texto de cada bloque.
     *
     * Alrededor de la caja se buscan las manchas que se distinguen del fondo.
     * Se quedan las que tocan la caja (y, en una segunda pasada, las muy
     * cercanas a ellas, como el punto de un "！"), pero nunca las que salen de
     * la zona de búsqueda: así el contorno del globo no se toma por texto.
     * La caja del bloque se amplía para abarcar todos los trazos.
     */
    fun refineBlocks(image: PixelImage, blocks: List<TextBlock>) {
        val gray = image.gray()
        for (block in blocks) {
            val box = block.box
            val pad = max(6, (min(box.width, box.height) * 0.25).toInt())
            val region = box.expand(pad).clip(image.width, image.height)
            val rw = region.width
            val rh = region.height
            if (rw < 3 || rh < 3) continue

            val border = IntArray(2 * (rw + rh))
            var n = 0
            for (x in 0 until rw) {
                border[n++] = gray[region.top * image.width + region.left + x]
                border[n++] = gray[(region.bottom - 1) * image.width + region.left + x]
            }
            for (y in 0 until rh) {
                border[n++] = gray[(region.top + y) * image.width + region.left]
                border[n++] = gray[(region.top + y) * image.width + region.right - 1]
            }
            val bg = median255(border, n)

            val ink = BooleanArray(rw * rh) { p ->
                abs(gray[(region.top + p / rw) * image.width + region.left + p % rw] - bg) > 50
            }
            val comps = connectedComponents(ink, rw, rh, eightConnected = true)
            val inside = BooleanArray(comps.count + 1) { l ->
                l > 0 && comps.minX[l] > 0 && comps.minY[l] > 0 &&
                    comps.maxX[l] < rw - 1 && comps.maxY[l] < rh - 1
            }

            val boxCore = BooleanArray(rw * rh) { p ->
                val x = region.left + p % rw
                val y = region.top + p / rw
                x >= box.left && x < box.right && y >= box.top && y < box.bottom
            }
            var core = boxCore
            val keep = BooleanArray(comps.count + 1)
            val near = max(2, pad / 3)
            repeat(2) {
                for (p in core.indices) {
                    val l = comps.labels[p]
                    if (core[p] && inside[l]) keep[l] = true
                }
                val grown = dilate(BooleanArray(rw * rh) { keep[comps.labels[it]] }, rw, rh, near)
                core = BooleanArray(rw * rh) { boxCore[it] || grown[it] }
            }

            val mask = BooleanArray(rw * rh) { keep[comps.labels[it]] }
            block.mask = mask
            block.maskRegion = region

            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
            for (p in mask.indices) if (mask[p]) {
                val x = p % rw; val y = p / rw
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
            }
            if (maxX >= 0) {
                block.box = box.union(
                    Box(region.left + minX, region.top + minY, region.left + maxX + 1, region.top + maxY + 1)
                )
            }
        }
    }

    /**
     * Devuelve una copia de la imagen sin el texto original. Si el fondo del
     * bloque es liso (globo blanco) los trazos se pintan de ese color; si no
     * (texto sobre el dibujo) se reconstruyen con [inpaint].
     */
    fun clean(image: PixelImage, blocks: List<TextBlock>): PixelImage {
        val out = image.copy()
        for (block in blocks) {
            val mask = block.mask ?: continue
            val region = block.maskRegion ?: continue
            if (mask.none { it }) continue
            val rw = region.width
            val rh = region.height
            val hole = dilate(mask, rw, rh, 2)

            // fondo = píxeles de la caja del bloque que no son texto
            val reds = IntArray(rw * rh); val greens = IntArray(rw * rh); val blues = IntArray(rw * rh)
            var n = 0
            var sum = 0.0; var sumSq = 0.0
            for (p in hole.indices) {
                val x = region.left + p % rw
                val y = region.top + p / rw
                if (hole[p] || x < block.box.left || x >= block.box.right || y < block.box.top || y >= block.box.bottom) continue
                val c = out.argb[y * out.width + x]
                reds[n] = (c shr 16) and 0xFF; greens[n] = (c shr 8) and 0xFF; blues[n] = c and 0xFF
                val l = PixelImage.luma(c).toDouble()
                sum += l; sumSq += l * l
                n++
            }
            val std = if (n > 0) sqrt(max(0.0, sumSq / n - (sum / n) * (sum / n))) else 999.0

            if (n > 20 && std < 15) {
                val color = (0xFF shl 24) or (median255(reds, n) shl 16) or
                    (median255(greens, n) shl 8) or median255(blues, n)
                for (p in hole.indices) if (hole[p]) {
                    out.argb[(region.top + p / rw) * out.width + region.left + p % rw] = color
                }
            } else {
                inpaint(out, region, hole)
            }
        }
        return out
    }

    /**
     * Calcula [TextBlock.renderBox]: el mayor rectángulo que cabe dentro del
     * globo que contiene el bloque (buscado en la imagen ya limpia). Si el
     * texto estaba sobre el dibujo, se ensancha un poco la caja original.
     * Al final se reparten las zonas para que ninguna pise a otra.
     */
    fun findRenderBoxes(cleaned: PixelImage, blocks: List<TextBlock>) {
        val gray = cleaned.gray()
        val w = cleaned.width
        val h = cleaned.height
        for (block in blocks) {
            val box = block.box
            val bubble = Bubbles.find(gray, w, h, box)
            val rects = bubble?.let { Bubbles.inscribedRects(it, box.centerX, box.centerY) }.orEmpty().map { r ->
                // Un poco de aire con el borde del globo.
                val mx = max(2, (r.width * 0.06).toInt())
                val my = max(2, (r.height * 0.06).toInt())
                Box(r.left + mx, r.top + my, r.right - mx, r.bottom - my)
            }.filter { it.width > 4 && it.height > 4 }
            block.renderOptions = if (rects.isNotEmpty()) {
                // La principal siempre abarca el texto original (globos muy justos o texto descentrado).
                listOf(rects.first().union(box)) + rects.drop(1)
            } else {
                // texto suelto sobre el dibujo: darle algo más de ancho
                val half = max(box.width, (box.height * 0.7).toInt()) / 2
                listOf(Box(max(0, box.centerX - half), box.top, min(w, box.centerX + half), box.bottom))
            }
        }
        Bubbles.separate(blocks)
    }
}
