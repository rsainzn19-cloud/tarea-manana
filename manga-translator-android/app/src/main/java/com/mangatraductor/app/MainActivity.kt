package com.mangatraductor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mangatraductor.app.databinding.ActivityMainBinding
import com.mangatraductor.app.databinding.DialogSettingsBinding
import com.mangatraductor.core.SourceLanguage
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: PagesViewModel by viewModels()
    private lateinit var adapter: PageAdapter
    private var pendingSave: List<PageItem>? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) vm.addImages(uris)
    }

    private var waitingOverlayPermission = false

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Con o sin permiso de notificaciones se puede seguir (sólo no se vería la notificación).
        continueFloatingSetup()
    }

    private val screenCaptureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            ScreenTranslateService.start(this, result.resultCode, data)
            toast(getString(R.string.floating_ready))
        } else {
            toast(getString(R.string.capture_denied))
        }
    }

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val items = pendingSave
        pendingSave = null
        if (granted && items != null) vm.save(items)
        else if (!granted) toast("Sin permiso no se puede guardar en la galería.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Android 15+ dibuja detrás de las barras del sistema: dejar su espacio.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }

        binding.toolbar.inflateMenu(R.menu.main)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> showSettings()
                R.id.action_story -> showStory()
                R.id.action_save_all -> save(vm.pages.value.filter { it.result != null })
                R.id.action_clear -> vm.clear()
            }
            true
        }

        adapter = PageAdapter(lifecycleScope, onTap = { vm.toggleOriginal(it.id) }, onLongPress = ::showPageActions)
        binding.pages.layoutManager = LinearLayoutManager(this)
        binding.pages.adapter = adapter
        binding.pages.post { adapter.targetWidth = binding.pages.width.coerceAtLeast(1) }

        binding.pick.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.ocrDownload.setOnClickListener { MangaApp.from(this).ocr.downloadNow() }
        binding.qwenDownload.setOnClickListener { MangaApp.from(this).qwen.downloadNow() }
        binding.qualityDownload.setOnClickListener { MangaApp.from(this).quality.downloadNow() }
        binding.engineHintChoose.setOnClickListener { showSettings() }
        binding.engineHintDismiss.setOnClickListener {
            Settings(this).engineHintDismissed = true
            updateEngineHint()
        }
        updateEngineHint()
        binding.floatingToggle.setOnClickListener {
            if (ScreenTranslateService.isRunning.value) ScreenTranslateService.stop(this) else startFloatingButton()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.pages.collect { pages ->
                        adapter.submitList(pages)
                        binding.empty.visibility = if (pages.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch { MangaApp.from(this@MainActivity).ocr.state.collect(::showOcrState) }
                launch { MangaApp.from(this@MainActivity).qwen.state.collect(::showQwenState) }
                launch { MangaApp.from(this@MainActivity).quality.state.collect(::showQualityState) }
                launch {
                    ScreenTranslateService.isRunning.collect { running ->
                        binding.floatingToggle.setText(if (running) R.string.floating_stop else R.string.floating_start)
                    }
                }
                launch { vm.messages.collect(::toast) }
            }
        }

        waitingOverlayPermission = savedInstanceState?.getBoolean(KEY_WAITING_OVERLAY) ?: false
        if (savedInstanceState == null) handleShare(intent)
        // Descargar ya lo que falte (diccionario de traducción y, en la versión
        // ligera, manga-ocr con Wi-Fi), para que no haya que esperar después.
        MangaApp.from(this).prefetchTranslation()
        MangaApp.from(this).ocr.ensure()
        if (Settings(this).engine == Settings.ENGINE_QWEN) MangaApp.from(this).qwen.ensure()
        if (Settings(this).useQualityModels) MangaApp.from(this).quality.ensure()
    }

    /** Tarjeta de la descarga de los modelos de calidad (si están activados). */
    private fun showQualityState(state: DownloadState) {
        val b = binding
        showDownload(b.qualityBanner, b.qualityBannerText, b.qualityProgress, b.qualityDownload, state,
            visible = Settings(this).useQualityModels) {
            when (state) {
                DownloadState.Ready -> ""
                DownloadState.Missing -> getString(R.string.quality_missing)
                is DownloadState.Downloading -> getString(R.string.quality_downloading, state.percent)
                DownloadState.WaitingForWifi -> getString(R.string.quality_waiting_wifi)
                DownloadState.WaitingForNetwork -> getString(R.string.quality_waiting_network)
                is DownloadState.Failed -> getString(R.string.quality_failed, state.message)
            }
        }
    }

    /** Tarjeta de la descarga de manga-ocr (sólo aparece en la versión ligera). */
    private fun showOcrState(state: DownloadState) {
        val b = binding
        showDownload(b.ocrBanner, b.ocrBannerText, b.ocrProgress, b.ocrDownload, state, visible = true) {
            when (state) {
                DownloadState.Ready -> ""
                DownloadState.Missing -> getString(R.string.ocr_missing)
                is DownloadState.Downloading -> getString(R.string.ocr_downloading, state.percent)
                DownloadState.WaitingForWifi -> getString(R.string.ocr_waiting_wifi)
                DownloadState.WaitingForNetwork -> getString(R.string.ocr_waiting_network)
                is DownloadState.Failed -> getString(R.string.ocr_failed, state.message)
            }
        }
    }

    /** Tarjeta de la descarga de Qwen (sólo si es el motor elegido). */
    private fun showQwenState(state: DownloadState) {
        val b = binding
        val model = MangaApp.from(this).qwen.model
        val name = (model as? QwenModel)?.size?.id?.uppercase().orEmpty()
        val size = DownloadableModel.sizeText(model.totalBytes)
        val visible = Settings(this).engine == Settings.ENGINE_QWEN
        showDownload(b.qwenBanner, b.qwenBannerText, b.qwenProgress, b.qwenDownload, state, visible) {
            when (state) {
                DownloadState.Ready -> ""
                DownloadState.Missing -> getString(R.string.qwen_missing, name, size)
                is DownloadState.Downloading -> getString(R.string.qwen_downloading, name, size, state.percent)
                DownloadState.WaitingForWifi -> getString(R.string.qwen_waiting_wifi, name, size)
                DownloadState.WaitingForNetwork -> getString(R.string.qwen_waiting_network)
                is DownloadState.Failed -> getString(R.string.qwen_failed, state.message)
            }
        }
    }

    private fun showDownload(
        card: View, text: TextView, progress: LinearProgressIndicator, button: Button,
        state: DownloadState, visible: Boolean, message: () -> String,
    ) {
        card.visibility = if (visible && state !is DownloadState.Ready) View.VISIBLE else View.GONE
        progress.visibility = if (state is DownloadState.Downloading) View.VISIBLE else View.GONE
        if (state is DownloadState.Downloading) progress.setProgressCompat(state.percent, true)
        text.text = message()
        button.visibility = when (state) {
            is DownloadState.Downloading, DownloadState.WaitingForNetwork, DownloadState.Ready -> View.GONE
            else -> View.VISIBLE
        }
        button.setText(when (state) {
            DownloadState.WaitingForWifi -> R.string.ocr_download_now
            is DownloadState.Failed -> R.string.ocr_retry
            else -> R.string.ocr_download
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING_OVERLAY, waitingOverlayPermission)
    }

    override fun onResume() {
        super.onResume()
        // Volvemos de los ajustes de «Mostrar sobre otras apps».
        if (waitingOverlayPermission) {
            waitingOverlayPermission = false
            if (SystemSettings.canDrawOverlays(this)) continueFloatingSetup()
        }
    }

    /** Pide (si faltan) los permisos del botón flotante y lo arranca. */
    private fun startFloatingButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueFloatingSetup()
        }
    }

    private fun continueFloatingSetup() {
        if (!SystemSettings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_text)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    waitingOverlayPermission = true
                    startActivity(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        val consent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Pantalla completa: el botón flotante tiene que ver cualquier app que esté abierta.
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        screenCaptureConsent.launch(consent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Imágenes recibidas con «Compartir» desde otra app. */
    private fun handleShare(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.addImages(listOf(it)) }
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.addImages(it) }
        }
    }

    private fun showSettings() {
        val settings = Settings(this)
        val d = DialogSettingsBinding.inflate(layoutInflater)
        d.source.check(when (settings.source) {
            SourceLanguage.CHINESE -> R.id.sourceZh
            SourceLanguage.KOREAN -> R.id.sourceKo
            SourceLanguage.JAPANESE -> R.id.sourceJa
        })
        d.language.check(if (settings.language == "es") R.id.langEs else R.id.langEn)
        d.engine.check(when (settings.engine) {
            Settings.ENGINE_CLAUDE -> R.id.engineClaude
            Settings.ENGINE_GEMINI_NANO -> R.id.engineGemini
            Settings.ENGINE_GEMINI_API -> R.id.engineGeminiApi
            Settings.ENGINE_QWEN -> R.id.engineQwen
            else -> R.id.engineMlkit
        })
        d.qwenSize.check(if (settings.qwenSize == QwenModel.Size.SMALL) R.id.qwenSmall else R.id.qwenLarge)
        if (!QwenModel.supported) {
            d.engineQwen.isEnabled = false
            d.engineQwen.text = "${getString(R.string.engine_qwen)}\n${getString(R.string.qwen_unsupported)}"
        }
        val ram = QwenModel.ramGb(this)
        val updateQwenHelp = {
            val size = if (d.qwenSize.checkedRadioButtonId == R.id.qwenSmall) QwenModel.Size.SMALL else QwenModel.Size.LARGE
            d.qwenHelp.text = if (ram < size.minRamGb) {
                getString(R.string.qwen_low_ram, ram, size.id.uppercase(), size.minRamGb)
            } else {
                getString(R.string.settings_qwen_help)
            }
        }
        updateQwenHelp()
        d.qwenSize.setOnCheckedChangeListener { _, _ -> updateQwenHelp() }
        d.apiKey.setText(settings.claudeKey)
        d.geminiKey.setText(settings.geminiKey)
        d.uppercase.isChecked = settings.uppercase
        d.useOcr.isChecked = settings.useMangaOcr
        d.useQuality.isChecked = settings.useQualityModels
        d.rememberStory.isChecked = settings.rememberStory
        val updateKeyVisibility = {
            val engine = d.engine.checkedRadioButtonId
            d.keyLayout.visibility = if (engine == R.id.engineClaude) View.VISIBLE else View.GONE
            d.geminiKeyBox.visibility = if (engine == R.id.engineGeminiApi) View.VISIBLE else View.GONE
            d.qwenBox.visibility = if (engine == R.id.engineQwen) View.VISIBLE else View.GONE
        }
        updateKeyVisibility()
        d.engine.setOnCheckedChangeListener { _, _ -> updateKeyVisibility() }
        // La clave de Gemini es gratis: se saca en Google AI Studio con una cuenta de Google.
        d.geminiGetKey.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_KEY_URL)))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_settings)
            .setView(d.root)
            .setPositiveButton(R.string.save) { _, _ ->
                settings.source = when (d.source.checkedRadioButtonId) {
                    R.id.sourceZh -> SourceLanguage.CHINESE
                    R.id.sourceKo -> SourceLanguage.KOREAN
                    else -> SourceLanguage.JAPANESE
                }
                settings.language = if (d.language.checkedRadioButtonId == R.id.langEs) "es" else "en"
                settings.engine = when (d.engine.checkedRadioButtonId) {
                    R.id.engineClaude -> Settings.ENGINE_CLAUDE
                    R.id.engineGemini -> Settings.ENGINE_GEMINI_NANO
                    R.id.engineGeminiApi -> Settings.ENGINE_GEMINI_API
                    R.id.engineQwen -> Settings.ENGINE_QWEN
                    else -> Settings.ENGINE_MLKIT
                }
                val previousQwen = settings.qwenSize
                settings.qwenSize = if (d.qwenSize.checkedRadioButtonId == R.id.qwenSmall) QwenModel.Size.SMALL else QwenModel.Size.LARGE
                applyQwenChoice(settings, previousQwen)
                settings.claudeKey = d.apiKey.text?.toString().orEmpty()
                settings.geminiKey = d.geminiKey.text?.toString().orEmpty()
                settings.uppercase = d.uppercase.isChecked
                settings.useMangaOcr = d.useOcr.isChecked
                settings.useQualityModels = d.useQuality.isChecked
                val quality = MangaApp.from(this).quality
                if (settings.useQualityModels) quality.ensure() else quality.cancel()
                showQualityState(quality.state.value)
                settings.rememberStory = d.rememberStory.isChecked
                if (settings.engine == Settings.ENGINE_GEMINI_API && settings.geminiKey.isBlank()) {
                    toast(getString(R.string.gemini_key_missing))
                }
                MangaApp.from(this).prefetchTranslation() // por si cambió el idioma
                if (settings.engine == Settings.ENGINE_GEMINI_NANO) {
                    // Comprobar (y, si hace falta, descargar) Gemini Nano en el móvil.
                    lifecycleScope.launch { toast(GeminiNano.prepare(MangaApp.from(this@MainActivity).scope)) }
                }
                updateEngineHint()
                toast("Ajustes guardados: se aplican a las próximas páginas (o usa «Volver a traducir»).")
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Consejo de usar un motor de IA mientras se use la traducción literal. */
    private fun updateEngineHint() {
        val settings = Settings(this)
        val show = settings.engine == Settings.ENGINE_MLKIT && !settings.engineHintDismissed
        binding.engineHint.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Qwen elegido: descargar el tamaño elegido (con Wi-Fi) y borrar el otro si
     * lo había, para no ocupar 1,5–3 GB de más. Otro motor: parar su descarga.
     */
    private fun applyQwenChoice(settings: Settings, previous: QwenModel.Size) {
        val downloads = MangaApp.from(this).qwen
        if (settings.engine != Settings.ENGINE_QWEN) {
            downloads.cancel()
        } else {
            downloads.switchTo(QwenModel(this, settings.qwenSize)) // anula la descarga del otro tamaño
            if (previous != settings.qwenSize) QwenModel(this, previous).delete()
            downloads.ensure()
        }
        showQwenState(downloads.state.value)
    }

    private fun showPageActions(item: PageItem) {
        val actions = buildList {
            if (item.texts.isNotEmpty()) add(getString(R.string.action_texts) to { showTexts(item) })
            if (item.result != null) {
                add(getString(R.string.action_share) to { share(item) })
                add(getString(R.string.action_save) to { save(listOf(item)) })
            }
            if (item.status == PageStatus.DONE || item.status == PageStatus.ERROR) {
                add(getString(R.string.action_retry) to { vm.retry(item.id) })
            }
            add(getString(R.string.action_remove) to { vm.remove(item.id) })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /** Lo que la app recuerda de la historia (resumen, nombres, últimas frases). */
    private fun showStory() {
        val stories = MangaApp.from(this).stories
        val story = stories.current
        val text = if (story.isEmpty) {
            getString(R.string.story_empty)
        } else {
            buildString {
                append(getString(R.string.story_pages, story.pages)).append("\n\n")
                if (story.summary.isNotBlank()) append(getString(R.string.story_summary)).append("\n").append(story.summary).append("\n\n")
                if (story.glossary.isNotEmpty()) {
                    append(getString(R.string.story_names)).append("\n")
                    story.glossary.forEach { (o, t) -> append("• ").append(o).append(" → ").append(t).append("\n") }
                }
            }.trim()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_story)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.story_new) { _, _ ->
                stories.reset()
                toast(getString(R.string.story_reset_done))
            }
            .show()
    }

    private fun showTexts(item: PageItem) {
        val text = item.texts.withIndex().joinToString("\n\n") { (i, t) -> "${i + 1}. ${t.first}\n→ ${t.second}" }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_texts)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun share(item: PageItem) {
        val file = item.result ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    private fun save(items: List<PageItem>) {
        if (items.isEmpty()) {
            toast("Todavía no hay páginas traducidas.")
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Gallery.needsPermission && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = items
            storagePermission.launch(permission)
        } else {
            vm.save(items)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val KEY_WAITING_OVERLAY = "esperando_permiso_superposicion"
        const val GEMINI_KEY_URL = "https://aistudio.google.com/apikey"
    }
}
