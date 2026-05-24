package com.offlinebot.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActivityEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val message: String
)

@Singleton
class ActivityLogger @Inject constructor() {
    private val _events = MutableSharedFlow<ActivityEvent>(replay = 200, extraBufferCapacity = 50)
    val events: SharedFlow<ActivityEvent> = _events.asSharedFlow()

    fun log(category: String, message: String) {
        _events.tryEmit(ActivityEvent(category = category, message = message))
    }

    fun voiceStarted() = log("Voice", "Recording started")
    fun voiceStopped(dur: Long) = log("Voice", "Recording stopped (${dur / 1000}s)")
    fun speechDetected() = log("VAD", "Speech detected (Silero VAD)")
    fun chunkTranscribed(dur: Float) = log("Whisper", "Chunk transcribed (${dur}s)")
    fun embeddingQueued(count: Int = 1) = log("Embedding", "Embedding queued ($count pending)")
    fun embeddingCompleted() = log("Embedding", "Embedding completed")
    fun modelLoading(name: String) = log("Model", "$name loading into RAM...")
    fun modelReady(name: String, loadTimeMs: Long) = log("Model", "$name ready (${loadTimeMs}ms)")
    fun modelUnloaded(name: String) = log("Model", "$name unloaded from RAM")
    fun inferenceCompleted(toksPerSec: Float) = log("LLM", "Inference completed (%.0f tok/s)".format(toksPerSec))
    fun textNoteSaved() = log("Capture", "Text note saved")
    fun photoSaved(count: Int) = log("Capture", "$count photo(s) saved")
    fun cloudRequestStarted() = log("Cloud", "Cloud request started")
    fun cloudTokenReceived() = log("Cloud", "Streaming token received")
    fun searchCompleted(timeMs: Long, results: Int) = log("Search", "Search completed (${timeMs}ms, $results results)")
    fun thermalWarning(temp: Float) = log("System", "Thermal warning — device temp high (${temp}°C)")
    fun performanceModeChanged(mode: String) = log("System", "Performance mode: $mode")
}
