package com.offlinebot.ai.whisper

import com.offlinebot.ai.ModelPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperCppEngine @Inject constructor(
    private val modelPaths: ModelPaths
) : WhisperEngine {
    private var nativeContext: Long = 0
    private var loadedModelPath: String? = null

    private val nativeAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary("offlinebot-whisper")
        }.isSuccess
    }

    override suspend fun transcribe(audioFile: File): TranscriptResult {
        val model = modelPaths.whisperBaseEn
        if (!model.exists()) throw WhisperModelMissingException(model.absolutePath)
        if (!nativeAvailable) {
            throw IllegalStateException("whisper.cpp JNI library is not built. Build with NDK/CMake to enable offline transcription.")
        }
        if (!audioFile.exists() || audioFile.length() == 0L) {
            return TranscriptResult(text = "", durationMs = 0)
        }

        val startedAt = System.currentTimeMillis()
        ensureModelLoaded(model.absolutePath)

        val text = nativeTranscribe(nativeContext, audioFile.absolutePath)
        return TranscriptResult(
            text = text.trim(),
            durationMs = System.currentTimeMillis() - startedAt
        )
    }

    override fun unload() {
        if (nativeContext != 0L && nativeAvailable) {
            nativeFree(nativeContext)
            nativeContext = 0
            loadedModelPath = null
        }
    }

    private fun ensureModelLoaded(modelPath: String) {
        if (nativeContext != 0L && loadedModelPath == modelPath) return
        unload()
        nativeContext = nativeInit(modelPath)
        if (nativeContext == 0L) {
            throw IllegalStateException("Failed to initialize whisper model from $modelPath")
        }
        loadedModelPath = modelPath
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(contextPtr: Long, audioPath: String): String
    private external fun nativeFree(contextPtr: Long)
}
