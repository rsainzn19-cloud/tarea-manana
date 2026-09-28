package com.mangatraductor.app

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MangaApp : Application() {

    /** Motor de traducción compartido por la lista de páginas y el botón flotante. */
    val engine by lazy { Engine(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Descarga ya, en segundo plano, el diccionario japonés de ML Kit del idioma
     * elegido (≈30 MB, sólo la primera vez), para que la primera traducción no espere.
     */
    fun prefetchTranslation() {
        val language = Settings(this).language
        scope.launch {
            try {
                MlKitTranslator(language).use { it.ensureModel() }
            } catch (e: Exception) {
                Log.w(TAG, "Todavía no se pudo descargar el diccionario", e)
            } catch (e: LinkageError) {
                Log.w(TAG, "ML Kit no está disponible", e)
            }
        }
    }

    companion object {
        private const val TAG = "MangaTraductor"

        fun from(context: Context) = context.applicationContext as MangaApp
    }
}

/**
 * Un único [PageTranslator] para todo el proceso: los modelos se cargan una
 * sola vez y las traducciones se hacen por turnos (una a la vez).
 */
class Engine(private val context: Context) {
    private val mutex = Mutex()
    private var translator: PageTranslator? = null

    suspend fun <T> use(block: (PageTranslator) -> T): T = mutex.withLock {
        withContext(Dispatchers.Default) {
            block(translator ?: PageTranslator(context).also { translator = it })
        }
    }
}
