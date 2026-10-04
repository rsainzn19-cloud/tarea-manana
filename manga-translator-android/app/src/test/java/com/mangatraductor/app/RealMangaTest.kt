package com.mangatraductor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mangatraductor.core.ComicTextDetector
import com.mangatraductor.core.LamaInpainter
import com.mangatraductor.core.LocalLlm
import com.mangatraductor.core.MangaOcr
import com.mangatraductor.core.OnnxPatcher
import com.mangatraductor.core.PaddleDetector
import com.mangatraductor.core.PaddleRecognizer
import com.mangatraductor.core.PageProcessor
import com.mangatraductor.core.PageTexts
import com.mangatraductor.core.PixelImage
import com.mangatraductor.core.QwenTranslator
import com.mangatraductor.core.SourceLanguage
import com.mangatraductor.core.StoryContext
import com.mangatraductor.core.TextKind
import com.mangatraductor.core.Translation
import com.mangatraductor.core.Translator
import com.mangatraductor.core.asLineReader
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Páginas de manga de verdad de principio a fin: detector de manga, manga-ocr
 * (o, con REAL_MANGA_SOURCE=zh/ko, los detectores y el lector de PaddleOCR),
 * borrado (LaMa), traducción (Qwen en el PC, si está) y rotulado de Android.
 * Deja las páginas traducidas en build/test-output/real/ para revisarlas.
 * Necesita REAL_MANGA_DIR, QUALITY_DIR y MANGA_OCR_DIR (QWEN_DIR opcional).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealMangaTest {

    private fun dir(property: String) = File(System.getProperty(property).orEmpty())

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, previous + if (a[i - 1] == b[j - 1]) 0 else 1)
                previous = t
            }
        }
        return d[b.length]
    }

    @Test
    fun translatesRealPages() {
        val pagesDir = dir("realMangaDir")
        val quality = dir("qualityDir")
        val ocrDir = dir("mangaOcrDir")
        assumeTrue("REAL_MANGA_DIR, QUALITY_DIR y MANGA_OCR_DIR", pagesDir.isDirectory &&
            File(quality, "comic-text-detector.onnx").exists() && File(ocrDir, "encoder_model_quantized.onnx").exists())
        val names = System.getProperty("realMangaPages").orEmpty().split(",").filter { it.isNotBlank() }
            .ifEmpty { pagesDir.list()!!.filter { it.endsWith(".png") }.sorted().take(3) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val out = File("build/test-output/real").apply { mkdirs() }

        val source = SourceLanguage.from(System.getProperty("realMangaSource").orEmpty().ifEmpty { "ja" })
        val paddle = if (source == SourceLanguage.JAPANESE) null else
            PaddleRecognizer.load(File(quality, "paddle-${source.code}.onnx"), File(quality, "paddle-${source.code}.yml"), 4)
        val paddleLines = if (source == SourceLanguage.JAPANESE) null else PaddleDetector.load(File(quality, "paddle-det.onnx"), 4)
        val vocab = File("src/main/assets/manga_ocr_vocab.txt").readLines()
        val ocr = MangaOcr.fromFiles(File(ocrDir, "encoder_model_quantized.onnx").path,
            File(ocrDir, "decoder_model_quantized.onnx").path, vocab, 4)
        val detector = ComicTextDetector.load(File(quality, "comic-text-detector.onnx"), 4)
        val lama = LamaInpainter.load(File(quality, "lama-manga.onnx"), 4)
        val qwenDir = dir("qwenDir")
        val llm = if (File(qwenDir, "decoder_model_merged_q4.onnx").exists()) {
            LocalLlm.load(File(qwenDir, "tokenizer.json"),
                OnnxPatcher.withInt8MatMul(File(qwenDir, "decoder_model_merged_q4.onnx")),
                File(qwenDir, "embed_tokens_q4.onnx"), 4)
        } else null
        // Sin Qwen: frases inglesas de largo parecido, para ver el rotulado.
        val filler = object : Translator {
            override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) =
                texts.map { t -> "This is where the translated line goes".split(" ").let { w -> w.take((t.length / 2).coerceIn(2, w.size)).joinToString(" ") } }
        }
        // Traducciones ya hechas (original<TAB>traducción), para revisar el rotulado sin esperar a Qwen.
        val saved = System.getProperty("realMangaTranslations").orEmpty().takeIf { it.isNotBlank() }?.let { path ->
            File(path).readLines().mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        }
        // "SFX:" delante marca una onomatopeya (lo que diría Gemini o Claude). El OCR puede
        // fallar algún carácter: vale el original más parecido (si se parece bastante).
        fun lookup(map: Map<String, String>, text: String): String? = map[text] ?: map.keys
            .minByOrNull { distance(it, text) }?.takeIf { distance(it, text) * 3 <= it.length }?.let { map[it] }
        val fixed = saved?.let { map ->
            object : Translator {
                override fun translate(texts: List<String>, pageJpeg: ByteArray?, story: StoryContext?) =
                    texts.map { (lookup(map, it) ?: it).removePrefix("SFX:") }

                override fun translatePages(pages: List<PageTexts>, story: StoryContext?) = pages.map { page ->
                    page.texts.map { t ->
                        val tr = lookup(map, t) ?: t
                        Translation(tr.removePrefix("SFX:"), kind = if (tr.startsWith("SFX:")) TextKind.SFX else TextKind.DIALOGUE)
                    }
                }
            }
        }
        val story = StoryContext()
        try {
            for (name in names) {
                val img = BitmapFactory.decodeFile(File(pagesDir, name).path)
                val pixels = IntArray(img.width * img.height)
                img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)
                val image = PixelImage(img.width, img.height, pixels)
                var t = System.nanoTime()
                val layout = detector.detect(image)
                val detectMs = (System.nanoTime() - t) / 1_000_000
                t = System.nanoTime()
                val translator = fixed ?: llm?.let { QwenTranslator(it, "en", source) } ?: filler
                val result = PageProcessor(ocr.takeIf { paddle == null }, translator, source, lama, paddle?.asLineReader())
                    .process(image, emptyList(), layout, story = story, lines = paddleLines?.detect(image).orEmpty())
                val restMs = (System.nanoTime() - t) / 1_000_000
                val bitmap = Bitmap.createBitmap(result.cleaned.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
                    .copy(Bitmap.Config.ARGB_8888, true)
                Typesetter(context, uppercase = System.getProperty("realMangaUppercase") == "1", language = "en",
                    font = System.getProperty("realMangaFont").orEmpty().ifEmpty { Settings.FONT_COMIC }).draw(bitmap, result.blocks)
                File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                println("== $name: detector $detectMs ms, resto $restMs ms, ${layout.blocks.size} bloques, ${result.blocks.size} textos")
                result.blocks.forEach { println("  ${it.box} | ${it.text} -> ${it.translation}") }
            }
        } finally {
            ocr.close(); detector.close(); lama.close(); llm?.close(); paddle?.close(); paddleLines?.close()
        }
    }
}
