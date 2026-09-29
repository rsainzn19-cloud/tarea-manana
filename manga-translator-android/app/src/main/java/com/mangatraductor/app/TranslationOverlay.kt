package com.mangatraductor.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.mangatraductor.core.Box
import com.mangatraductor.core.TextBlock

/**
 * Muestra la traducción encima de la pantalla. Sólo se dibujan los trozos
 * cambiados (los globos), alineados píxel a píxel con la captura, así parece
 * que el texto se ha traducido "en su sitio". Un toque la cierra; una
 * pulsación larga llama a [onLongPress] (guardar la captura traducida).
 */
@SuppressLint("ClickableViewAccessibility")
class TranslationOverlay(
    context: Context,
    private val windowManager: WindowManager,
    val translated: Bitmap,
    blocks: List<TextBlock>,
    private val onClosed: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    val view = PatchView(context, translated, patchesFor(blocks))

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private var closed = false

    init {
        val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                close()
                return true
            }
            override fun onLongPress(e: MotionEvent) = this@TranslationOverlay.onLongPress()
        })
        view.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
    }

    fun show() = windowManager.addView(view, params)

    fun close() {
        if (closed) return
        closed = true
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // ya se había quitado
        }
        onClosed()
    }

    companion object {
        /** Zona que cambia en cada globo: el texto borrado y la traducción escrita. */
        fun patchesFor(blocks: List<TextBlock>): List<Rect> = blocks.map { block ->
            // Todas las zonas posibles: el rotulador escribe en la que mejor le va.
            val r = (block.renderOptions + block.box).reduce(Box::union).expand(4)
            Rect(r.left, r.top, r.right, r.bottom)
        }
    }
}

/** Dibuja [bitmap] sólo dentro de [patches], compensando dónde está la ventana en la pantalla. */
class PatchView(context: Context, private val bitmap: Bitmap, patches: List<Rect>) : View(context) {
    private val clip = Path().apply {
        for (r in patches) addRect(RectF(r), Path.Direction.CW)
    }
    private val location = IntArray(2)

    override fun onDraw(canvas: Canvas) {
        // La captura está en coordenadas de pantalla; la ventana puede no empezar
        // en (0, 0) (barra de estado, recorte de la cámara...), así que se compensa.
        getLocationOnScreen(location)
        canvas.save()
        canvas.translate(-location[0].toFloat(), -location[1].toFloat())
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
    }
}
