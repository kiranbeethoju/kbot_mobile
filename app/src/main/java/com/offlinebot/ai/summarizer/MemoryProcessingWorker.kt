package com.offlinebot.ai.summarizer

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.offlinebot.ai.ModelPaths
import com.offlinebot.ai.embeddings.EmbeddingEngine
import com.offlinebot.ai.whisper.WhisperEngine
import com.offlinebot.ai.whisper.WhisperModelMissingException
import com.offlinebot.data.database.dao.EmbeddingDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.TranscriptDao
import com.offlinebot.data.database.entities.EmbeddingEntity
import com.offlinebot.data.database.entities.TranscriptEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class MemoryProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val embeddingDao: EmbeddingDao,
    private val whisperEngine: WhisperEngine,
    private val embeddingEngine: EmbeddingEngine,
    private val modelPaths: ModelPaths
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Check required models exist before processing
        val missing = buildList {
            if (!modelPaths.whisperBaseEn.exists())
                add("Whisper base.en (ggml-base.en.bin)")
            if (!modelPaths.miniLm.exists())
                add("MiniLM ONNX (model.onnx)")
            if (!modelPaths.miniLmVocab.exists())
                add("MiniLM vocab (vocab.txt)")
            if (!modelPaths.miniLmTokenizer.exists())
                add("MiniLM tokenizer (tokenizer.json)")
        }
        if (missing.isNotEmpty()) {
            Log.e(TAG, "Cannot process: missing models: ${missing.joinToString()}")
            return Result.failure()
        }

        val pending = recordingDao.pending()
        if (pending.isEmpty()) return Result.success()

        return try {
            pending.forEach { recording ->
                val audioFile = File(recording.filePath)
                if (!audioFile.exists() || audioFile.length() == 0L) {
                    Log.w(TAG, "Skipping recording ${recording.id}: audio file missing or empty")
                    recordingDao.markProcessed(recording.id, -1, -1)
                    return@forEach
                }

                val transcript = whisperEngine.transcribe(audioFile)
                whisperEngine.unload()

                val transcriptId = transcriptDao.insert(
                    TranscriptEntity(
                        recordingId = recording.id,
                        transcript = transcript.text,
                        createdAt = System.currentTimeMillis()
                    )
                )

                if (transcript.text.isNotBlank()) {
                    val vector = embeddingEngine.embed(transcript.text)
                    embeddingEngine.unload()
                    val embeddingId = embeddingDao.insert(
                        EmbeddingEntity(recordingId = recording.id, vector = vector)
                    )
                    recordingDao.markProcessed(recording.id, transcriptId, embeddingId)
                } else {
                    recordingDao.markProcessed(recording.id, transcriptId, -1)
                }
            }
            Result.success()
        } catch (e: WhisperModelMissingException) {
            Log.e(TAG, "Whisper model missing: ${e.message}")
            Result.failure()
        } catch (error: Throwable) {
            Log.e(TAG, "Processing failed", error)
            whisperEngine.unload()
            embeddingEngine.unload()
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MemoryProcessing"
    }
}
