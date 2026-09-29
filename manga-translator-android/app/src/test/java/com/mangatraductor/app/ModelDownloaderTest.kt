package com.mangatraductor.app

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangatraductor.core.SourceLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Descargas de modelos (manga-ocr de la versión ligera, Qwen) con el gestor de descargas simulado. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ModelDownloaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(DownloadManager::class.java)

    private fun requests() = (0L..shadowOf(manager).requestCount.toLong() + 10)
        .mapNotNull { id -> shadowOf(manager).getRequest(id)?.let { id to shadowOf(it) } }

    @Test
    fun enqueuesBothFilesFromGithubFirstAndWaitsForWifi() {
        ModelDownloader(context, OcrModel(context)).start(allowMetered = false)
        val reqs = requests()
        assertEquals(2, reqs.size)
        for ((_, r) in reqs) {
            assertTrue(r.uri.toString().startsWith(OcrModel.SOURCES[0]))
            assertTrue(r.destination.toString().endsWith("manga-ocr/" + r.uri.lastPathSegment + DownloadableModel.TMP_SUFFIX))
            assertFalse(r.allowedOverMetered)
            assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE, r.notificationVisibility)
        }
    }

    @Test
    fun reportsProgressAndFallsBackToHuggingFaceWhenGithubFails() {
        val downloader = ModelDownloader(context, OcrModel(context))
        downloader.start(allowMetered = true)
        val first = requests()
        first.forEach { (_, r) ->
            r.status = DownloadManager.STATUS_RUNNING
            r.bytesSoFar = 29_000_000
        }
        assertEquals(DownloadState.Downloading(49), downloader.poll())

        // Falla el primer servidor: se prueba el siguiente, con el mismo permiso de datos.
        first[0].second.status = DownloadManager.STATUS_FAILED
        assertEquals(DownloadState.Downloading(0), downloader.poll())
        val second = requests().filter { (id, _) -> first.none { it.first == id } }
        assertEquals(2, second.size)
        second.forEach { (_, r) ->
            assertTrue(r.uri.toString().startsWith(OcrModel.SOURCES[1]))
            assertTrue(r.allowedOverMetered)
        }
    }

    @Test
    fun rejectsCorruptFilesAndGivesUpAfterTheLastServer() {
        val model = OcrModel(context)
        val downloader = ModelDownloader(context, model)
        downloader.start(allowMetered = true, source = model.sources.size - 1)
        // "Terminadas", pero con contenido que no es el modelo.
        for (f in model.files) model.tmpFile(f).apply {
            parentFile?.mkdirs()
            writeText("no es el modelo")
        }
        requests().forEach { (_, r) -> r.status = DownloadManager.STATUS_SUCCESSFUL }
        val state = downloader.poll()
        assertTrue(state is DownloadState.Failed)
        assertFalse(model.isDownloaded)
        assertFalse(downloader.isActive)
    }

    @Test
    fun qwenDownloadsEveryFileOfTheChosenSizeFromHuggingFace() {
        val model = QwenModel(context, QwenModel.Size.SMALL)
        ModelDownloader(context, model).start(allowMetered = false)
        val urls = requests().map { it.second.uri.toString() }.sorted()
        val base = "https://huggingface.co/onnx-community/Qwen3.5-2B-ONNX/resolve/b1fc7ca3afafcb8e4b13d29715a6b9ea5af1d1cb"
        assertEquals(
            listOf("onnx/decoder_model_merged_q4.onnx", "onnx/decoder_model_merged_q4.onnx_data",
                "onnx/embed_tokens_q4.onnx", "onnx/embed_tokens_q4.onnx_data", "tokenizer.json").map { "$base/$it" },
            urls,
        )
        // Cada tamaño en su carpeta, y el grande trae un archivo de pesos más.
        assertEquals("qwen3.5-2b", model.downloadDir.name)
        assertEquals(6, QwenModel(context, QwenModel.Size.LARGE).files.size)
        assertEquals("3,1 GB", DownloadableModel.sizeText(QwenModel(context, QwenModel.Size.LARGE).totalBytes))
    }

    @Test
    fun qualityModelsComeFromTheirPinnedHuggingFaceRevisions() {
        val model = QualityModels(context)
        ModelDownloader(context, model).start(allowMetered = false)
        assertEquals(
            listOf(
                "https://huggingface.co/mayocream/comic-text-detector-onnx/resolve/a5d67ec772adef819ef5b0e7aa701fcf4c8bf74a/comic-text-detector.onnx",
                "https://huggingface.co/mayocream/lama-manga-onnx/resolve/b55497aadbfcb9740e1ed16f008268d71b4f3f79/lama-manga.onnx",
            ),
            requests().map { it.second.uri.toString() }.sorted(),
        )
        assertEquals("302 MB", DownloadableModel.sizeText(model.totalBytes))
    }

    @Test
    fun qwenSeeingThePageAddsOnlyItsImageEncoder() {
        val model = QwenModel(context, QwenModel.Size.SMALL)
        assertEquals(5, model.files.size)
        Settings(context).qwenSeesPage = true
        assertEquals(7, model.files.size)
        // Con Qwen ya bajado sólo falta el codificador de imagen (218 MB).
        model.downloadDir.mkdirs()
        for (f in model.files.take(5)) java.io.RandomAccessFile(File(model.downloadDir, f.name), "rw").use { it.setLength(f.size) }
        assertTrue(model.hasLanguageModel)
        assertFalse(model.isDownloaded)
        assertEquals("218 MB", DownloadableModel.sizeText(model.missingBytes))
        ModelDownloader(context, model).start(allowMetered = false)
        assertEquals(
            listOf(
                "https://huggingface.co/onnx-community/Qwen3.5-2B-ONNX/resolve/b1fc7ca3afafcb8e4b13d29715a6b9ea5af1d1cb/onnx/vision_encoder_q4.onnx",
                "https://huggingface.co/onnx-community/Qwen3.5-2B-ONNX/resolve/b1fc7ca3afafcb8e4b13d29715a6b9ea5af1d1cb/onnx/vision_encoder_q4.onnx_data",
            ),
            requests().map { it.second.uri.toString() }.sorted(),
        )
        Settings(context).qwenSeesPage = false
        assertTrue(model.isDownloaded)
        model.delete()
    }

    @Test
    fun chineseAddsOnlyItsPaddleReader() {
        Settings(context).source = SourceLanguage.CHINESE
        val model = QualityModels(context)
        assertEquals(4, model.files.size)
        assertEquals("319 MB", DownloadableModel.sizeText(model.missingBytes))
        // Con el detector y LaMa ya bajados (de la v1.7) sólo falta el lector de chino.
        model.downloadDir.mkdirs()
        for (f in model.files.take(2)) java.io.RandomAccessFile(File(model.downloadDir, f.name), "rw").use { it.setLength(f.size) }
        assertTrue(model.hasDetectorAndLama)
        assertFalse(model.isDownloaded)
        ModelDownloader(context, model).start(allowMetered = false)
        assertEquals(
            listOf(
                "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/ed152b8b495f84de93cda5709d768548a9127622/inference.onnx",
                "https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx/resolve/ed152b8b495f84de93cda5709d768548a9127622/inference.yml",
            ),
            requests().map { it.second.uri.toString() }.sorted(),
        )
        assertEquals("17 MB", DownloadableModel.sizeText(model.missingBytes))
        Settings(context).source = SourceLanguage.KOREAN
        assertEquals(listOf("paddle-ko.onnx", "paddle-ko.yml"), model.files.drop(2).map { it.name })
        Settings(context).source = SourceLanguage.JAPANESE
        assertTrue(model.isDownloaded)
        model.delete()
    }

    @Test
    fun switchingModelCancelsTheOtherDownload() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = DownloadController(context, scope, QwenModel(context, QwenModel.Size.LARGE))
        controller.ensure()
        assertEquals(6, requests().size)
        controller.switchTo(QwenModel(context, QwenModel.Size.SMALL))
        assertEquals(DownloadState.Missing, controller.state.value)
        assertEquals(0, requests().size) // las 6 del tamaño grande se anularon
        scope.cancel()
    }

    @Test
    fun summarizesWaitingStates() {
        fun row(status: Int, reason: Int = 0) = ModelDownloader.DownloadRow(status, reason, 0)
        val total = 100L
        assertEquals(DownloadState.WaitingForWifi, ModelDownloader.summarize(
            listOf(row(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_QUEUED_FOR_WIFI), row(DownloadManager.STATUS_PENDING)),
            expected = 2, doneBytes = 0, totalBytes = total))
        assertEquals(DownloadState.WaitingForNetwork, ModelDownloader.summarize(
            listOf(row(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK)),
            expected = 1, doneBytes = 0, totalBytes = total))
        assertEquals(DownloadState.Ready, ModelDownloader.summarize(
            listOf(row(DownloadManager.STATUS_SUCCESSFUL)), expected = 1, doneBytes = 0, totalBytes = total))
        // El usuario canceló una descarga desde la notificación.
        assertTrue(ModelDownloader.summarize(listOf(row(DownloadManager.STATUS_RUNNING)),
            expected = 2, doneBytes = 0, totalBytes = total) is DownloadState.Failed)
        val noSpace = ModelDownloader.summarize(
            listOf(row(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE)),
            expected = 1, doneBytes = 0, totalBytes = 2_000_000_000)
        assertTrue((noSpace as DownloadState.Failed).message.contains("2,1 GB"))
    }
}
