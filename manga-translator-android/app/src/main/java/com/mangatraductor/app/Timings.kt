package com.mangatraductor.app

import java.util.Locale

/**
 * Cuánto tarda cada paso de una traducción, para verlo en «Ver textos» y en
 * la notificación del botón flotante (y saber qué es lo lento en cada móvil).
 */
class Timings {
    private val start = System.nanoTime()
    private val steps = LinkedHashMap<String, Long>()

    fun <T> measure(step: String, block: () -> T): T {
        val t = System.nanoTime()
        try {
            return block()
        } finally {
            add(step, System.nanoTime() - t)
        }
    }

    @Synchronized
    fun add(step: String, nanos: Long) {
        steps[step] = (steps[step] ?: 0L) + nanos
    }

    /** «4,2 s (buscar texto 1,1 · leer 0,9 · traducir 1,5 · borrar 0,4 · rotular 0,3)»; sin los pasos de menos de 0,05 s. */
    @Synchronized
    fun summary(): String {
        val parts = steps.filter { it.value >= 50_000_000L }.map { (step, nanos) -> "$step ${seconds(nanos)}" }
        val total = "${seconds(System.nanoTime() - start)} s"
        return if (parts.isEmpty()) total else "$total (${parts.joinToString(" · ")})"
    }

    companion object {
        const val LOAD = "cargar modelos"
        const val FIND = "buscar texto"
        const val READ = "leer"
        const val TRANSLATE = "traducir"
        const val CLEAN = "borrar"
        const val DRAW = "rotular"

        private fun seconds(nanos: Long) = String.format(Locale("es"), "%.1f", nanos / 1e9)
    }
}
