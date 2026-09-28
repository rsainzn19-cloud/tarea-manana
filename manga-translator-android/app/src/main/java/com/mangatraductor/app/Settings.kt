package com.mangatraductor.app

import android.content.Context
import androidx.core.content.edit

/** Preferencias del usuario (se guardan en el almacenamiento privado de la app). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

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
        get() = prefs.getBoolean("mayusculas", false)
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

    companion object {
        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_QWEN = "qwen"
        const val ENGINE_GEMINI_NANO = "gemini_nano"
        const val ENGINE_GEMINI_API = "gemini_api"
        const val ENGINE_CLAUDE = "claude"
    }
}
