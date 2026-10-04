package com.mangatraductor.app

import android.content.Context
import androidx.core.content.edit
import com.mangatraductor.core.SourceLanguage

/** Preferencias del usuario (se guardan en el almacenamiento privado de la app). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** Idioma del cómic: japonés (manga), chino (manhua) o coreano (manhwa). */
    var source: SourceLanguage
        get() = SourceLanguage.from(prefs.getString("idioma_origen", "ja") ?: "ja")
        set(v) = prefs.edit { putString("idioma_origen", v.code) }

    /**
     * ¿Las páginas de este idioma se leen de derecha a izquierda? El japonés
     * siempre; el chino, por defecto (manga traducido); el coreano no (manhwa).
     */
    fun readsRightToLeft(source: SourceLanguage): Boolean =
        source == SourceLanguage.JAPANESE || prefs.getBoolean("derecha_izquierda_${source.code}", source == SourceLanguage.CHINESE)

    fun setReadsRightToLeft(source: SourceLanguage, value: Boolean) =
        prefs.edit { putBoolean("derecha_izquierda_${source.code}", value) }

    var language: String
        get() = prefs.getString("idioma", "en") ?: "en"
        set(v) = prefs.edit { putString("idioma", v) }

    var engine: String
        get() = prefs.getString("motor", ENGINE_MLKIT) ?: ENGINE_MLKIT
        set(v) = prefs.edit { putString("motor", v) }

    var claudeKey: String
        get() = prefs.getString("clave_claude", "") ?: ""
        set(v) = prefs.edit { putString("clave_claude", v.trim()) }

    var uppercase: Boolean
        get() = prefs.getBoolean("mayusculas", true)
        set(v) = prefs.edit { putBoolean("mayusculas", v) }

    var useMangaOcr: Boolean
        get() = prefs.getBoolean("usar_manga_ocr", true)
        set(v) = prefs.edit { putBoolean("usar_manga_ocr", v) }

    /** Clave gratuita de Google AI Studio para Gemini en la nube. */
    var geminiKey: String
        get() = prefs.getString("clave_gemini", "") ?: ""
        set(v) = prefs.edit { putString("clave_gemini", v.trim()) }

    /** Pasar a la IA la memoria de las páginas anteriores. */
    var rememberStory: Boolean
        get() = prefs.getBoolean("recordar_historia", true)
        set(v) = prefs.edit { putBoolean("recordar_historia", v) }

    /** Tamaño de Qwen que se descarga (el grande traduce mejor; el pequeño va más rápido). */
    var qwenSize: QwenModel.Size
        get() = QwenModel.Size.from(prefs.getString("tamano_qwen", QwenModel.Size.LARGE.id) ?: "")
        set(v) = prefs.edit { putString("tamano_qwen", v.id) }

    /** Que Qwen vea también la página (más lento; se descarga su codificador de imagen, ~220 MB). */
    var qwenSeesPage: Boolean
        get() = prefs.getBoolean("qwen_ve_pagina", false)
        set(v) = prefs.edit { putBoolean("qwen_ve_pagina", v) }

    /** Usar comic-text-detector y LaMa (se descargan, ~300 MB). */
    var useQualityModels: Boolean
        get() = prefs.getBoolean("modelos_calidad", true)
        set(v) = prefs.edit { putBoolean("modelos_calidad", v) }

    /** Letra de los diálogos: cómic (Comic Neue) o a mano (Patrick Hand). */
    var font: String
        get() = prefs.getString("letra", FONT_HAND) ?: FONT_HAND
        set(v) = prefs.edit { putString("letra", v) }

    /** Ya no enseñar el consejo de usar un motor de IA. */
    var engineHintDismissed: Boolean
        get() = prefs.getBoolean("consejo_motor_visto", false)
        set(v) = prefs.edit { putBoolean("consejo_motor_visto", v) }

    companion object {
        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_QWEN = "qwen"
        const val FONT_COMIC = "comic"
        const val FONT_HAND = "a_mano"
        const val ENGINE_GEMINI_NANO = "gemini_nano"
        const val ENGINE_GEMINI_API = "gemini_api"
        const val ENGINE_CLAUDE = "claude"
    }
}

/** Nombre del idioma del cómic para la interfaz. */
fun sourceName(source: SourceLanguage): Int = when (source) {
    SourceLanguage.JAPANESE -> R.string.source_ja
    SourceLanguage.CHINESE -> R.string.source_zh
    SourceLanguage.KOREAN -> R.string.source_ko
}
