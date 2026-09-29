package com.mangatraductor.core

/** Resultado de procesar una página: la imagen sin el texto original y los bloques traducidos. */
class PageResult(val cleaned: PixelImage, val blocks: List<TextBlock>)

/**
 * Pasos independientes de Android: agrupar -> afinar máscara -> unir los
 * trozos de un mismo globo -> OCR -> traducir -> borrar -> calcular dónde
 * escribir. El dibujo del texto final lo hace la app (necesita las fuentes y
 * el Canvas de Android).
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
     * @param layout lo que vio el detector de manga (comic-text-detector), si está:
     *   agrupa los textos por bloque y da la máscara exacta de las letras.
     * @param pageImage la página para los motores que la ven, dibujada a partir
     *   de las cajas de los textos (en orden; la app pone el número de cada uno).
     */
    fun process(
        image: PixelImage,
        detections: List<DetectedText>,
        layout: TextLayout? = null,
        pageImage: ((List<Box>) -> ByteArray?)? = null,
        story: StoryContext? = null,
        onProgress: (String) -> Unit = {},
    ): PageResult {
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
        if (withText.isEmpty()) return PageResult(image, emptyList())

        onProgress("Traduciendo…")
        val pageJpeg = pageImage?.invoke(withText.map { it.box })
        val translations = translator.translate(withText.map { it.text }, pageJpeg, story)
        withText.zip(translations).forEach { (block, t) -> block.translation = t }
        // Lo que la IA marcó como ruido (vacío) no se borra ni se rotula.
        val kept = withText.filter { it.translation.isNotBlank() }
        // La página pasa a formar parte de la memoria de la historia.
        story?.remember(kept.map { it.text }, kept.map { it.translation })

        onProgress("Rotulando…")
        val cleaned = Cleaner.clean(image, kept, inpainter)
        Cleaner.findRenderBoxes(cleaned, kept)
        return PageResult(cleaned, kept)
    }
}
