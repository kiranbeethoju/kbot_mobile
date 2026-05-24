package com.offlinebot.recorder.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class LocalAudioRecorder @Inject constructor(
    private val audioStorage: AudioStorage
) {
    private var wavRecorder: WavAudioRecorder? = null
    private var startedAt: Long = 0L
    private var pausedAt: Long = 0L
    private var totalPausedMs: Long = 0L

    @Volatile var isRecording: Boolean = false
        private set

    @Volatile var isPaused: Boolean = false
        private set

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun start(): PendingRecording {
        if (isRecording) {
            return PendingRecording(
                requireNotNull(audioStorage.activeFile).absolutePath,
                startedAt
            )
        }

        val file = audioStorage.prepareNextRecording()
        val recorder = WavAudioRecorder(file)
        try {
            recorder.start()
        } catch (e: Exception) {
            audioStorage.finishRecording()
            _errors.tryEmit("Recording failed: ${e.message}")
            throw e
        }
        wavRecorder = recorder
        isRecording = true
        isPaused = false
        startedAt = System.currentTimeMillis()
        totalPausedMs = 0L

        return PendingRecording(file.absolutePath, startedAt)
    }

    fun pause() {
        if (!isRecording || isPaused) return
        wavRecorder?.pause()
        isPaused = true
        pausedAt = System.currentTimeMillis()
    }

    fun resume() {
        if (!isRecording || !isPaused) return
        wavRecorder?.resume()
        totalPausedMs += System.currentTimeMillis() - pausedAt
        isPaused = false
    }

    fun stop(): FinishedRecording? {
        if (!isRecording) return null

        val recorder = wavRecorder ?: return null
        val wasPaused = isPaused
        recorder.stop()
        wavRecorder = null
        isRecording = false
        isPaused = false

        val file = audioStorage.finishRecording()
        val activePausedMs = if (wasPaused) System.currentTimeMillis() - pausedAt else 0L

        return file?.let {
            FinishedRecording(
                filePath = it.absolutePath,
                durationMs = System.currentTimeMillis() - startedAt - totalPausedMs - activePausedMs,
                createdAt = startedAt
            )
        }
    }
}

class RecorderStartException(cause: Throwable) :
    IllegalStateException("Could not start local audio recording.", cause)

data class PendingRecording(
    val filePath: String,
    val startedAt: Long
)

data class FinishedRecording(
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long
)
