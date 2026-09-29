package com.mangatraductor.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Globos de diálogo: la zona conectada del color del fondo del texto que lo
 * rodea. Sirve para unir los trozos de texto de un mismo globo y para saber
 * dónde cabe la traducción.
 */
internal object Bubbles {

    /** Un globo cerrado: su caja y cuáles de sus píxeles son el interior. */
    class Bubble(val bounds: Box, private val interior: BooleanArray) {
        fun inside(x: Int, y: Int): Boolean =
            x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom &&
                interior[(y - bounds.top) * bounds.width + (x - bounds.left)]

        /** ¿El fondo de [box] es este globo? (el texto en sí no cuenta: son sus agujeros) */
        fun holds(box: Box): Boolean {
            var count = 0
            for (y in max(box.top, bounds.top) until min(box.bottom, bounds.bottom)) {
                for (x in max(box.left, bounds.left) until min(box.right, bounds.right)) {
                    if (interior[(y - bounds.top) * bounds.width + (x - bounds.left)]) count++
                }
            }
            return count >= box.area * 0.35
        }
    }

    /**
     * Busca el globo que rodea a [box]. [strokes] son los trazos del texto (si
     * aún no se ha borrado): cuentan como parte del globo. Devuelve null si el
     * texto no está en un globo cerrado (texto sobre el dibujo, globo abierto).
     */
    fun find(gray: IntArray, w: Int, h: Int, box: Box, strokes: TextBlock? = null): Bubble? {
        // Zona de búsqueda: el globo suele ser algo más grande que su texto.
        val size = max(box.width, box.height)
        val window = box.expand(size * 3 / 2).clip(w, h)
        val ww = window.width
        val wh = window.height
        if (ww < 3 || wh < 3) return null

        val stroke = BooleanArray(ww * wh)
        val mask = strokes?.mask
        val region = strokes?.maskRegion
        if (mask != null && region != null) {
            val grown = dilate(mask, region.width, region.height, 2)
            for (p in grown.indices) if (grown[p]) {
                val x = region.left + p % region.width - window.left
                val y = region.top + p / region.width - window.top
                if (x in 0 until ww && y in 0 until wh) stroke[y * ww + x] = true
            }
        }

        // Color del fondo: mediana de lo que hay en la caja que no es texto.
        val inner = IntArray(box.area)
        var n = 0
        for (y in box.top until box.bottom) for (x in box.left until box.right) {
            if (!stroke[(y - window.top) * ww + (x - window.left)]) inner[n++] = gray[y * w + x]
        }
        if (n == 0) return null
        val bg = median255(inner, n)

        var similar = BooleanArray(ww * wh) { p ->
            stroke[p] || abs(gray[(window.top + p / ww) * w + window.left + p % ww] - bg) < 30
        }
        // "apertura": corta uniones finas entre el globo y zonas blancas del dibujo
        similar = dilate(erode(similar, ww, wh, 1), ww, wh, 1)
        val comps = connectedComponents(similar, ww, wh, eightConnected = false)

        val votes = HashMap<Int, Int>()
        for (y in box.top until box.bottom) for (x in box.left until box.right) {
            val l = comps.labels[(y - window.top) * ww + (x - window.left)]
            if (l > 0) votes[l] = (votes[l] ?: 0) + 1
        }
        val label = votes.maxByOrNull { it.value }?.key ?: return null
        val touchesEdge = (comps.minX[label] == 0 && window.left > 0) ||
            (comps.minY[label] == 0 && window.top > 0) ||
            (comps.maxX[label] == ww - 1 && window.right < w) ||
            (comps.maxY[label] == wh - 1 && window.bottom < h)
        if (touchesEdge) return null
        val found = Box(
            window.left + comps.minX[label], window.top + comps.minY[label],
            window.left + comps.maxX[label] + 1, window.top + comps.maxY[label] + 1,
        )
        // Una zona enorme (el fondo de la página, un cielo blanco) no es un globo.
        if (found.area > 20L * box.area || found.area > w.toLong() * h / 4) return null
        val bounds = found
        val interior = BooleanArray(bounds.area) { p ->
            val x = bounds.left + p % bounds.width - window.left
            val y = bounds.top + p / bounds.width - window.top
            comps.labels[y * ww + x] == label
        }
        return Bubble(bounds, interior)
    }

    /**
     * Une los bloques que están en el mismo globo: el detector a veces parte
     * el texto de un globo en dos (columnas separadas, un "…" aparte) y cada
     * mitad se traducía por separado y se escribía encima de la otra.
     */
    fun mergeSameBubble(image: PixelImage, blocks: List<TextBlock>, rightToLeft: Boolean): List<TextBlock> {
        if (blocks.size < 2) return blocks
        val gray = image.gray()
        val bubbles = blocks.map { find(gray, image.width, image.height, it.box, it) }
        // Tamaño típico de un carácter, para no unir textos lejanos (dos globos pegados
        // forman una sola zona blanca, pero cada uno es de un personaje).
        val sides = blocks.flatMap { it.parts }.map { min(it.box.width, it.box.height) }.sorted()
        val charSize = if (sides.isEmpty()) 20 else sides[sides.size / 2]

        val parent = IntArray(blocks.size) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in blocks.indices) {
            val a = bubbles[i] ?: continue
            for (j in blocks.indices) {
                if (i == j || root(i) == root(j)) continue
                val b = bubbles[j]
                val bi = blocks[i].box
                val bj = blocks[j].box
                val gap = max(max(bi.left - bj.right, bj.left - bi.right), max(bi.top - bj.bottom, bj.top - bi.bottom))
                if (gap > 4 * charSize) continue
                val both = bi.union(bj)
                // El globo de i contiene a j (y, si j encontró su globo, también al revés)...
                if (!a.holds(blocks[j].box)) continue
                if (b != null && !b.holds(blocks[i].box)) continue
                // ...y no es una zona blanca enorme del dibujo con textos sueltos.
                if (a.bounds.area > 30L * both.area) continue
                parent[root(j)] = root(i)
            }
        }
        val groups = blocks.indices.groupBy { root(it) }.values
        if (groups.size == blocks.size) return blocks

