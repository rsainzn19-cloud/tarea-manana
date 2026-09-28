package com.mangatraductor.core

/** Resultado de procesar una página: la imagen sin japonés y los bloques traducidos. */
class PageResult(val cleaned: PixelImage, val blocks: List<TextBlock>)

/**
 * Pasos independientes de Android: agrupar -> afinar máscara -> OCR ->
 * traducir -> borrar -> calcular dónde escribir. El dibujo del texto final
 * lo hace la app (necesita las fuentes y el Canvas de Android).
 *
 * @param reader lector de texto (manga-ocr); si es null se usa el texto del detector.
 */
class PageProcessor(private val reader: TextReader?, private val translator: Translator) {

    fun process(
        image: PixelImage,
        detections: List<DetectedText>,
        pageJpeg: ByteArray? = null,
        story: StoryContext? = null,
        onProgress: (String) -> Unit = {},
    ): PageResult {
        val blocks = BlockMerger.merge(detections)
        Cleaner.refineBlocks(image, blocks)

        onProgress("Leyendo ${blocks.size} globos…")
        for (block in blocks) {
            block.text = reader?.read(image, block.box) ?: block.detectorText()
        }
        val withText = blocks.filter { JAPANESE.containsMatchIn(it.text) }
        if (withText.isEmpty()) return PageResult(image, emptyList())

        onProgress("Traduciendo…")
        val translations = translator.translate(withText.map { it.text }, pageJpeg, story)
        withText.zip(translations).forEach { (block, t) -> block.translation = t }
        // La página pasa a formar parte de la memoria de la historia.
        story?.remember(withText.map { it.text }, translations)

        onProgress("Rotulando…")
        val cleaned = Cleaner.clean(image, withText)
        Cleaner.findRenderBoxes(cleaned, withText)
        return PageResult(cleaned, withText)
    }

    companion object {
        /** Hiragana, katakana, kanji y katakana de ancho medio. */
        val JAPANESE = Regex("[\\u3040-\\u30ff\\u3400-\\u4dbf\\u4e00-\\u9fff\\uff66-\\uff9f]")
    }
}
