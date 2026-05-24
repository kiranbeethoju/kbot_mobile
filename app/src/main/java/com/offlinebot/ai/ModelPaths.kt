package com.offlinebot.ai

import android.content.Context
import java.io.File

class ModelPaths(private val context: Context) {
    private val root: File
        get() = File(context.getExternalFilesDir(null), "models")

    val whisperBaseEn: File
        get() = File(root, "whisper/ggml-base.en.bin")

    val miniLm: File
        get() = File(root, "embeddings/minilm/model.onnx")

    val miniLmVocab: File
        get() = File(root, "embeddings/minilm/vocab.txt")

    val miniLmTokenizer: File
        get() = File(root, "embeddings/minilm/tokenizer.json")

    val phi3Q4: File
        get() = File(root, "llm/phi3-q4.gguf")

    val gemma3nQ4: File
        get() = File(root, "llm/gemma-3-1b-it-Q2_K.gguf")
}
