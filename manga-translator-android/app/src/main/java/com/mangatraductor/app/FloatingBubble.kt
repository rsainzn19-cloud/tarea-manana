package com.mangatraductor.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlin.math.abs

/**
 * Botón flotante que se dibuja encima de las demás apps (p. ej. el navegador).
 * Se arrastra con el dedo y al soltarlo se pega al borde más cercano.
 * Un toque llama a [onTap]; una pulsación larga, a [onLongPress].
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingBubble(
    private val context: Context,
    private val windowManager: WindowManager,
    private val screenSize: () -> Pair<Int, Int>,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val size = (56 * density).toInt()
    private val prefs = context.getSharedPreferences("burbuja", Context.MODE_PRIVATE)

    private val icon = ImageView(context).apply {
        setImageResource(R.drawable.ic_translate)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val spinner = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        visibility = View.GONE
    }

    val view: FrameLayout = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.brand))
            setStroke((2 * density).toInt(), Color.WHITE)
        }
        elevation = 6 * density
        contentDescription = context.getString(R.string.bubble_description)
        val iconSize = (30 * density).toInt()
        addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        val spinnerSize = (36 * density).toInt()
        addView(spinner, FrameLayout.LayoutParams(spinnerSize, spinnerSize, Gravity.CENTER))
    }

    val params = WindowManager.LayoutParams(
        size, size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val (w, h) = screenSize()
        x = prefs.getInt("x", w - size)
        y = prefs.getInt("y", h / 3)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressed = false
    private val longPress = Runnable {
        longPressed = true
        onLongPress()
    }

    init {
        view.setOnTouchListener { _, event -> handleTouch(event) }
    }

    fun show() {
        clampToScreen()
        windowManager.addView(view, params)
    }

    fun remove() {
        view.removeCallbacks(longPress)
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // ya no estaba en pantalla
        }
    }

    /** Oculta el botón sin quitarlo (para que no salga en la captura). */
    fun setHidden(hidden: Boolean) {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun setBusy(busy: Boolean) {
        icon.visibility = if (busy) View.GONE else View.VISIBLE
        spinner.visibility = if (busy) View.VISIBLE else View.GONE
    }

    /** Recoloca el botón dentro de la pantalla (p. ej. tras girar el móvil). */
    fun onScreenChanged() {
        if (view.parent == null) return
        snapToEdge()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                longPressed = false
                view.postDelayed(longPress, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && abs(dx) + abs(dy) > touchSlop) {
                    dragging = true
                    view.removeCallbacks(longPress)
                }
                if (dragging && !longPressed) {
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                }
            }
            MotionEvent.ACTION_UP -> {
                view.removeCallbacks(longPress)
                when {
                    dragging -> snapToEdge()
                    !longPressed -> onTap()
                }
            }
            MotionEvent.ACTION_CANCEL -> view.removeCallbacks(longPress)
        }
        return true
    }

    private fun snapToEdge() {
        val (w, _) = screenSize()
        params.x = if (params.x + size / 2 < w / 2) 0 else w - size
        clampToScreen()
        if (view.parent != null) windowManager.updateViewLayout(view, params)
        prefs.edit { putInt("x", params.x).putInt("y", params.y) }
    }

    private fun clampToScreen() {
        val (w, h) = screenSize()
        params.x = params.x.coerceIn(0, (w - size).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (h - size).coerceAtLeast(0))
    }
}
