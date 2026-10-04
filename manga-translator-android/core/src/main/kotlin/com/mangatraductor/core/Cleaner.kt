package com.mangatraductor.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Máscara del texto original, borrado y cálculo del espacio dentro de cada globo. */
object Cleaner {

    /**
     * Calcula la máscara de los trazos del texto de cada bloque.
     *
     * Alrededor de la caja se buscan las manchas que se distinguen del fondo.
     * Se quedan las que tocan la caja (y, en una segunda pasada, las muy
     * cercanas a ellas, como el punto de un "！"), pero nunca las que salen de
     * la zona de búsqueda: así el contorno del globo no se toma por texto.
     * Con el detector de manga ([layout]) se usa su máscara de letras, que es
     * mucho más precisa, completada con las manchas del texto que la tocan.
     * La caja del bloque se amplía para abarcar todos los trazos.
     */
    fun refineBlocks(image: PixelImage, blocks: List<TextBlock>, layout: TextLayout? = null) {
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

            val basic = BooleanArray(rw * rh) { keep[comps.labels[it]] }
            val fromLayout = layout?.let { layoutMask(it, region, box, comps, inside) }
            val mask = if (fromLayout == null) basic else BooleanArray(rw * rh) { fromLayout[it] || basic[it] }
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

    /** Devuelve a [cleaned] el texto original de [blocks] (los que al final no se rotulan). */
    fun restore(cleaned: PixelImage, original: PixelImage, blocks: List<TextBlock>) {
        for (block in blocks) {
            val mask = block.mask ?: continue
            val region = block.maskRegion ?: continue
            // Lo que pudo tocar el borrado: la máscara ampliada (también el margen de LaMa).
            val touched = dilate(mask, region.width, region.height, 6)
            for (p in touched.indices) if (touched[p]) {
                val i = (region.top + p / region.width) * cleaned.width + region.left + p % region.width
                cleaned.argb[i] = original.argb[i]
            }
        }
    }

    /**
     * Máscara del detector de manga dentro de [region]: sus manchas de letra
     * que tocan la caja del bloque, más las manchas de tinta ([comps]) que las
     * pisan (trazos que la red dejó a medias). Null si ahí no vio letras.
     */
    /** ¿El fondo de [box] (ya sin el texto) es claro y liso, como el de un globo? */
    private fun lightBackground(gray: IntArray, w: Int, box: Box): Boolean {
        val values = IntArray(box.area)
        var n = 0
        for (y in box.top until box.bottom) for (x in box.left until box.right) values[n++] = gray[y * w + x]
        if (n == 0) return false
        val typical = median255(values, n)
        var close = 0
        for (i in 0 until n) if (abs(values[i] - typical) <= 25) close++
        return typical >= 200 && close >= n * 0.85
    }

    private fun layoutMask(layout: TextLayout, region: Box, box: Box, comps: Components, inside: BooleanArray): BooleanArray? {
        val rw = region.width
        val rh = region.height
        val seg = BooleanArray(rw * rh) { p -> layout.ink(region.left + p % rw, region.top + p / rw) >= TextLayout.INK }
        val segComps = connectedComponents(seg, rw, rh, eightConnected = true)
        val near = box.expand(2)
        val keepSeg = BooleanArray(segComps.count + 1)
        for (p in seg.indices) {
            val l = segComps.labels[p]
            if (l == 0 || keepSeg[l]) continue
            val x = region.left + p % rw
            val y = region.top + p / rw
            if (x >= near.left && x < near.right && y >= near.top && y < near.bottom) keepSeg[l] = true
        }
        val fromSeg = BooleanArray(rw * rh) { keepSeg[segComps.labels[it]] }
        if (fromSeg.none { it }) return null
        val keepInk = BooleanArray(comps.count + 1)
        for (p in fromSeg.indices) if (fromSeg[p]) {
            val l = comps.labels[p]
            if (inside[l]) keepInk[l] = true
        }
        return BooleanArray(rw * rh) { fromSeg[it] || keepInk[comps.labels[it]] }
    }

    /**
     * Devuelve una copia de la imagen sin el texto original. Si el fondo del
     * bloque es liso (globo blanco) los trazos se pintan de ese color; si no
     * (texto sobre el dibujo) los reconstruye [inpainter] (LaMa si está).
     */
    fun clean(image: PixelImage, blocks: List<TextBlock>, inpainter: Inpainter = SimpleInpainter): PixelImage {
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
            val lumas = IntArray(rw * rh)
            var n = 0
            for (p in hole.indices) {
                val x = region.left + p % rw
                val y = region.top + p / rw
                if (hole[p] || x < block.box.left || x >= block.box.right || y < block.box.top || y >= block.box.bottom) continue
                val c = out.argb[y * out.width + x]
                reds[n] = (c shr 16) and 0xFF; greens[n] = (c shr 8) and 0xFF; blues[n] = c and 0xFF
                lumas[n] = PixelImage.luma(c)
                n++
            }
            // Liso si casi todo el fondo es del mismo tono (unos pocos píxeles del
            // contorno del globo dentro de la caja no lo impiden).
            val typical = median255(lumas, n)
            var close = 0
            for (i in 0 until n) if (abs(lumas[i] - typical) <= 20) close++

            if (n > 20 && close >= n * 0.9) {
                val color = (0xFF shl 24) or (median255(reds, n) shl 16) or
                    (median255(greens, n) shl 8) or median255(blues, n)
                for (p in hole.indices) if (hole[p]) {
                    out.argb[(region.top + p / rw) * out.width + region.left + p % rw] = color
                }
            } else {
                inpainter.inpaint(out, region, hole)
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
            // Un globo cerrado o, si no, una zona lisa y clara alrededor del texto (un globo
            // cortado por el borde de la viñeta): así la traducción no queda encajada en el
            // hueco justo del texto original.
            val closed = Bubbles.find(gray, w, h, box)
            val bubble = closed ?: Bubbles.find(gray, w, h, box, open = true)?.takeIf { lightBackground(gray, w, box) }
            val rects = bubble?.let { Bubbles.inscribedRects(it, box.centerX, box.centerY) }.orEmpty().map { r ->
                // Un poco de aire con el borde del globo.
                val mx = max(2, (r.width * 0.06).toInt())
                val my = max(2, (r.height * 0.06).toInt())
                Box(r.left + mx, r.top + my, r.right - mx, r.bottom - my)
            }.filter { it.width > 4 && it.height > 4 }
            block.inBubble = closed != null && rects.isNotEmpty()
            block.shape = if (rects.isNotEmpty()) bubble?.let { Bubbles.shape(it, box.centerX, box.centerY) } else null
            block.renderOptions = if (rects.isNotEmpty()) {
                // La principal siempre abarca el texto original (globos muy justos o texto
                // descentrado), pero sin salirse del globo (de su interior alrededor del texto).
                val inside = block.shape?.let { s -> Box(s.left.min(), s.top, s.right.max() + 1, s.bottom) } ?: bubble!!.bounds
                listOf(rects.first().union(box).intersect(inside)) + rects.drop(1)
            } else {
                // texto suelto sobre el dibujo: darle algo más de ancho
                val half = max(box.width, (box.height * 0.7).toInt()) / 2
                listOf(Box(max(0, box.centerX - half), box.top, min(w, box.centerX + half), box.bottom))
            }
        }
        Bubbles.separate(blocks)
    }
}
