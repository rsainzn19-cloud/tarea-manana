package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.mangatraductor.core.Box
import com.mangatraductor.core.BubbleShape
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.TextBlock
import com.mangatraductor.core.TextKind
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Escribe cada traducción dentro de su globo, como un cómic rotulado:
 * - Las líneas siguen la forma del globo (más anchas en el centro de un globo
 *   ovalado) o, si da letra más grande, un rectángulo dentro de él.
 * - Ningún globo lleva la letra mucho más grande que el resto de la página.
 * - Las onomatopeyas van con letra de efecto, más grandes, y apiladas en
 *   vertical si el hueco es alto y estrecho.
 */
class Typesetter(context: Context, private val uppercase: Boolean, language: String = "en", font: String = Settings.FONT_HAND) {

    private val dialogueFace: Typeface = ResourcesCompat.getFont(
        context, if (font == Settings.FONT_HAND) R.font.patrick_hand else R.font.comic_neue_bold,
    ) ?: Typeface.DEFAULT_BOLD
    private val sfxFace: Typeface = ResourcesCompat.getFont(context, R.font.bangers) ?: Typeface.DEFAULT_BOLD
    private val locale: Locale = Locale.forLanguageTag(language)

    /** Interlineado: en mayúsculas no hay letras que bajen de la línea, así que va más junto. */
    private val spacing = if (uppercase) 0.9f else 1.0f

    /** Un texto ya colocado: su diseño y dónde va. */
    private class Placement(val layout: StaticLayout, val x: Float, val y: Float)

    /** Una zona donde puede ir el texto. */
    private fun interface Area {
        /** El texto a tamaño [size], o null si no cabe (o tendría que partir palabras). */
        fun place(text: String, paint: TextPaint, size: Float): Placement?
    }

    private class Job(val block: TextBlock, val text: String, val sfx: Boolean, val area: Area, val fitted: Int?)

    fun draw(bitmap: Bitmap, blocks: List<TextBlock>) {
        val canvas = Canvas(bitmap)
        // Tamaños relativos a la página: ~40 px de máximo en una página de 900 px.
        val maxSize = max(16f, bitmap.width * 0.045f)
        val minSize = max(9f, bitmap.width * 0.01f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textLocale = locale }

        val jobs = blocks.mapNotNull { block ->
            var text = block.translation.trim()
            if (text.isEmpty()) return@mapNotNull null
            val sfx = block.kind == TextKind.SFX
            if (uppercase || sfx) text = text.uppercase(locale)
            paint.typeface = if (sfx) sfxFace else dialogueFace
            val top = if (sfx) maxSize * 1.8f else maxSize
            val shown = if (sfx && isStacked(block, text)) stacked(text) else text
            // De las zonas posibles, la que deja la letra más grande (a igualdad, la primera).
            val fitted = areasFor(block, text, sfx).map { it to fitSize(it, shown, paint, top, minSize) }
            val best = fitted.maxByOrNull { it.second ?: -1 }!!
            Job(block, shown, sfx, best.first, best.second)
        }
        // Tope común para diálogos y narraciones: un globo grande con poco texto no lleva letra enorme.
        val sizes = jobs.filter { !it.sfx }.mapNotNull { it.fitted }.sorted()
        val cap = if (sizes.size >= 3) max(minSize.toInt(), (sizes[sizes.size / 2] * 1.5f).toInt()) else Int.MAX_VALUE

        for (job in jobs) {
            paint.typeface = if (job.sfx) sfxFace else dialogueFace
            val size = job.fitted?.let { if (job.sfx) it else min(it, cap) }
            val placement = size?.let { job.area.place(job.text, paint, it.toFloat()) }
                ?: fallback(job, paint, minSize)

            // Letra negra con borde blanco sobre fondo claro; al revés sobre fondo oscuro.
            val l = placement.layout
            val dark = averageLuma(bitmap, placement.x.toInt(), placement.y.toInt(), l.width, l.height) < 110
            canvas.save()
            canvas.translate(placement.x, placement.y)
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = max(2f, paint.textSize / if (job.sfx) 4f else 6f)
            paint.color = if (dark) Color.BLACK else Color.WHITE
            l.draw(canvas)
            paint.style = Paint.Style.FILL
            paint.color = if (dark) Color.WHITE else Color.BLACK
            l.draw(canvas)
            canvas.restore()
        }
    }

    /** No cabe ni con la letra mínima: se escribe igual en la zona principal, partiendo palabras con guion. */
    private fun fallback(job: Job, paint: TextPaint, minSize: Float): Placement {
        val box = inner(job.block.renderBox ?: job.block.box)
        paint.textSize = minSize
        val layout = build(job.text, paint, box.width, hyphenate = true, spacing = spacing)
        return Placement(layout, box.left.toFloat(), box.top + (box.height - layout.height) / 2f)
    }