        val merged = groups.map { members ->
            if (members.size == 1) {
                blocks[members[0]]
            } else {
                val parts = members.flatMap { blocks[it].parts }
                TextBlock(members.map { blocks[it].box }.reduce(Box::union), parts).also {
                    Cleaner.refineBlocks(image, listOf(it))
                }
            }
        }
        return BlockMerger.readingOrder(merged, rightToLeft)
    }

    /**
     * Rectángulos dentro del globo que pasan por el centro del texto: primero
     * el de mayor área y después el más ancho para cada altura (de bajo y
     * ancho a alto y estrecho). El rotulador se queda con el que permita la
     * letra más grande para cada traducción.
     */
    fun inscribedRects(bubble: Bubble, cx: Int, cy: Int): List<Box> {
        if (!bubble.inside(cx, cy)) return emptyList()
        val b = bubble.bounds
        val top = b.top
        val rows = b.height
        val left = IntArray(rows)
        val right = IntArray(rows)
        var first = cy - top
        var last = cy - top
        fun run(row: Int): Boolean {
            val y = top + row
            if (!bubble.inside(cx, y)) return false
            var l = cx
            while (l - 1 >= b.left && bubble.inside(l - 1, y)) l--
            var r = cx
            while (r + 1 < b.right && bubble.inside(r + 1, y)) r++
            left[row] = l
            right[row] = r
            return true
        }
        run(first)
        while (first - 1 >= 0 && run(first - 1)) first--
        while (last + 1 < rows && run(last + 1)) last++

        // Todos los pares (fila de arriba, fila de abajo) alrededor del centro.
        val c = cy - top
        val span = last - first + 1
        val upLeft = IntArray(rows)
        val upRight = IntArray(rows)
        var l = Int.MIN_VALUE
        var r = Int.MAX_VALUE
        for (row in c downTo first) {
            l = max(l, left[row]); r = min(r, right[row])
            upLeft[row] = l; upRight[row] = r
        }
        var best: Box? = null
        var bestArea = 0L
        val widest = arrayOfNulls<Box>(HEIGHT_STEPS)
        var dl = Int.MIN_VALUE
        var dr = Int.MAX_VALUE
        for (bottom in c..last) {
            dl = max(dl, left[bottom]); dr = min(dr, right[bottom])
            for (topRow in c downTo first) {
                val width = min(dr, upRight[topRow]) - max(dl, upLeft[topRow]) + 1
                if (width <= 0) break
                val height = bottom - topRow + 1
                val rect = Box(max(dl, upLeft[topRow]), top + topRow, min(dr, upRight[topRow]) + 1, top + bottom + 1)
                val area = width.toLong() * height
                if (area > bestArea) {
                    bestArea = area
                    best = rect
                }
                val step = min(HEIGHT_STEPS - 1, (height - 1) * HEIGHT_STEPS / span)
                if (widest[step].let { it == null || it.width < width }) widest[step] = rect
            }
        }
        val options = listOfNotNull(best) + widest.filterNotNull().filter { it != best && it.height >= span / 5 }
        return options
    }

    private const val HEIGHT_STEPS = 8

    /**
     * Ninguna zona de escritura pisa a otra: si dos se cruzan, se corta por
     * el hueco que hay entre sus textos (en horizontal o en vertical).
     */
    fun separate(blocks: List<TextBlock>) {
        for (i in blocks.indices) for (j in i + 1 until blocks.size) {
            val a = blocks[i]
            val b = blocks[j]
            val ra = a.renderOptions.reduceOrNull(Box::union) ?: continue
            val rb = b.renderOptions.reduceOrNull(Box::union) ?: continue
            if (!ra.intersects(rb)) continue
            val (first, second) = if (a.box.centerX <= b.box.centerX) a to b else b to a
            val (upper, lower) = if (a.box.centerY <= b.box.centerY) a to b else b to a
            val gapX = second.box.left - first.box.right
            val gapY = lower.box.top - upper.box.bottom
            if (gapX >= gapY) {
                val cut = if (gapX >= 0) first.box.right + gapX / 2 else (first.box.centerX + second.box.centerX) / 2
                first.clipOptions { it.copy(right = min(it.right, cut)) }
                second.clipOptions { it.copy(left = max(it.left, cut)) }
            } else {
                val cut = if (gapY >= 0) upper.box.bottom + gapY / 2 else (upper.box.centerY + lower.box.centerY) / 2
                upper.clipOptions { it.copy(bottom = min(it.bottom, cut)) }
                lower.clipOptions { it.copy(top = max(it.top, cut)) }
            }
        }
    }

    private fun TextBlock.clipOptions(clip: (Box) -> Box) {
        val clipped = renderOptions.map(clip)
        renderOptions = clipped.filter { it.width >= 8 && it.height >= 8 }.ifEmpty { listOf(clipped.first()) }
    }
}
