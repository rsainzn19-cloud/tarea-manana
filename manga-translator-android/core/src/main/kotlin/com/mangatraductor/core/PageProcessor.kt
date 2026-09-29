package com.mangatraductor.core

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/** Resultado de procesar una página: la imagen sin el texto original y los bloques traducidos. */
class PageResult(val cleaned: PixelImage, val blocks: List<TextBlock>)

/**
 * Página ya leída y lista para traducir: sus textos en orden de lectura, y el
 * borrado del original ya en marcha (se hace mientras se traduce).
 */
class PreparedPage internal constructor(
    val image: PixelImage,
    val blocks: List<TextBlock>,
    internal val cleaning: CompletableFuture<PixelImage>?,
) {
    val texts: List<String> get() = blocks.map { it.text }
    val boxes: List<Box> get() = blocks.map { it.box }

    /** Si al final no se traduce (error), se para el borrado. */
    fun cancel() {
        cleaning?.cancel(false)
    }
}

/**
 * Pasos independientes de Android: agrupar -> afinar máscara -> unir los
 * trozos de un mismo globo -> OCR -> (borrar mientras se) traduce -> calcular
 * dónde escribir. El dibujo del texto final lo hace la app (necesita las
 * fuentes y el Canvas de Android).
 *
 * @param reader lector de texto (manga-ocr, sólo japonés); si es null se usa el texto del detector.
 */
class PageProcessor(
    private val reader: TextReader?,
    private val translator: Translator,
    private val source: SourceLanguage = SourceLanguage.JAPANESE,
    private val inpainter: Inpainter = SimpleInpainter,
) {

    /**
     * Una página de principio a fin.
     *
     * @param layout lo que vio el detector de manga (comic-text-detector), si está:
     *   agrupa los textos por bloque y da la máscara exacta de las letras.
     * @param pageImage la página para los motores que la ven, dibujada a partir
     *   de las cajas de los textos (la app pone el número de cada uno).
     */
    fun process(
        image: PixelImage,
        detections: List<DetectedText>,
        layout: TextLayout? = null,
        pageImage: ((List<Box>) -> ByteArray?)? = null,
        story: StoryContext? = null,
        onProgress: (String) -> Unit = {},
    ): PageResult {
        val page = prepare(image, detections, layout, onProgress)
        if (page.blocks.isEmpty()) return PageResult(image, emptyList())
        onProgress("Traduciendo…")
        val translations = try {
            translator.translatePages(listOf(PageTexts(page.texts, pageImage?.invoke(page.boxes))), story).single()
        } catch (e: Exception) {
            page.cancel()
            throw e
        }
        return finish(page, translations, story, onProgress)
    }

    /** Agrupa, afina las máscaras, lee el texto y empieza a borrarlo. */
    fun prepare(
        image: PixelImage,
        detections: List<DetectedText>,
        layout: TextLayout? = null,
        onProgress: (String) -> Unit = {},
    ): PreparedPage {
        var blocks = if (layout != null) {
            BlockMerger.mergeWithLayout(detections, layout, source.rightToLeft)
        } else {
            BlockMerger.merge(detections, source.rightToLeft)
        }
        Cleaner.refineBlocks(image, blocks, layout)
        blocks = Bubbles.mergeSameBubble(image, blocks, source.rightToLeft, layout)

        onProgress("Leyendo ${blocks.size} globos…")
        for (block in blocks) {
            block.text = reader?.read(image, block.box) ?: block.detectorText()
        }
        val withText = blocks.filter { source.script.containsMatchIn(it.text) }
        if (withText.isEmpty()) return PreparedPage(image, emptyList(), null)
        // El borrado (LaMa puede tardar unos segundos) se hace mientras la IA traduce.
        return PreparedPage(image, withText, CompletableFuture.supplyAsync { Cleaner.clean(image, withText, inpainter) })
    }

    /** Pone las traducciones, guarda la página en la memoria y calcula dónde escribir. */
    fun finish(
        page: PreparedPage,
        translations: List<Translation>,
        story: StoryContext? = null,
        onProgress: (String) -> Unit = {},
    ): PageResult {
        if (page.blocks.isEmpty()) return PageResult(page.image, emptyList())
        page.blocks.zip(translations).forEach { (block, t) ->
            block.translation = t.text
            // El original corregido por la IA (leyendo el globo) queda para la memoria y "Ver textos".
            t.original?.takeIf { source.script.containsMatchIn(it) }?.let { block.text = it }
            block.kind = t.kind ?: TextKind.DIALOGUE
        }
        // Lo que la IA marcó como ruido (vacío) no se borra ni se rotula.
        val kept = page.blocks.filter { it.translation.isNotBlank() }
        // La página pasa a formar parte de la memoria de la historia.
        story?.remember(kept.map { it.text }, kept.map { it.translation })

        onProgress("Rotulando…")
        val cleaned = try {
            page.cleaning!!.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
        Cleaner.restore(cleaned, page.image, page.blocks.filter { it.translation.isBlank() })
        Cleaner.findRenderBoxes(cleaned, kept)
        // Sin IA que lo diga: onomatopeya si es un texto corto en kana suelto sobre el dibujo.
        for ((block, t) in page.blocks.zip(translations)) {
            if (t.kind == null && !block.inBubble && SFX.matches(block.text)) block.kind = TextKind.SFX
        }
        return PageResult(cleaned, kept)
    }

    private companion object {
        /** Onomatopeya típica: pocas kana, alargamientos y exclamaciones. */
        val SFX = Regex("[\\u3040-\\u30ffー〜~！!？?…・っッ]{1,8}")
    }
}
