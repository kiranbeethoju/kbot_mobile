package com.offlinebot.ai

import java.io.File
import javax.inject.Inject

class ModelCatalog @Inject constructor(
    private val modelPaths: ModelPaths
) {
    fun statuses(): List<ModelStatus> = bundledStatus()

    fun bundledStatus(): List<ModelStatus> = listOf(
        ModelStatus(
            id = "whisper-base-en",
            label = "Whisper base.en (141 MB)",
            description = "Speech-to-text. Bundled with the app.",
            file = modelPaths.whisperBaseEn,
            required = true
        ),
        ModelStatus(
            id = "minilm-onnx",
            label = "MiniLM embeddings (86 MB)",
            description = "Semantic search model. Bundled with the app.",
            file = modelPaths.miniLm,
            required = true
        ),
        ModelStatus(
            id = "minilm-vocab",
            label = "MiniLM vocab (231 KB)",
            description = "Bundled with the app.",
            file = modelPaths.miniLmVocab,
            required = true
        ),
        ModelStatus(
            id = "minilm-tokenizer",
            label = "MiniLM tokenizer (466 KB)",
            description = "Bundled with the app.",
            file = modelPaths.miniLmTokenizer,
            required = true
        ),
        ModelStatus(
            id = "gemma-3-1b",
            label = "Gemma 3 1B IT Q2_K (658 MB)",
            description = "On-device LLM optimized for mobile. Low RAM, fast inference.",
            file = modelPaths.gemma3nQ4,
            required = false
        )
    )
}

data class ModelStatus(
    val id: String,
    val label: String,
    val description: String = "",
    val file: File,
    val required: Boolean,
    val downloadUrl: String? = null,
    val sha256: String? = null
) {
    val installed: Boolean
        get() = file.exists() && file.length() > 0L

    val sizeBytes: Long
        get() = if (installed) file.length() else 0L

    val sizeLabel: String
        get() {
            if (!installed) return "Not installed"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return "%.1f MB".format(mb)
        }
}
