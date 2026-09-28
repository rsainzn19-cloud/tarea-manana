package com.mangatraductor.app

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Descarga de manga-ocr de la versión ligera, con el gestor de descargas simulado. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OcrDownloaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(DownloadManager::class.java)

    private fun requests() = (0L..shadowOf(manager).requestCount.toLong() + 10)
        .mapNotNull { id -> shadowOf(manager).getRequest(id)?.let { id to shadowOf(it) } }

    @Test
    fun enqueuesBothFilesFromGithubFirstAndWaitsForWifi() {
        OcrDownloader(context).start(allowMetered = false)
        val reqs = requests()
        assertEquals(2, reqs.size)
        for ((_, r) in reqs) {
            assertTrue(r.uri.toString().startsWith(OcrModel.SOURCES[0]))
            assertTrue(r.destination.toString().endsWith(OcrModel.TMP_SUFFIX))
            assertFalse(r.allowedOverMetered)
            assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE, r.notificationVisibility)
        }
    }

    @Test
    fun reportsProgressAndFallsBackToHuggingFaceWhenGithubFails() {
        val downloader = OcrDownloader(context)
        downloader.start(allowMetered = true)
        val first = requests()
        first.forEach { (_, r) ->
            r.status = DownloadManager.STATUS_RUNNING
            r.bytesSoFar = 29_000_000
        }
        assertEquals(OcrState.Downloading(49), downloader.poll())

        // Falla el primer servidor: se prueba el siguiente, con el mismo permiso de datos.
        first[0].second.status = DownloadManager.STATUS_FAILED
        assertEquals(OcrState.Downloading(0), downloader.poll())
        val second = requests().filter { (id, _) -> first.none { it.first == id } }
        assertEquals(2, second.size)
        second.forEach { (_, r) ->
            assertTrue(r.uri.toString().startsWith(OcrModel.SOURCES[1]))
            assertTrue(r.allowedOverMetered)
        }
    }

    @Test
    fun rejectsCorruptFilesAndGivesUpAfterTheLastServer() {
        val downloader = OcrDownloader(context)
        val model = OcrModel(context)
        downloader.start(allowMetered = true, source = OcrModel.SOURCES.size - 1)
        // "Terminadas", pero con contenido que no es el modelo.
        for (f in model.files) File(model.downloadDir, f.name + OcrModel.TMP_SUFFIX).apply {
            parentFile?.mkdirs()
            writeText("no es el modelo")
        }
        requests().forEach { (_, r) -> r.status = DownloadManager.STATUS_SUCCESSFUL }
        val state = downloader.poll()
        assertTrue(state is OcrState.Failed)
        assertFalse(model.isDownloaded)
        assertFalse(downloader.isActive)
    }

    @Test
    fun summarizesWaitingStates() {
        fun row(status: Int, reason: Int = 0) = OcrDownloader.DownloadRow(status, reason, 0)
        val total = 100L
        assertEquals(OcrState.WaitingForWifi, OcrDownloader.summarize(
            listOf(row(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_QUEUED_FOR_WIFI), row(DownloadManager.STATUS_PENDING)),
            expected = 2, doneBytes = 0, totalBytes = total))
        assertEquals(OcrState.WaitingForNetwork, OcrDownloader.summarize(
            listOf(row(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK)),
            expected = 1, doneBytes = 0, totalBytes = total))
        assertEquals(OcrState.Ready, OcrDownloader.summarize(
            listOf(row(DownloadManager.STATUS_SUCCESSFUL)), expected = 1, doneBytes = 0, totalBytes = total))
        // El usuario canceló una descarga desde la notificación.
        assertTrue(OcrDownloader.summarize(listOf(row(DownloadManager.STATUS_RUNNING)),
            expected = 2, doneBytes = 0, totalBytes = total) is OcrState.Failed)
        val noSpace = OcrDownloader.summarize(
            listOf(row(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_INSUFFICIENT_SPACE)),
            expected = 1, doneBytes = 0, totalBytes = total)
        assertTrue((noSpace as OcrState.Failed).message.contains("espacio"))
    }
}
