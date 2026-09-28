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

    companion object {
        const val ENGINE_MLKIT = "mlkit"
        const val ENGINE_GEMINI_NANO = "gemini_nano"
        const val ENGINE_CLAUDE = "claude"
    }
}
