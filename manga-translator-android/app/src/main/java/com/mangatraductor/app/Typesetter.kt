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
import kotlin.math.max
import kotlin.math.min

/** Escribe cada traducción dentro de su globo con el mayor tamaño de letra que quepa. */
class Typesetter(context: Context, private val uppercase: Boolean) {

    private val typeface: Typeface =
        ResourcesCompat.getFont(context, R.font.comic_neue_bold) ?: Typeface.DEFAULT_BOLD

    fun draw(bitmap: Bitmap, blocks: List<TextBlock>) {
        val canvas = Canvas(bitmap)
        // Tamaños relativos a la página: ~40 px de máximo en una página de 900 px.
        val maxSize = max(16f, bitmap.width * 0.045f)
        val minSize = max(9f, bitmap.width * 0.01f)

        for (block in blocks) {
            var text = block.translation.trim()
            if (text.isEmpty()) continue
            if (uppercase) text = text.uppercase()

            val box = block.renderBox ?: block.box
            val margin = max(2, (min(box.width, box.height) * 0.04).toInt())
            val left = box.left + margin
            val top = box.top + margin
            val width = max(1, box.width - 2 * margin)
            val height = max(1, box.height - 2 * margin)

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = this@Typesetter.typeface }
            val layout = fit(text, paint, width, height, maxSize, minSize)

            // Letra negra con borde blanco sobre fondo claro; al revés sobre fondo oscuro.
            val darkBackground = averageLuma(bitmap, left, top, width, height) < 110
            val fill = if (darkBackground) Color.WHITE else Color.BLACK
            val stroke = if (darkBackground) Color.BLACK else Color.WHITE

            canvas.save()
            canvas.translate(left.toFloat(), top + (height - layout.height) / 2f)
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

    /** Búsqueda binaria del tamaño: cabe en alto y ninguna palabra se parte. */
    private fun fit(text: String, paint: TextPaint, width: Int, height: Int, maxSize: Float, minSize: Float): StaticLayout {
        val words = text.split(Regex("\\s+"))
        var lo = minSize.toInt()
        var hi = maxSize.toInt()
        var best: StaticLayout? = null
        while (lo <= hi) {
            val size = (lo + hi) / 2
            paint.textSize = size.toFloat()
            val layout = build(text, paint, width)
            val wordsFit = words.all { paint.measureText(it) <= width }
            if (wordsFit && layout.height <= height) {
                best = layout
                lo = size + 1
            } else {
                hi = size - 1
            }
        }
        if (best != null) {
            paint.textSize = best.paint.textSize
            return best
        }
        paint.textSize = minSize // no cabe ni con la letra mínima: se escribe igual
        return build(text, paint, width)
    }

    private fun build(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
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
