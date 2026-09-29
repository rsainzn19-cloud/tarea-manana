package com.mangatraductor.app

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.mangatraductor.core.LANGUAGE_NAMES
import com.mangatraductor.core.NumberedLines
import com.mangatraductor.core.SourceLanguage
import com.mangatraductor.core.StoryContext
import com.mangatraductor.core.TranslationException
import com.mangatraductor.core.Translator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Traducción con Gemini Nano, la IA que viene dentro del móvil (Pixel 10 y
 * otros compatibles) a través de ML Kit GenAI. Funciona sin internet y sin
 * descargar nada en la app: el modelo lo gestiona Android (AICore).
 */
class GeminiNanoTranslator(private val context: Context, target: String, private val source: SourceLanguage) : Translator {

    private val model: GenerativeModel = GeminiNano.client
    private val language = LANGUAGE_NAMES[target] ?: target

    override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?): List<String> = runBlocking {
        GeminiNano.requireAvailable()
        // Memoria de la historia en versión corta: Gemini Nano acepta ~4000 tokens.
        val memory = story?.describe(maxLines = 8).orEmpty()
        // Por si la página tiene muchísimos globos, en tandas.
        texts.chunked(25).flatMap { chunk -> translateChunk(chunk, memory) }
    }

    private suspend fun translateChunk(texts: List<String>, memory: String): List<String> {
        val memoryBlock = if (memory.isEmpty()) "" else "Context from earlier pages of the same story (keep names the same):\n$memory\n\n"
        val prompt = memoryBlock + """
            Translate these ${source.englishName} ${source.comic} speech bubbles into natural, casual $language, like a published ${source.comic}.
            They are in reading order, a conversation: make the lines follow on from each other.
            The OCR may have small mistakes: fix them from context. Keep each translation short.
            Answer only with one line per bubble, in the form "number: translation", same numbers as below.

        """.trimIndent() + "\n" + NumberedLines.format(texts)
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0.2f
            topK = 16
            maxOutputTokens = 1024
        }
        val response = try {
            model.generateContent(request)
        } catch (e: GenAiException) {
            if (e.errorCode != GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED) throw GeminiNano.friendly(e)
            // Gemini Nano sólo responde a la app que está en primer plano: desde el
            // botón flotante (con el navegador delante) se abre un instante una
            // pantalla transparente de la app y se repite la petición.
            try {
                ForegroundBridge.run(context) { model.generateContent(request) }
            } catch (e2: GenAiException) {
                throw GeminiNano.friendly(e2)
            }
        }
        val reply = response.candidates.firstOrNull()?.text.orEmpty()
        return NumberedLines.parse(reply, texts.size)
    }
}

/** Estado de Gemini Nano en el móvil. */
object GeminiNano {
    val client: GenerativeModel by lazy { Generation.getClient() }

    /**
     * Comprueba si está listo; si hay que descargarlo, pide a Android que lo
     * descargue (en [scope], sin esperar). Devuelve un mensaje para el usuario.
     */
    suspend fun prepare(scope: CoroutineScope): String = try {
        when (client.checkStatus()) {
            FeatureStatus.AVAILABLE -> "Gemini Nano está listo en tu móvil."
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                scope.launch {
                    try {
                        client.download().collect { }
                    } catch (e: Exception) {
                        // Se reintentará la próxima vez; mientras, traducción sin conexión.
                    }
                }
                "Gemini Nano se está descargando en tu móvil. Mientras tanto se usa la traducción sin conexión."
            }
            else -> "Este móvil no tiene Gemini Nano disponible: se usará la traducción sin conexión."
        }
    } catch (e: GenAiException) {
        friendly(e).message.orEmpty()
    } catch (e: Exception) {
        "Gemini Nano no está disponible en este móvil: ${e.message}"
    } catch (e: LinkageError) {
        "Gemini Nano no está disponible en este móvil."
    }

    suspend fun requireAvailable() {
        val status = try {
            client.checkStatus()
        } catch (e: GenAiException) {
            throw friendly(e)
        }
        when (status) {
            FeatureStatus.AVAILABLE -> Unit
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                throw TranslationException("Gemini Nano todavía se está descargando en el móvil.")
            else -> throw TranslationException("Este móvil no tiene Gemini Nano.")
        }
    }

    fun friendly(e: GenAiException): TranslationException = TranslationException(
        when (e.errorCode) {
            GenAiException.ErrorCode.BUSY,
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> "Gemini Nano está ocupado; espera un momento."
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> "Android no dejó usar Gemini Nano en segundo plano."
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> "Gemini Nano necesita actualizar el sistema o AICore."
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> "No hay espacio para Gemini Nano."
            GenAiException.ErrorCode.NOT_AVAILABLE,
            GenAiException.ErrorCode.NOT_SUPPORTED,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> "Este móvil no tiene Gemini Nano."
            else -> "Gemini Nano falló (${e.errorCode})."
        },
        e,
    )
}
