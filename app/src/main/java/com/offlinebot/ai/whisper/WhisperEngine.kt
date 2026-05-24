package com.offlinebot.ai.whisper

import java.io.File

interface WhisperEngine {
    suspend fun transcribe(audioFile: File): TranscriptResult
    fun unload()
}

data class TranscriptResult(
    val text: String,
    val language: String = "en",
    val durationMs: Long
)

class WhisperModelMissingException(modelPath: String) :
    IllegalStateException("Whisper model is missing: $modelPath")
