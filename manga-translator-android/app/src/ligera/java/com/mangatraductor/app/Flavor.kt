package com.mangatraductor.app

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.TextRecognizer

/**
 * Versión ligera (cabe en 30 MB): el OCR de ML Kit lo aporta Google Play
 * Services y manga-ocr se descarga la primera vez que se abre la app.
 */
internal object Flavor {
    const val MANGA_OCR_IN_APK = false

    /**
     * Pide a Google Play Services el modelo de OCR japonés si aún no lo tiene y
     * espera a que termine (en apps instaladas fuera de Play Store no se instala solo).
     * Debe llamarse fuera del hilo principal.
     */
    fun prepareDetector(context: Context, recognizer: TextRecognizer) {
        val modules = ModuleInstall.getClient(context)
        fun available() = Tasks.await(modules.areModulesAvailable(recognizer)).areModulesAvailable()
        if (available()) return
        Tasks.await(modules.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()))
        // La petición vuelve enseguida; la descarga sigue en segundo plano.
        val deadline = System.currentTimeMillis() + 3 * 60_000
        while (!available()) {
            check(System.currentTimeMillis() < deadline) {
                "Google Play Services no terminó de descargar el OCR japonés. Revisa la conexión y reintenta."
            }
            Thread.sleep(1000)
        }
    }
}