    private fun areasFor(block: TextBlock, text: String, sfx: Boolean): List<Area> {
        val rects = block.renderOptions.ifEmpty { listOf(block.box) }.map { rectArea(inner(it)) }
        if (sfx) {
            // Onomatopeya: en todo su hueco (apilada si es alto y estrecho).
            val all = inner(block.renderOptions.fold(block.box, Box::union))
            return if (isStacked(block, text)) listOf(rectArea(all, lineSpacing = 0.85f)) else listOf(rectArea(all)) + rects
        }
        val shape = block.shape ?: return rects
        // Las líneas siguen el globo, sin salirse de la zona de este texto (si otro está al lado).
        val clip = block.renderOptions.fold(block.box, Box::union)
        return listOf(shapedArea(shape, clip)) + rects
    }

    /** Onomatopeya de una palabra en un hueco alto y estrecho: una letra debajo de otra. */
    private fun isStacked(block: TextBlock, text: String): Boolean {
        val b = block.box
        return b.height > b.width * 1.8 && ' ' !in text.trim() && text.length in 2..12
    }

    private fun stacked(text: String) = text.trim().toList().joinToString("\n")

    private fun inner(box: Box): Box {
        val margin = max(2, (min(box.width, box.height) * 0.04).toInt())
        return Box(box.left + margin, box.top + margin, max(box.left + margin + 1, box.right - margin), max(box.top + margin + 1, box.bottom - margin))
    }

    private fun rectArea(box: Box, lineSpacing: Float = spacing) = Area { text, paint, size ->
        paint.textSize = size
        val layout = build(text, paint, box.width, hyphenate = false, spacing = lineSpacing)
        if (layout.height > box.height || breaksWords(layout, text)) null
        else Placement(layout, box.left.toFloat(), box.top + (box.height - layout.height) / 2f)
    }

    /**
     * Líneas con la forma del globo: cada línea tan ancha como el globo a su
     * altura. Se prueba con n líneas, se miden sus anchos y se repite con las
     * que salgan hasta que coincidan.
     */
    private fun shapedArea(shape: BubbleShape, clip: Box) = Area { text, paint, size ->
        paint.textSize = size
        val fm = paint.fontMetricsInt
        val lineHeight = ((fm.descent - fm.ascent) * spacing).toInt().coerceAtLeast(1)
        val topLimit = max(shape.top, clip.top)
        val bottomLimit = min(shape.bottom, clip.bottom)
        val margin = max(2, (size * 0.25f).toInt())
        var lines = 1
        repeat(8) {
            val total = lines * lineHeight
            val top = (topLimit + bottomLimit) / 2 - total / 2
            if (top < topLimit + margin || top + total > bottomLimit - margin) return@Area null
            val spans = (0 until lines).map { i ->
                val span = shape.span(top + i * lineHeight, top + (i + 1) * lineHeight) ?: return@Area null
                val l = max(span.first + margin, clip.left + margin)
                val r = min(span.second - margin, clip.right - margin)
                if (r - l < size) return@Area null
                l to r
            }
            val left = spans.minOf { it.first }
            val right = spans.maxOf { it.second }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, right - left)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(0f, spacing)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setIndents(IntArray(lines) { spans[it].first - left }, IntArray(lines) { right - spans[it].second })
                .build()
            if (layout.lineCount == lines) {
                return@Area if (breaksWords(layout, text)) null
                else Placement(layout, left.toFloat(), top + (total - layout.height) / 2f)
            }
            lines = layout.lineCount
        }
        null
    }

    /** Búsqueda binaria del mayor tamaño que cabe en [area] (null si ni el mínimo). */
    private fun fitSize(area: Area, text: String, paint: TextPaint, maxSize: Float, minSize: Float): Int? {
        var lo = minSize.toInt()
        var hi = maxSize.toInt()
        var best: Int? = null
        while (lo <= hi) {
            val size = (lo + hi) / 2
            if (area.place(text, paint, size.toFloat()) != null) {
                best = size
                lo = size + 1
            } else {
                hi = size - 1
            }
        }
        return best
    }

    /**
     * ¿Alguna línea corta una palabra (o separa su puntuación, como "SIGH" +
     * "...")? Sólo se puede cortar en un espacio, un salto o tras un guion.
     */
    private fun breaksWords(layout: StaticLayout, text: String): Boolean {
        for (i in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(i)
            if (end !in 1 until text.length) continue
            val before = text[end - 1]
            if (!before.isWhitespace() && before != '-' && before != '—' && !text[end].isWhitespace()) return true
        }
        return false
    }

    private fun build(text: String, paint: TextPaint, width: Int, hyphenate: Boolean, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, spacing)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(if (hyphenate) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    private fun averageLuma(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Int {
        var sum = 0L
        var n = 0
        val stepX = max(1, width / 12)
        val stepY = max(1, height / 12)
        var y = max(0, top)
        while (y < min(bitmap.height, top + height)) {
            var x = max(0, left)
            while (x < min(bitmap.width, left + width)) {
                sum += PixelImage.luma(bitmap.getPixel(x, y))
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 255 else (sum / n).toInt()
    }
}
