package com.offlinebot.ui.timeline

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinebot.ai.whisper.WhisperEngine
import com.offlinebot.data.database.dao.MediaAttachmentDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.TranscriptDao
import com.offlinebot.data.database.entities.MediaAttachmentEntity
import com.offlinebot.data.database.entities.RecordingEntity
import com.offlinebot.data.database.entities.TranscriptEntity
import com.offlinebot.recorder.playback.AudioPlayer
import com.offlinebot.recorder.storage.RecordingFileResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TimelineState(
    val recordings: List<RecordingUi> = emptyList(),
    val expandedId: Long? = null,
    val playingId: Long? = null,
    val playbackPositionMs: Int = 0,
    val transcribingId: Long? = null,
    val errorMessage: String? = null,
    val useListView: Boolean = false,
    val editingId: Long? = null,
    val editingText: String = ""
)

data class RecordingUi(
    val id: Long,
    val inputType: String,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val textContent: String?,
    val processed: Boolean,
    val transcript: String?,
    val photos: List<String>,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val mediaAttachmentDao: MediaAttachmentDao,
    private val whisperEngine: WhisperEngine,
    private val audioPlayer: AudioPlayer,
    private val recordingFileResolver: RecordingFileResolver,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                recordingDao.allRecordings(),
                audioPlayer.isPlaying,
                audioPlayer.currentPosition
            ) { recordings, _, _ -> recordings }
                .collect { recordings ->
                    val uis = recordings.map { rec ->
                        val photos = if (rec.inputType == "photo") {
                            mediaAttachmentDao.getByRecordingId(rec.id).map { it.filePath }
                        } else emptyList()
                        val transcript = transcriptDao.getByRecordingId(rec.id).firstOrNull()?.transcript
                        RecordingUi(id = rec.id, inputType = rec.inputType, filePath = rec.filePath,
                            durationMs = rec.durationMs, createdAt = rec.createdAt, textContent = rec.textContent,
                            processed = rec.processed, transcript = transcript, photos = photos,
                            latitude = rec.latitude, longitude = rec.longitude)
                    }
                    _state.update { it.copy(recordings = uis) }
                }
        }

        viewModelScope.launch {
            audioPlayer.isPlaying.collect { isPlaying ->
                if (!isPlaying) _state.update { it.copy(playingId = null, playbackPositionMs = 0) }
            }
        }

        viewModelScope.launch {
            audioPlayer.currentPosition.collect { pos ->
                _state.update { it.copy(playbackPositionMs = pos) }
            }
        }
    }

    fun toggleView() { _state.update { it.copy(useListView = !it.useListView) } }

    fun toggleExpand(recordingId: Long) {
        _state.update { if (it.expandedId == recordingId) it.copy(expandedId = null) else it.copy(expandedId = recordingId) }
    }

    fun togglePlayback(recordingId: Long, filePath: String) {
        if (_state.value.playingId == recordingId) { audioPlayer.stop(); _state.update { it.copy(playingId = null) } }
        else {
            val audioFile = recordingFileResolver.resolve(filePath)
            val started = audioFile != null && audioPlayer.play(audioFile.absolutePath)
            _state.update { it.copy(playingId = if (started) recordingId else null,
                errorMessage = if (started) null else "Audio file missing: $filePath") }
        }
    }

    fun transcribeRecording(recordingId: Long, filePath: String) {
        viewModelScope.launch {
            _state.update { it.copy(transcribingId = recordingId, errorMessage = null) }
            try {
                val audioFile = recordingFileResolver.resolve(filePath)
                    ?: throw IllegalStateException("Audio file not found at $filePath")
                val result = whisperEngine.transcribe(audioFile)
                if (result.text.isBlank()) throw IllegalStateException("Transcription returned empty")
                val transcriptId = transcriptDao.insert(TranscriptEntity(recordingId = recordingId, transcript = result.text, createdAt = System.currentTimeMillis()))
                recordingDao.markTranscribed(recordingId, transcriptId)
                _state.update { it.copy(transcribingId = null) }
            } catch (e: Exception) {
                _state.update { it.copy(transcribingId = null, errorMessage = e.message ?: "Transcription failed") }
            }
        }
    }

    // --- CRUD ---
    fun startEdit(recording: RecordingUi) {
        _state.update { it.copy(editingId = recording.id, editingText = recording.textContent ?: recording.transcript ?: "") }
    }

    fun updateEditText(text: String) { _state.update { it.copy(editingText = text) } }

    fun saveEdit() {
        val id = _state.value.editingId ?: return
        val text = _state.value.editingText.trim()
        viewModelScope.launch {
            recordingDao.updateTextContent(id, text)
            _state.update { it.copy(editingId = null, editingText = "") }
        }
    }

    fun cancelEdit() { _state.update { it.copy(editingId = null, editingText = "") } }

    fun deleteRecording(recordingId: Long) {
        viewModelScope.launch {
            if (_state.value.playingId == recordingId) audioPlayer.stop()
            mediaAttachmentDao.deleteByRecordingId(recordingId)
            recordingDao.deleteById(recordingId)
        }
    }

    // --- Export ---
    fun exportDatabase() {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) { buildExportJson() }
                val filename = "offlinebot-backup-${System.currentTimeMillis()}.json"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        context.contentResolver.openOutputStream(it)?.use { os -> os.write(json.toByteArray()) }
                    }
                    _state.update { it.copy(errorMessage = null) }
                    // Open share sheet
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export Database").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(dir, filename)
                    file.writeText(json)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export Database").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _state.update { it.copy(errorMessage = "Export failed: ${e.message}") }
            }
        }
    }

    private suspend fun buildExportJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"exported_at\": ${System.currentTimeMillis()},")
        sb.appendLine("  \"recordings\": [")
        val recordings = recordingDao.allRecordingsSnapshot()
        recordings.forEachIndexed { i, rec ->
            val transcripts = transcriptDao.getByRecordingId(rec.id)
            sb.appendLine("    {")
            sb.appendLine("      \"id\": ${rec.id},")
            sb.appendLine("      \"input_type\": \"${rec.inputType}\",")
            sb.appendLine("      \"text_content\": ${rec.textContent?.let { "\"$it\"" } ?: "null"},")
            sb.appendLine("      \"duration_ms\": ${rec.durationMs},")
            sb.appendLine("      \"created_at\": ${rec.createdAt},")
            sb.appendLine("      \"transcripts\": [${transcripts.joinToString { "\"${it.transcript.replace("\"", "\\\"")}\"" }}]")
            sb.append(if (i < recordings.size - 1) "    }," else "    }")
            sb.appendLine()
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    override fun onCleared() { audioPlayer.stop(); super.onCleared() }

    companion object { private const val TAG = "TimelineVM" }
}
