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

class MangaApp : Application() {

    /** Motor de traducción compartido por la lista de páginas y el botón flotante. */
    val engine by lazy { Engine(this) }

    /** Memoria de la historia (páginas anteriores), compartida igual que el motor. */
    val stories by lazy { StoryStore(this) }

    /** Tareas de fondo de la app (sobreviven a las pantallas). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Descarga de manga-ocr (sólo en la versión ligera; la completa ya lo trae). */
    val ocr by lazy { DownloadController(this, scope, OcrModel(this)) }

    /** Descarga de Qwen (sólo si se elige como motor), del tamaño elegido en Ajustes. */
    val qwen by lazy { DownloadController(this, scope, QwenModel(this, Settings(this).qwenSize)) }

    override fun onCreate() {
        super.onCreate()
        // Si había descargas en marcha (la app se cerró), seguir su progreso.
        ocr.resume()
        qwen.resume()
    }

    /**
     * Descarga ya, en segundo plano, el diccionario de ML Kit (del idioma del
     * cómic al elegido) (≈30 MB, sólo la primera vez), para que la primera traducción no espere.
     */
    fun prefetchTranslation() {
        val settings = Settings(this)
        val language = settings.language
        val source = settings.source
        scope.launch {
            try {
                MlKitTranslator(source, language).use { it.ensureModel() }
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

/**
 * Descarga de un modelo en segundo plano, con su estado para la pantalla. La
 * primera vez espera al Wi-Fi; con [downloadNow] se descarga también con datos.
 */
class DownloadController(private val context: Context, private val scope: CoroutineScope, model: DownloadableModel) {

    var model: DownloadableModel = model
        private set
    private var downloader = ModelDownloader(context, model)

    private val _state = MutableStateFlow(if (model.isAvailable) DownloadState.Ready else DownloadState.Missing)
    val state: StateFlow<DownloadState> = _state

    private var polling: Job? = null
    private var inProcess: Job? = null

    /** Retoma el seguimiento de una descarga que ya estaba en marcha. */
    fun resume() {
        if (!model.isAvailable && downloader.isActive) watch()
    }

    /** Asegura que el modelo se esté descargando (si falta), esperando al Wi-Fi. */
    fun ensure() {
        when {
            model.isAvailable -> _state.value = DownloadState.Ready
            downloader.isActive -> watch()
            inProcess?.isActive == true -> return
            else -> start(allowMetered = false)
        }
    }

    /** Descargar ya, aunque sea con datos móviles (botón de la app). */
    fun downloadNow() = start(allowMetered = true)

    /** Cambia de modelo (p. ej. otro tamaño de Qwen): anula la descarga del anterior. */
    fun switchTo(newModel: DownloadableModel) {
        if (newModel.key == model.key) return
        cancel()
        model = newModel
        downloader = ModelDownloader(context, newModel)
        _state.value = if (newModel.isAvailable) DownloadState.Ready else DownloadState.Missing
        resume()
    }

    /** Anula la descarga en marcha. */
    fun cancel() {
        polling?.cancel()
        inProcess?.cancel()
        downloader.cancel()
        _state.value = if (model.isAvailable) DownloadState.Ready else DownloadState.Missing
    }

    private fun start(allowMetered: Boolean) {
        try {
            downloader.start(allowMetered)
            _state.value = DownloadState.Downloading(0)
            watch()
        } catch (e: Exception) {
            // Sin gestor de descargas en el móvil: se descarga desde la propia app.
            Log.w(TAG, "Gestor de descargas no disponible", e)
            downloadInProcess()
        }
    }

    private fun watch() {
        if (polling?.isActive == true) return
        val d = downloader
        polling = scope.launch {
            while (true) {
                val state = try {
                    d.poll()
                } catch (e: Exception) {
                    DownloadState.Failed(e.message ?: "error de descarga")
                }
                if (d !== downloader) break // se cambió de modelo
                _state.value = state
                if (state is DownloadState.Ready || state is DownloadState.Failed || state is DownloadState.Missing) break
                delay(700)
            }
        }
    }

    private fun downloadInProcess() {
        if (inProcess?.isActive == true) return
        _state.value = DownloadState.Downloading(0)
        val m = model
        inProcess = scope.launch {
            try {
                m.downloadInProcess { bytes ->
                    _state.value = DownloadState.Downloading((bytes * 100 / m.totalBytes).toInt().coerceAtMost(99))
                }
                _state.value = DownloadState.Ready
            } catch (e: Exception) {
                _state.value = DownloadState.Failed(e.message ?: "error de red")
            }
        }
    }

    private companion object {
        const val TAG = "MangaTraductor"
    }
}
