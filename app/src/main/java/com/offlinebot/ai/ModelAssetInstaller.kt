package com.offlinebot.ai

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ModelAssetInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelPaths: ModelPaths
) {
    suspend fun installIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        var installed = false

        // Single-file models
        val singleFiles = listOf(
            Triple("models/whisper/ggml-base.en.bin", modelPaths.whisperBaseEn, "Whisper"),
            Triple("models/embeddings/minilm/model.onnx", modelPaths.miniLm, "MiniLM"),
            Triple("models/embeddings/minilm/vocab.txt", modelPaths.miniLmVocab, "MiniLM vocab"),
            Triple("models/embeddings/minilm/tokenizer.json", modelPaths.miniLmTokenizer, "MiniLM tokenizer")
        )

        singleFiles.forEach { (assetPath, destFile, _) ->
            if (installFile(assetPath, destFile)) installed = true
        }

        // Install Gemma 3 1B model (single file, 658MB)
        if (!modelPaths.gemma3nQ4.exists() || modelPaths.gemma3nQ4.length() == 0L) {
            val installedGemma = installFile("models/llm/gemma-3-1b-it-Q2_K.gguf", modelPaths.gemma3nQ4)
            if (installedGemma) {
                installed = true
                Log.i(TAG, "Installed Gemma 3 1B model (${modelPaths.gemma3nQ4.length()} bytes)")
            }
        }

        installed
    }

    private fun installFile(assetPath: String, destFile: File): Boolean {
        if (destFile.exists() && destFile.length() > 0L) return false
        destFile.parentFile?.mkdirs()
        return try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "ModelAssetInstaller"
    }
}
