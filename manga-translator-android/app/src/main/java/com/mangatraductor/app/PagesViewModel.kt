package com.mangatraductor.app

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class PageStatus { WAITING, WORKING, DONE, ERROR }

data class PageItem(
    val id: Long,
    val name: String,
    val source: File,
    val width: Int,
    val height: Int,
    val status: PageStatus = PageStatus.WAITING,
    val progress: String = "En cola…",
    val result: File? = null,
    val texts: List<Pair<String, String>> = emptyList(),
    val version: Int = 0, // cambia al volver a traducir, para refrescar la imagen
    val showOriginal: Boolean = false,
)

sealed interface OcrState {
    data object Missing : OcrState
    data class Downloading(val percent: Int) : OcrState
    data class Failed(val message: String) : OcrState
    data object Ready : OcrState
}

class PagesViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _pages = MutableStateFlow<List<PageItem>>(emptyList())
    val pages: StateFlow<List<PageItem>> = _pages

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    private val ocrModel = OcrModel(app)
    private val _ocrState = MutableStateFlow(if (ocrModel.isDownloaded) OcrState.Ready else OcrState.Missing)
    val ocrState: StateFlow<OcrState> = _ocrState

    private val workDir = File(app.cacheDir, "pages").apply { mkdirs() }
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private var translator: PageTranslator? = null
    private var nextId = System.currentTimeMillis()

    init {
        // Las páginas se traducen de una en una, en segundo plano.
        viewModelScope.launch(Dispatchers.Default) {
            for (id in queue) process(id)
        }
    }

    /** Copia las imágenes elegidas a la caché (así no se pierde el acceso) y las pone en cola. */
    fun addImages(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                val id = nextId++
                val source = File(workDir, "src_$id")
                try {
                    app.contentResolver.openInputStream(uri)!!.use { input ->
                        source.outputStream().use { input.copyTo(it) }
                    }
                } catch (e: Exception) {
                    _messages.tryEmit("No se pudo abrir una de las imágenes.")
                    continue
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    source.delete()
                    _messages.tryEmit("Una de las imágenes no tiene un formato válido.")
                    continue
                }
                val item = PageItem(id, displayName(uri) ?: "pagina_$id", source, bounds.outWidth, bounds.outHeight)
                _pages.update { it + item }
                queue.send(id)
            }
        }
    }

    private fun process(id: Long) {
        val item = _pages.value.find { it.id == id } ?: return // la quitaron de la lista
        updatePage(id) { it.copy(status = PageStatus.WORKING, progress = "Empezando…") }
        try {
            val engine = translator ?: PageTranslator(app).also { translator = it }
            val output = File(workDir, "tr_$id.jpg")
            val page = engine.translate(item.source, output) { msg -> updatePage(id) { it.copy(progress = msg) } }
            updatePage(id) {
                it.copy(status = PageStatus.DONE, result = page.file, texts = page.texts,
                    version = it.version + 1, showOriginal = false)
            }
            page.note?.let { _messages.tryEmit(it) }
        } catch (e: OutOfMemoryError) {
            updatePage(id) { it.copy(status = PageStatus.ERROR, progress = "La imagen es demasiado grande para la memoria del móvil.") }
        } catch (e: Exception) {
            updatePage(id) { it.copy(status = PageStatus.ERROR, progress = "Error: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private fun updatePage(id: Long, change: (PageItem) -> PageItem) {
        _pages.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    fun toggleOriginal(id: Long) = updatePage(id) {
        if (it.result == null) it else it.copy(showOriginal = !it.showOriginal)
    }

    fun retry(id: Long) {
        updatePage(id) { it.copy(status = PageStatus.WAITING, progress = "En cola…") }
        queue.trySend(id)
    }

    fun remove(id: Long) {
        _pages.value.find { it.id == id }?.let { it.source.delete(); it.result?.delete() }
        _pages.update { list -> list.filterNot { it.id == id } }
    }

    fun clear() {
        _pages.value.forEach { it.source.delete(); it.result?.delete() }
        _pages.value = emptyList()
    }

    fun save(items: List<PageItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            var saved = 0
            for (item in items) {
                val file = item.result ?: continue
                try {
                    Gallery.save(app, file, item.name.substringBeforeLast('.') + "_traducida.jpg")
                    saved++
                } catch (e: Exception) {
                    _messages.tryEmit("No se pudo guardar ${item.name}: ${e.message}")
                }
            }
            _messages.tryEmit(if (saved == 1) "Página guardada en Imágenes/MangaTraductor." else "$saved páginas guardadas en Imágenes/MangaTraductor.")
        }
    }

    fun downloadOcrModel() {
        if (_ocrState.value is OcrState.Downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _ocrState.value = OcrState.Downloading(0)
            try {
                ocrModel.download { bytes ->
                    _ocrState.value = OcrState.Downloading((bytes * 100 / ocrModel.totalBytes).toInt())
                }
                _ocrState.value = OcrState.Ready
                _messages.tryEmit("manga-ocr listo: las próximas páginas se leerán con él.")
            } catch (e: Exception) {
                _ocrState.value = OcrState.Failed(e.message ?: "Error de red")
            }
        }
    }

    private fun displayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    override fun onCleared() {
        queue.close()
        translator?.close()
    }
}
