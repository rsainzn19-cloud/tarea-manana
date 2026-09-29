package com.mangatraductor.core

/**
 * Formato sencillo para pedir traducciones a modelos pequeños (p. ej. Gemini
 * Nano): una línea por globo, "número: texto". Más robusto que JSON para un
 * modelo en el móvil.
 */
object NumberedLines {

    fun format(texts: List<String>, first: Int = 0): String =
        texts.withIndex().joinToString("\n") { (i, t) -> "${first + i}: ${t.replace('\n', ' ')}" }

    private val LINE = Regex("""^\s*[-*•]?\s*\[?(\d+)\]?\s*[:：.)、-]\s*(.*?)\s*$""")

    /**
     * Lee la respuesta y devuelve [count] traducciones en orden; las que falten
     * (o vengan vacías) quedan como cadena vacía. Si el modelo parte una
     * traducción en varias líneas, las líneas sin número se unen a la anterior.
     */
    fun parse(reply: String, count: Int): List<String> {
        val out = MutableList(count) { "" }
        var current = -1
        for (raw in reply.lines()) {
            val line = raw.trim().removePrefix("```").trim()
            if (line.isEmpty()) continue
            val match = LINE.matchEntire(line)
            if (match != null) {
                val id = match.groupValues[1].toInt()
                current = if (id in 0 until count && out[id].isEmpty()) id else -1
                if (current >= 0) out[current] = clean(match.groupValues[2])
            } else if (current >= 0) {
                out[current] = (out[current] + " " + clean(line)).trim()
            }
        }
        return out
    }

    private fun clean(s: String) = s.trim().removeSurrounding("\"").removeSurrounding("「", "」").trim()
}
