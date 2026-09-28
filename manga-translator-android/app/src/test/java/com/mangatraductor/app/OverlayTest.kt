package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/** Botón flotante y capa de traducción, con un Android simulado (Robolectric). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test
    fun bubbleTapTranslatesDragSnapsToEdgeAndLongPressStops() {
        var taps = 0
        var longPresses = 0
        val bubble = FloatingBubble(
            context, context.getSystemService(WindowManager::class.java), { 1080 to 2400 },
            onTap = { taps++ }, onLongPress = { longPresses++ },
        )
        bubble.show()
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, bubble.params.type)

        // Un toque: traducir.
        touch(bubble.view, MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(bubble.view, MotionEvent.ACTION_UP, 20f, 20f)
        assertEquals(1, taps)

        // Arrastrar hacia la izquierda: no es un toque y al soltar se pega al borde izquierdo.
        touch(bubble.view, MotionEvent.ACTION_DOWN, 20f, 20f)
        touch(bubble.view, MotionEvent.ACTION_MOVE, -700f, 320f)
        touch(bubble.view, MotionEvent.ACTION_UP, -700f, 320f)
        assertEquals(1, taps)
        assertEquals(0, bubble.params.x)
        assertEquals(2400 / 3 + 300, bubble.params.y)

        // Pulsación larga: desactivar (y no cuenta como toque).
        touch(bubble.view, MotionEvent.ACTION_DOWN, 20f, 20f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        touch(bubble.view, MotionEvent.ACTION_UP, 20f, 20f)
        assertEquals(1, longPresses)
        assertEquals(1, taps)

        // Mientras se captura la pantalla el botón se oculta.
        bubble.setHidden(true)
        assertEquals(View.INVISIBLE, bubble.view.visibility)
        bubble.remove()
    }

    @Test
    fun overlayDrawsOnlyTheTranslatedBubbles() {
        val translated = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val view = PatchView(context, translated, listOf(Rect(10, 10, 30, 30)))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 100, 100)
        val screen = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(screen))
        assertEquals(Color.RED, screen.getPixel(20, 20))        // dentro del globo: la traducción
        assertEquals(Color.TRANSPARENT, screen.getPixel(60, 60)) // fuera: se ve la pantalla real
    }
}
