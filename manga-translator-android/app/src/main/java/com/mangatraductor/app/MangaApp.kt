package com.mangatraductor.app

import android.app.Application
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Falta y no se está descargando. */
    data object Missing : OcrState
    data class Downloading(val percent: Int) : OcrState
    /** En cola hasta que haya Wi-Fi (se puede forzar con datos móviles). */
    data object WaitingForWifi : OcrState
    data object WaitingForNetwork : OcrState
    data class Failed(val message: String) : OcrState
}

class MangaApp : Application() {

    /** Motor de traducción compartido por la lista de páginas y el botón flotante. */
    val engine by lazy { Engine(this) }

    /** Tareas de fondo de la app (sobreviven a las pantallas). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Ready)
    val ocrState: StateFlow<OcrState> = _ocrState

    private val downloader by lazy { OcrDownloader(this) }
    private var polling: Job? = null

    override fun onCreate() {
        super.onCreate()
        if (!OcrModel(this).isAvailable) {
            _ocrState.value = OcrState.Missing
            // Si ya había una descarga en marcha (la app se cerró), seguir su progreso.
            if (downloader.isActive) watchDownload()
        }
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

    /**
     * Versión ligera: asegura que manga-ocr se esté descargando. La primera vez
     * espera a tener Wi-Fi; con [downloadOcrNow] se descarga también con datos.
     */
    fun ensureOcr() {
        when {
            _ocrState.value is OcrState.Ready -> return
            downloader.isActive -> watchDownload()
            inProcess?.isActive == true -> return
            else -> startDownload(allowMetered = false)
        }
    }

    /** Descargar manga-ocr ya, aunque sea con datos móviles (botón de la app). */
    fun downloadOcrNow() = startDownload(allowMetered = true)

    private fun startDownload(allowMetered: Boolean) {
        try {
            downloader.start(allowMetered)
            _ocrState.value = OcrState.Downloading(0)
            watchDownload()
        } catch (e: Exception) {
            // Sin gestor de descargas en el móvil: se descarga desde la propia app.
            Log.w(TAG, "Gestor de descargas no disponible", e)
            downloadInProcess()
        }
    }

    private fun watchDownload() {
        if (polling?.isActive == true) return
        polling = scope.launch {
            while (true) {
                val state = try {
                    downloader.poll()
                } catch (e: Exception) {
                    OcrState.Failed(e.message ?: "error de descarga")
                }
                _ocrState.value = state
                if (state is OcrState.Ready || state is OcrState.Failed || state is OcrState.Missing) break
                delay(700)
            }
        }
    }

    private var inProcess: Job? = null

    private fun downloadInProcess() {
        if (inProcess?.isActive == true) return
        _ocrState.value = OcrState.Downloading(0)
        inProcess = scope.launch {
            try {
                val model = OcrModel(this@MangaApp)
                model.downloadInProcess { bytes ->
                    _ocrState.value = OcrState.Downloading((bytes * 100 / model.totalBytes).toInt().coerceAtMost(99))
                }
                _ocrState.value = OcrState.Ready
            } catch (e: Exception) {
                _ocrState.value = OcrState.Failed(e.message ?: "error de red")
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
