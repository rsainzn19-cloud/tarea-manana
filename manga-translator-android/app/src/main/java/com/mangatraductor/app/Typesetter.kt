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
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.TextBlock
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Escribe cada traducción dentro de su globo con el mayor tamaño de letra que
 * quepa, pero sin que un globo tenga la letra mucho más grande que el resto
 * de la página (se ve más profesional, como un cómic rotulado a mano).
 */
class Typesetter(context: Context, private val uppercase: Boolean, language: String = "en") {

    private val typeface: Typeface =
        ResourcesCompat.getFont(context, R.font.comic_neue_bold) ?: Typeface.DEFAULT_BOLD
    private val locale: Locale = Locale.forLanguageTag(language)

    private class Job(val text: String, val left: Int, val top: Int, val width: Int, val height: Int, val fitted: Int?)

    fun draw(bitmap: Bitmap, blocks: List<TextBlock>) {
        val canvas = Canvas(bitmap)
        // Tamaños relativos a la página: ~40 px de máximo en una página de 900 px.
        val maxSize = max(16f, bitmap.width * 0.045f)
        val minSize = max(9f, bitmap.width * 0.01f)
        val paint = newPaint()

        val jobs = blocks.mapNotNull { block ->
            var text = block.translation.trim()
            if (text.isEmpty()) return@mapNotNull null
            if (uppercase) text = text.uppercase(locale)
            // De las formas posibles dentro del globo, la que deja la letra más grande
            // (a igualdad, la principal: es la que mejor abarca el texto original).
            val options = block.renderOptions.ifEmpty { listOf(block.box) }.map { box ->
                val margin = max(2, (min(box.width, box.height) * 0.04).toInt())
                val width = max(1, box.width - 2 * margin)
                val height = max(1, box.height - 2 * margin)
                Job(text, box.left + margin, box.top + margin, width, height, fitSize(text, paint, width, height, maxSize, minSize))
            }
            options.firstOrNull { it.fitted == options.maxOf { o -> o.fitted ?: -1 } } ?: options.first()
        }
        // Tope común: un globo grande con poco texto no lleva letra enorme.
        val sizes = jobs.mapNotNull { it.fitted }.sorted()
        val cap = if (sizes.size >= 3) max(minSize.toInt(), (sizes[sizes.size / 2] * 1.25f).toInt()) else maxSize.toInt()

        for (job in jobs) {
            val layout = if (job.fitted != null) {
                paint.textSize = min(job.fitted, cap).toFloat()
                build(job.text, paint, job.width, hyphenate = false)
            } else {
                // No cabe ni con la letra mínima: se escribe igual, partiendo palabras con guion.
                paint.textSize = minSize
                build(job.text, paint, job.width, hyphenate = true)
            }

            // Letra negra con borde blanco sobre fondo claro; al revés sobre fondo oscuro.
            val darkBackground = averageLuma(bitmap, job.left, job.top, job.width, job.height) < 110
            val fill = if (darkBackground) Color.WHITE else Color.BLACK
            val stroke = if (darkBackground) Color.BLACK else Color.WHITE

            canvas.save()
            canvas.translate(job.left.toFloat(), job.top + (job.height - layout.height) / 2f)
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = max(2f, paint.textSize / 6f)
            paint.color = stroke
            layout.draw(canvas)
            paint.style = Paint.Style.FILL
            paint.color = fill
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun newPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@Typesetter.typeface
        textLocale = locale
    }

    /** Búsqueda binaria del mayor tamaño que cabe en alto sin partir ninguna palabra (null si ninguno). */
    private fun fitSize(text: String, paint: TextPaint, width: Int, height: Int, maxSize: Float, minSize: Float): Int? {
        val words = text.split(Regex("\\s+"))
        var lo = minSize.toInt()
        var hi = maxSize.toInt()
        var best: Int? = null
        while (lo <= hi) {
            val size = (lo + hi) / 2
            paint.textSize = size.toFloat()
            val wordsFit = words.all { paint.measureText(it) <= width }
            if (wordsFit && build(text, paint, width, hyphenate = false).height <= height) {
                best = size
                lo = size + 1
            } else {
                hi = size - 1
            }
        }
        return best
    }

    private fun build(text: String, paint: TextPaint, width: Int, hyphenate: Boolean): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
            .setHyphenationFrequency(if (hyphenate) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE)
            .build()

    private fun averageLuma(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Int {
        var sum = 0L
        var n = 0
        val stepX = max(1, width / 12)
        val stepY = max(1, height / 12)
        var y = top
        while (y < min(bitmap.height, top + height)) {
            var x = left
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
