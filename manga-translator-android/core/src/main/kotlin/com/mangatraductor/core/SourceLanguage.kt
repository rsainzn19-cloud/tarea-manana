package com.mangatraductor.core

/** Idioma del cómic que se traduce. */
enum class SourceLanguage(
    val code: String,
    /** Nombre en inglés (para las instrucciones de las IAs). */
    val englishName: String,
    /** Cómo se llama el cómic en ese idioma (manga, manhua, manhwa). */
    val comic: String,
    /** Las viñetas y los globos se leen de derecha a izquierda. */
    val rightToLeft: Boolean,
    /** Caracteres de su escritura: un bloque sin ninguno no es texto que traducir. */
    val script: Regex,
) {
    JAPANESE("ja", "Japanese", "manga", rightToLeft = true,
        Regex("[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uff66-\\uff9f]")),
    CHINESE("zh", "Chinese", "manhua", rightToLeft = false,
        Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]")),
    KOREAN("ko", "Korean", "manhwa", rightToLeft = false,
        Regex("[\\uac00-\\ud7af\\u1100-\\u11ff\\u3130-\\u318f]"));

    companion object {
        fun from(code: String): SourceLanguage = entries.firstOrNull { it.code == code } ?: JAPANESE
    }
}
