package com.mangatraductor.app

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.mangatraductor.core.LocalLlm
import com.mangatraductor.core.OnnxPatcher
import java.io.IOException

/**
 * Qwen 3.5 (de Alibaba, licencia Apache 2.0) para traducir dentro del móvil,
 * sin internet. Exportación ONNX de onnx-community con pesos de 4 bits; se
 * descarga de Hugging Face (revisión fija) la primera vez.
 */
class QwenModel(context: Context, val size: Size) : DownloadableModel(context) {

    enum class Size(val id: String, val repo: String, val revision: String, val files: List<ModelFile>, val minRamGb: Int) {
        /** 2 000 millones de parámetros: más rápido, 1,6 GB. */
        SMALL(
            "2b", "onnx-community/Qwen3.5-2B-ONNX", "b1fc7ca3afafcb8e4b13d29715a6b9ea5af1d1cb",
            listOf(
                tokenizer(),
                ModelFile("embed_tokens_q4.onnx", 857,
                    "0255dd844858758f452d9678f1e2c91db178f21b7da254d29b12e7fe3d23e305", "onnx/embed_tokens_q4.onnx"),
                ModelFile("embed_tokens_q4.onnx_data", 325_795_840,
                    "9a6404d9b1c79ffc038d5c28deec04d419efb4594125453c991be38b4522ecb6", "onnx/embed_tokens_q4.onnx_data"),
                ModelFile(DECODER, 885_982,
                    "33f9c1311878df2140e7286f76d9e3acb29d77d91f724210674b0b63d8330df5", "onnx/$DECODER"),
                ModelFile("${DECODER}_data", 1_209_126_912,
                    "c6f4807e1287961a354b0bc6f8d4c68c658f707a9541e771a297dfefaf4a1e14", "onnx/${DECODER}_data"),
            ),
            minRamGb = 6,
        ),

        /** 4 000 millones: traduce mejor, pero tarda el doble y ocupa 3,1 GB. */
        LARGE(
            "4b", "onnx-community/Qwen3.5-4B-ONNX", "74d8caba2117fd5f41d655e9cc27eda1338662b3",
            listOf(
                tokenizer(),
                ModelFile("embed_tokens_q4.onnx", 857,
                    "b72feb6947db92d643f70abc6a4d3e5398a9535470a97685e3b1ab3b05047146", "onnx/embed_tokens_q4.onnx"),
                ModelFile("embed_tokens_q4.onnx_data", 407_244_800,
                    "aeb939a0c9a5d54c9f3ce77db340132780692c02e602b80a0cefb6ec8a9728be", "onnx/embed_tokens_q4.onnx_data"),
                ModelFile(DECODER, 1_206_737,
                    "731f0569c84ea71a756abfe090f3b472050ecda7ad35a78fb553dc530a60315b", "onnx/$DECODER"),
                ModelFile("${DECODER}_data", 2_093_368_320,
                    "c884a7c467f508568008bbaed3a90a77103f2dbc0643bd2625f8c483864c9726", "onnx/${DECODER}_data"),
                ModelFile("${DECODER}_data_1", 607_298_560,
                    "8239304d972b0ee1661b8be4b90ccf5937073a281f62fc1917d793b1fb873f3c", "onnx/${DECODER}_data_1"),
            ),
            minRamGb = 8,
        );

        companion object {
            fun from(id: String) = entries.firstOrNull { it.id == id } ?: LARGE
        }
    }

    override val key = "qwen3.5-${size.id}"
    override val files = size.files
    override val sources: List<(ModelFile) -> String> =
        listOf { f -> "https://huggingface.co/${size.repo}/resolve/${size.revision}/${f.remotePath}" }
    override val title: String get() = context.getString(R.string.qwen_download_title)

    /** Carga el modelo (tarda unos segundos y ocupa 1,5–3 GB de memoria). */
    fun load(): LocalLlm {
        if (!isDownloaded) throw IOException("Falta descargar Qwen")
        // Núcleos rápidos: en un Pixel 10, el principal y los 5 de rendimiento.
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        return LocalLlm.load(
            tokenizer = installed(files[0])!!,
            decoder = OnnxPatcher.withInt8MatMul(installed(files.first { it.name == DECODER })!!),
            embed = installed(files.first { it.name == "embed_tokens_q4.onnx" })!!,
            threads = threads,
        )
    }

    companion object {
        private const val DECODER = "decoder_model_merged_q4.onnx"

        /** El tokenizador es el mismo en los dos tamaños. */
        private fun tokenizer() = ModelFile("tokenizer.json", 19_226_111,
            "89da80cc6689bef4d90cc1028249436975ffb0814618f1d93c65310e05801a9b")

        /** Qwen necesita un móvil de 64 bits (el modelo no cabe en la memoria de una app de 32). */
        val supported: Boolean get() = Process.is64Bit()

        /** Memoria RAM del móvil, en GB. */
        fun ramGb(context: Context): Int {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            return ((info.totalMem + 500_000_000L) / 1_000_000_000L).toInt()
        }
    }
}
