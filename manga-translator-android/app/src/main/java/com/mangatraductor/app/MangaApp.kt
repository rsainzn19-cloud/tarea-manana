package com.mangatraductor.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Estado del modelo manga-ocr (sólo cambia en la versión ligera, que lo descarga). */
sealed interface OcrState {
    data object Ready : OcrState
    /** Falta y no se está descargando (p. ej. con datos móviles, a la espera del usuario). */
    data object Missing : OcrState
    data class Downloading(val percent: Int) : OcrState
    data class Failed(val message: String) : OcrState
}

class MangaApp : Application() {

    /** Motor de traducción compartido por la lista de páginas y el botón flotante. */
    val engine by lazy { Engine(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Ready)
    val ocrState: StateFlow<OcrState> = _ocrState

    override fun onCreate() {
        super.onCreate()
        if (!OcrModel(this).isAvailable) _ocrState.value = OcrState.Missing
    }

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

    /** Descarga manga-ocr si falta (versión ligera). Sigue aunque se cierre la pantalla. */
    fun downloadOcr() {
        val state = _ocrState.value
        if (state is OcrState.Ready || state is OcrState.Downloading) return
        _ocrState.value = OcrState.Downloading(0)
        scope.launch {
            try {
                val model = OcrModel(this@MangaApp)
                model.download { bytes ->
                    _ocrState.value = OcrState.Downloading((bytes * 100 / model.totalBytes).toInt())
                }
                _ocrState.value = OcrState.Ready
            } catch (e: Exception) {
                _ocrState.value = OcrState.Failed(e.message ?: "error de red")
            }
        }
    }

    /** Con Wi-Fi descarga manga-ocr sola; con datos móviles espera a que el usuario lo pida. */
    fun downloadOcrIfUnmetered() {
        if (_ocrState.value !is OcrState.Missing) return
        val connectivity = getSystemService(ConnectivityManager::class.java)
        if (!connectivity.isActiveNetworkMetered) downloadOcr()
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
