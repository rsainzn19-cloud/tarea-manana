package com.mangatraductor.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Pantalla transparente y sin contenido: mientras está abierta la app cuenta
 * como "en primer plano" (lo que exige Gemini Nano) pero se sigue viendo la
 * app de detrás (p. ej. el navegador). Se cierra sola al terminar.
 */
class ForegroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ForegroundBridge.created(this)
    }

    override fun onResume() {
        super.onResume()
        ForegroundBridge.resumed(this)
    }
}

object ForegroundBridge {
    private var waiting: CompletableDeferred<ForegroundActivity>? = null
    private var current: ForegroundActivity? = null

    /** Abre la pantalla transparente, ejecuta [block] y la cierra. */
    suspend fun <T> run(context: Context, block: suspend () -> T): T {
        val ready = CompletableDeferred<ForegroundActivity>()
        withContext(Dispatchers.Main) {
            waiting = ready
            context.startActivity(
                Intent(context, ForegroundActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            )
        }
        val activity = withTimeout(5_000) { ready.await() }
        try {
            return block()
        } finally {
            withContext(Dispatchers.Main) {
                activity.finish()
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
                if (current === activity) current = null
                if (waiting === ready) waiting = null
            }
        }
    }

    internal fun created(activity: ForegroundActivity) {
        // Si nadie la está esperando (p. ej. Android la recreó), no pinta nada: cerrar.
        if (waiting == null) activity.finish() else current = activity
    }

    internal fun resumed(activity: ForegroundActivity) {
        waiting?.complete(activity)
    }
}
