package com.mangatraductor.app

import android.content.Context
import android.util.Log
import com.mangatraductor.core.StoryContext
import java.io.File

/**
 * Guarda la memoria de la historia en los archivos privados de la app, para
 * que se conserve aunque se cierre la app o se lea con el botón flotante.
 * Se borra al empezar una historia nueva.
 */
class StoryStore(context: Context) {
    private val file = File(context.filesDir, "historia.json")

    /** La historia en curso. */
    @Volatile
    var current: StoryContext = load()
        private set

    private fun load(): StoryContext = try {
        if (file.exists()) StoryContext.fromJson(file.readText()) else StoryContext()
    } catch (e: Exception) {
        Log.w("MangaTraductor", "No se pudo leer la historia guardada", e)
        StoryContext()
    }

    /** Guarda [story] si sigue siendo la historia en curso (no se empezó otra entretanto). */
    @Synchronized
    fun save(story: StoryContext) {
        if (story !== current) return
        val tmp = File(file.path + ".tmp")
        tmp.writeText(story.toJson())
        if (!tmp.renameTo(file)) {
            file.writeText(story.toJson())
            tmp.delete()
        }
    }

    /** Olvidar todo y empezar una historia nueva. */
    @Synchronized
    fun reset() {
        current = StoryContext()
        file.delete()
    }
}
