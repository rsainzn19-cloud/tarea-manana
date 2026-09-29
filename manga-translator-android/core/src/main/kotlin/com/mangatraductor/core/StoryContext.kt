package com.mangatraductor.core

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Memoria de la historia. Al traducir varias páginas seguidas se va guardando
 * un resumen de lo que pasa, los nombres de personajes y términos (para
 * traducirlos siempre igual) y las últimas frases, y se le pasa a la IA con
 * cada página nueva para que entienda el contexto.
 */
class StoryContext {
    var summary: String = ""
        private set
    val glossary: Map<String, String> get() = _glossary
    val recent: List<Pair<String, String>> get() = _recent.toList()
    var pages: Int = 0
        private set

    private val _glossary = LinkedHashMap<String, String>()
    private val _recent = ArrayDeque<Pair<String, String>>()

    val isEmpty: Boolean get() = summary.isBlank() && _glossary.isEmpty() && _recent.isEmpty()

    /** Guarda las frases de una página ya traducida. */
    fun remember(originals: List<String>, translations: List<String>) {
        originals.zip(translations).filter { (o, t) -> o.isNotBlank() && t.isNotBlank() }.forEach { _recent.addLast(it) }
        while (_recent.size > MAX_RECENT) _recent.removeFirst()
        pages++
    }

    /** Lo que la IA devuelve sobre la historia: el resumen actualizado y nombres nuevos. */
    fun update(newSummary: String?, newGlossary: Map<String, String>) {
        newSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { summary = it.take(MAX_SUMMARY_CHARS) }
        for ((original, translation) in newGlossary) {
            if (original.isBlank() || translation.isBlank()) continue
            _glossary.remove(original.trim())
            _glossary[original.trim()] = translation.trim()
        }
        while (_glossary.size > MAX_GLOSSARY) _glossary.remove(_glossary.keys.first())
    }

    /** Nombres corregidos a mano: sustituyen a todos los que había. */
    fun replaceGlossary(entries: Map<String, String>) {
        _glossary.clear()
        update(null, entries)
    }

    /**
     * Lee un glosario escrito a mano, una línea por nombre: "original = traducción"
     * (también vale "→", "->" o ":"). Las líneas que no se entienden se ignoran.
     */
    fun parseGlossary(text: String): Map<String, String> = text.lines().mapNotNull { line ->
        val parts = line.removePrefix("•").split(GLOSSARY_SEPARATOR, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0].trim() to parts[1].trim() else null
    }.toMap()

    /** Texto para la IA (en inglés, como el resto de las instrucciones). */
    fun describe(maxLines: Int = MAX_RECENT): String {
        if (isEmpty) return ""
        return buildString {
            if (summary.isNotBlank()) append("Story so far: ").append(summary).append('\n')
            if (_glossary.isNotEmpty()) {
                append("Names and terms (always translate them this way):\n")
                _glossary.forEach { (o, t) -> append("- ").append(o).append(" = ").append(t).append('\n') }
            }
            val lines = _recent.toList().takeLast(maxLines)
            if (lines.isNotEmpty()) {
                append("Previous lines, in order (original -> translation):\n")
                lines.forEach { (o, t) -> append("- ").append(o).append(" -> ").append(t).append('\n') }
            }
        }.trim()
    }

    fun toJson(): String = MAPPER.writeValueAsString(mapOf(
        "summary" to summary,
        "pages" to pages,
        "glossary" to _glossary.map { (o, t) -> mapOf("original" to o, "translation" to t) },
        "recent" to _recent.map { (o, t) -> mapOf("original" to o, "translation" to t) },
    ))

    companion object {
        const val MAX_RECENT = 40
        const val MAX_GLOSSARY = 60
        const val MAX_SUMMARY_CHARS = 1500
        private val GLOSSARY_SEPARATOR = Regex("\\s*(?:=|→|->|:)\\s*")
        private val MAPPER = ObjectMapper()

        fun fromJson(json: String): StoryContext {
            val node = MAPPER.readTree(json)
            return StoryContext().apply {
                summary = node.path("summary").asText("")
                pages = node.path("pages").asInt(0)
                node.path("glossary").forEach { _glossary[it.path("original").asText()] = it.path("translation").asText() }
                node.path("recent").forEach { _recent.addLast(it.path("original").asText() to it.path("translation").asText()) }
            }
        }
    }
}
