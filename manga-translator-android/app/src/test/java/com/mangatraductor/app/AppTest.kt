package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangatraductor.core.PageProcessor
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.SamplePage
import com.mangatraductor.core.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

/** Pruebas con un Android simulado (Robolectric), con dibujo real de píxeles. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppTest {

    private val english = listOf(
        "Good morning! Nice weather today, huh?",
        "We're gonna be late for school!",
        "Wait a sec, I forgot something!",
        "That afternoon...",
        "I'm starving... I want something to eat.",
    )

    @Test
    fun typesetterWritesEachTranslationInsideItsBubble() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixed = object : Translator {
            override fun translate(texts: List<String>, pageJpeg: ByteArray?) = english.take(texts.size)
        }
        val page = SamplePage.load()
        val result = PageProcessor(null, fixed).process(page, SamplePage.detections())
        assertEquals(5, result.blocks.size)

        val bitmap = Bitmap.createBitmap(result.cleaned.argb, page.width, page.height, Bitmap.Config.ARGB_8888)
            .copy(Bitmap.Config.ARGB_8888, true)
        Typesetter(context, uppercase = false).draw(bitmap, result.blocks)

        val drawn = IntArray(page.width * page.height)
        bitmap.getPixels(drawn, 0, page.width, 0, 0, page.width, page.height)
        for (block in result.blocks) {
            val r = block.renderBox!!
            var ink = 0
            for (y in r.top until r.bottom) for (x in r.left until r.right) {
                if (PixelImage.luma(drawn[y * page.width + x]) < 100) ink++
            }
            assertTrue("no se escribió «${block.translation}» en $r", ink > 200)
        }
        // Fuera de los globos la imagen no cambia.
        val untouched = 450 * page.width + 440 // el cuerpo del personaje del primer panel
        assertEquals(result.cleaned.argb[untouched], drawn[untouched])

        val out = File("build/test-output/typeset.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Página rotulada guardada en ${out.absolutePath}")
    }

    @Test
    fun ocrBundledInTheApkReadsABubble() {
        // Mismo código que en el móvil: el modelo se mapea desde los assets de la app.
        val context = ApplicationProvider.getApplicationContext<Context>()
        OcrModel(context).load().use { ocr ->
            val page = SamplePage.load()
            val first = com.mangatraductor.core.BlockMerger.merge(SamplePage.detections()).first()
            com.mangatraductor.core.Cleaner.refineBlocks(page, listOf(first))
            assertEquals("おはよう!今日はいい天気だね。", ocr.read(page, first.box))
        }
    }

    @Test
    fun mainScreenOpensWithEmptyListAndSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.empty).visibility)
                // El OCR ya viene dentro del APK y está la tarjeta del botón flotante.
                assertTrue(OcrModel(activity).isAvailable)
                val toggle = activity.findViewById<android.widget.Button>(R.id.floatingToggle)
                assertEquals(activity.getString(R.string.floating_start), toggle.text.toString())

                val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
                assertEquals(3, toolbar.menu.size())
                toolbar.menu.performIdentifierAction(R.id.action_settings, 0)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                assertTrue(dialog.isShowing)
                // Por defecto: traducción sin conexión, sin campo de clave.
                assertEquals(View.GONE, dialog.findViewById<View>(R.id.keyLayout)!!.visibility)
                dialog.findViewById<View>(R.id.engineClaude)!!.performClick()
                assertEquals(View.VISIBLE, dialog.findViewById<View>(R.id.keyLayout)!!.visibility)
            }
        }
    }
}
