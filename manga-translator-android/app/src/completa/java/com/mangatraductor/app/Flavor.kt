package com.mangatraductor.app

import android.content.Context
import com.google.mlkit.vision.text.TextRecognizer

/** Versión completa: manga-ocr y el OCR de ML Kit van dentro del APK. */
internal object Flavor {
    const val MANGA_OCR_IN_APK = true

    /** El modelo de detección ya está en el APK: no hay nada que preparar. */
    @Suppress("UNUSED_PARAMETER")
    fun prepareDetector(context: Context, recognizer: TextRecognizer) = Unit
}
