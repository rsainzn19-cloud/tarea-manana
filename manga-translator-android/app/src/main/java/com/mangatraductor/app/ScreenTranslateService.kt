package com.mangatraductor.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Servicio en segundo plano del botón flotante. Mientras está activo:
 *  - hay una notificación fija (con el botón «Detener»),
 *  - la pantalla se captura continuamente (MediaProjection),
 *  - un botón flotante aparece encima de las demás apps.
 * Al tocar el botón se hace una captura, se traduce y la traducción se
 * superpone en su sitio. Tocar la traducción la cierra.
 */
class ScreenTranslateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlayContext: Context
    private lateinit var windowManager: WindowManager

    private var projection: MediaProjection? = null
    private var capture: ScreenCapture? = null
    private var bubble: FloatingBubble? = null
    private var overlay: TranslationOverlay? = null
    private var busy = false

    /** Por dónde va la traducción (para decirlo si se vuelve a tocar el botón). */
    @Volatile private var progress = ""

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // El usuario dejó de compartir la pantalla (desde la barra de estado, al bloquear...).
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Desde Android 11 las ventanas flotantes de un servicio deben crearse con un
        // "contexto de ventana" para tener las medidas correctas de la pantalla.
        overlayContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            this
        }
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                // Si ya estaba activo, se usa el permiso nuevo en lugar del anterior.
                stopCapture()
                // Primero el servicio en primer plano: Android 14 lo exige antes de usar el permiso.
                startInForeground()
                try {
                    startCapture(resultCode, data ?: error("falta el permiso de captura"))
                } catch (e: Exception) {
                    toast("No se pudo iniciar la captura de pantalla: ${e.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (bubble == null) {
                    try {
                        showBubble()
                    } catch (e: RuntimeException) {
                        // Sin el permiso «Mostrar sobre otras apps» no se puede dibujar el botón.
                        toast(getString(R.string.overlay_permission_missing))
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                runningState.value = true
                MangaApp.from(this).prefetchTranslation()
                MangaApp.from(this).ocr.ensure()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenTranslateService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = manager.getMediaProjection(resultCode, data) ?: error("permiso de captura no válido")
        // Android 14 exige registrar el callback antes de crear la pantalla virtual.
        p.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = p
        val (w, h) = screenSize()
        capture = ScreenCapture(p, w, h, resources.displayMetrics.densityDpi)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    private fun stopCapture() {
        overlay?.close()
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        capture?.close()
        capture = null
        projection?.let {
            it.unregisterCallback(projectionCallback)
            it.stop()
        }
        projection = null
    }

    /** Al girar el móvil cambia el tamaño de la pantalla: ajustar la captura y el botón. */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) onScreenSizeChanged()
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private fun onScreenSizeChanged() {
        val capture = capture ?: return
        val (w, h) = screenSize()
        if (w == capture.width && h == capture.height) return
        capture.resize(w, h, resources.displayMetrics.densityDpi)
        overlay?.close() // la traducción ya no coincidiría con la pantalla
        bubble?.onScreenChanged()
    }

    private fun showBubble() {
        val newBubble = FloatingBubble(
            overlayContext, windowManager, ::screenSize,
            onTap = ::translateScreen,
            onLongPress = {
                toast(getString(R.string.bubble_stopped))
                stopSelf()
            },
        )
        newBubble.show()
        bubble = newBubble
    }

    /** Tamaño real de la pantalla en píxeles (incluidas las barras del sistema). */
    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private fun translateScreen() {
        val capture = capture ?: return
        val bubble = bubble ?: return
        if (busy) {
            toast("${getString(R.string.still_translating)} $progress".trim())
            return
        }
        busy = true
        scope.launch {
            try {
                overlay?.close()
                onScreenSizeChanged() // por si se giró el móvil sin avisar
                // Ocultar el botón y dejar que la pantalla se redibuje sin él antes de la foto.
                bubble.setHidden(true)
                delay(180)
                val since = capture.frameCount
                val shot = withContext(Dispatchers.IO) { capture.grab(after = since, timeoutMs = 250) }
                bubble.setHidden(false)
                if (shot == null) {
                    toast(getString(R.string.capture_failed))
                    return@launch
                }
                bubble.setBusy(true)
                val result = try {
                    progress = ""
                    MangaApp.from(this@ScreenTranslateService).engine.use { it.translate(shot) { step -> progress = step } }
                } finally {
                    shot.recycle()
                }
                if (result.blocks.isEmpty()) {
                    result.bitmap.recycle()
                    toast(getString(R.string.no_japanese_on_screen))
                    return@launch
                }
                result.note?.let(::toast)
                showOverlay(result)
            } catch (e: Exception) {
                toast("Error al traducir: ${e.message ?: e.javaClass.simpleName}")
            } catch (e: OutOfMemoryError) {
                toast("No hay memoria suficiente para traducir la pantalla.")
            } finally {
                busy = false
                this@ScreenTranslateService.bubble?.let {
                    it.setBusy(false)
                    it.setHidden(false)
                }
            }
        }
    }

    private fun showOverlay(result: TranslatedImage) {
        val bitmap = result.bitmap
        overlay = TranslationOverlay(
            overlayContext, windowManager, bitmap, result.blocks,
            onClosed = {
                overlay = null
                bitmap.recycle()
            },
            onLongPress = { saveScreenshot(bitmap) },
        ).also { it.show() }
        val prefs = getSharedPreferences("burbuja", MODE_PRIVATE)
        val shown = prefs.getInt("avisos_cerrar", 0)
        if (shown < 3) {
            toast(getString(R.string.overlay_hint))
            prefs.edit { putInt("avisos_cerrar", shown + 1) }
        }
    }

    /** Guarda en la galería la captura con la traducción. */
    private fun saveScreenshot(bitmap: Bitmap) {
        if (Gallery.needsPermission &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            toast(getString(R.string.save_needs_permission))
            return
        }
        // Copia propia: la original se libera en cuanto se cierra la traducción.
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val name = "captura_traducida_${System.currentTimeMillis()}.jpg"
                    val file = File(cacheDir, name)
                    file.outputStream().use { copy.compress(Bitmap.CompressFormat.JPEG, 93, it) }
                    Gallery.save(this@ScreenTranslateService, file, name)
                    file.delete()
                }
                toast(getString(R.string.screenshot_saved))
            } catch (e: Exception) {
                toast("No se pudo guardar: ${e.message}")
            } finally {
                copy.recycle()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        stopCapture()
        bubble?.remove()
        bubble = null
        runningState.value = false
        super.onDestroy()
    }

    private fun toast(message: String) = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val CHANNEL_ID = "traductor_pantalla"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.mangatraductor.app.INICIAR"
        private const val ACTION_STOP = "com.mangatraductor.app.DETENER"
        private const val EXTRA_RESULT_CODE = "codigo"
        private const val EXTRA_DATA = "permiso"

        private val runningState = MutableStateFlow(false)

        /** Si el botón flotante está activo. */
        val isRunning: StateFlow<Boolean> get() = runningState

        /** Arranca el servicio con el permiso de captura recién concedido por el usuario. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenTranslateService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenTranslateService::class.java))
        }
    }
}
