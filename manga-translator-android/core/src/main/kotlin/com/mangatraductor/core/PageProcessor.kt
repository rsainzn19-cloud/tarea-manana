package com.mangatraductor.core

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.math.min

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
 * @param reader lector de globos enteros (manga-ocr, sólo japonés); si es null se usa el texto del detector.
 * @param lineReader lector de cada línea del detector (PaddleOCR, en chino y coreano), si no hay [reader].
 * @param rightToLeft la página se lee de derecha a izquierda (manga). Si el texto va casi
 *   todo en columnas verticales también (un manga traducido al chino conserva el orden).
 */
class PageProcessor(
    private val reader: TextReader?,
    private val translator: Translator,
    private val source: SourceLanguage = SourceLanguage.JAPANESE,
    private val inpainter: Inpainter = SimpleInpainter,
    private val lineReader: LineReader? = null,
    private val rightToLeft: Boolean = source.rightToLeft,
) {

    /**
     * Una página de principio a fin.
     *
     * @param layout lo que vio el detector de manga (comic-text-detector), si está:
     *   agrupa los textos por bloque y da la máscara exacta de las letras.
     * @param pageImage la página para los motores que la ven, dibujada a partir
     *   de las cajas de los textos (la app pone el número de cada uno).
     * @param lines líneas de texto de otro detector (PaddleOCR, en chino y coreano),
     *   que se leen con el lector de líneas.
     */
    fun process(
        image: PixelImage,
        detections: List<DetectedText>,
        layout: TextLayout? = null,
        pageImage: ((List<Box>) -> ByteArray?)? = null,
        story: StoryContext? = null,
        lines: List<Box> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): PageResult {
        val page = prepare(image, detections, layout, lines, onProgress)
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
        lines: List<Box> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): PreparedPage {
        // Chino y coreano: cada línea (de ML Kit o de los detectores) la lee PaddleOCR.
        val found = lineReader?.let { readLines(image, it, detections, layout, lines) } ?: detections
        // Un manga traducido al chino con el texto en vertical se lee de derecha a izquierda.
        val rightToLeft = rightToLeft || mostlyVertical(found)
        var blocks = if (layout != null) {
            BlockMerger.mergeWithLayout(found, layout, rightToLeft, fitToLines = lineReader != null)
        } else {
            BlockMerger.merge(found, rightToLeft)
        }
        Cleaner.refineBlocks(image, blocks, layout)
        blocks = Bubbles.mergeSameBubble(image, blocks, rightToLeft, layout)

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

    internal companion object {
        /** Onomatopeya típica: pocas kana, alargamientos y exclamaciones. */
        private val SFX = Regex("[\\u3040-\\u30ffー〜~！!？?…・っッ]{1,8}")

        /** Seguridad mínima para quedarse con lo que leyó PaddleOCR (si no, el texto de ML Kit). */
        private const val MIN_CONFIDENCE = 0.6f

        /**
         * Las líneas de texto, leídas con [reader]. Pueden venir de ML Kit
         * ([detections]), del detector de manga ([layout], DBNet: muy fiable con
         * texto vertical) y del de PaddleOCR ([lines]: separa mejor las líneas
         * horizontales cortas y juntas). En cada bloque del detector de manga
         * (≈ un globo) se usan las de la fuente que se lee con más seguridad; fuera
         * de los bloques, todas sin repetir. Si PaddleOCR no está seguro de una
         * línea se queda el texto de ML Kit que haya en ella.
         */
        fun readLines(
            image: PixelImage,
            reader: LineReader,
            detections: List<DetectedText>,
            layout: TextLayout?,
            lines: List<Box>,
        ): List<DetectedText> {
            val readings = HashMap<Box, LineReading?>()
            fun reading(box: Box) = readings.getOrPut(box) { reader.read(image, box) }
            fun textOf(box: Box): String {
                reading(box)?.takeIf { it.confidence >= MIN_CONFIDENCE && it.text.isNotBlank() }?.let { return it.text }
                val vertical = box.height > box.width
                return detections.filter { box.overlapArea(it.box) > it.box.area / 2 }
                    .sortedBy { if (vertical) it.box.top else it.box.left }
                    .joinToString("") { it.text }
            }
            // Caracteres leídos con seguridad: lo que mejor lee el globo.
            fun score(option: List<Box>) = option.sumOf { box ->
                val r = reading(box)
                if (r == null || r.confidence < 0.5f) 0.0 else r.confidence.toDouble() * r.text.count { !it.isWhitespace() }
            }

            // Una "línea" que se lee mal puede ser varias pegadas (líneas cortas muy juntas):
            // se prueba a partirla y se queda lo que se lea mejor.
            fun refine(box: Box): List<Box> {
                if ((reading(box)?.confidence ?: 0f) >= 0.85f) return listOf(box)
                val parts = LineSplitter.split(image, box) ?: return listOf(box)
                return if (score(parts) > score(listOf(box))) parts else listOf(box)
            }

            // Sin repetir: de las que se solapan, la que se lee con más seguridad.
            fun distinct(boxes: List<Box>): List<Box> {
                val kept = mutableListOf<Box>()
                for (box in boxes.sortedWith(compareBy({ -(reading(it)?.confidence ?: 0f) }, { it.area }))) {
                    if (kept.none { it.overlapArea(box) > min(it.area, box.area) / 3 }) kept += box
                }
                return kept
            }

            val sources = listOf(lines, layout?.lines?.map { it.box }.orEmpty(), detections.map { it.box })
                .map { source -> distinct(source.flatMap(::refine)) }
            val used = HashSet<Box>()
            val out = mutableListOf<DetectedText>()
            for (group in layout?.blocks.orEmpty().map { it.box }) {
                val options = sources.map { source -> source.filter { it !in used && group.overlapArea(it) >= it.area / 2 } }
                    .filter { it.isNotEmpty() }
                if (options.isEmpty()) continue
                val best = options.maxBy { score(it) }
                out += best.map { DetectedText(it, textOf(it)) }
                options.forEach { used += it }
            }
            // Fuera de los bloques: todas las líneas, sin repetir las que se solapan (con ellas
            // o con las de los bloques).
            val rest = mutableListOf<Box>()
            val taken = out.map { it.box }
            for (source in sources) for (box in source) {
                if (box in used) continue
                if ((rest + taken).none { it.overlapArea(box) > min(it.area, box.area) / 3 }) rest += box
            }
            out += rest.map { DetectedText(it, textOf(it)) }
            // Lo que no se pudo leer (dibujo que parecía texto) no cuenta: sólo agrandaría el bloque.
            return out.filter { it.text.isNotBlank() }
        }

        /** ¿Casi todo el texto va en columnas verticales? (las líneas más altas que anchas) */
        fun mostlyVertical(detections: List<DetectedText>): Boolean {
            val vertical = detections.count { it.box.height > it.box.width * 1.3 }
            val horizontal = detections.count { it.box.width > it.box.height * 1.3 }
            return vertical > horizontal
        }
    }
}
