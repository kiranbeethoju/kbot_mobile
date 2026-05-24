package com.offlinebot.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinebot.ai.ModelPaths
import com.offlinebot.data.database.dao.MediaAttachmentDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.entities.MediaAttachmentEntity
import com.offlinebot.data.database.entities.RecordingEntity
import com.offlinebot.data.repository.MemoryRepository
import com.offlinebot.recorder.service.RecorderController
import com.offlinebot.recorder.storage.LocalAudioRecorder
import com.offlinebot.utils.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val query: String = "",
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recentCount: Int = 0,
    val weekCount: Int = 0,
    val pendingProcessingCount: Int = 0,
    val errorMessage: String? = null,
    val savedMessage: String? = null,
    val selectedInputTab: Int = 0,
    val textNoteContent: String = "",
    val capturedPhotoPaths: List<String> = emptyList(),
    val photoCaption: String = "",
    val saving: Boolean = false,
    val searchResults: List<String> = emptyList(),
    val searching: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recorderController: RecorderController,
    private val memoryRepository: MemoryRepository,
    private val recordingDao: RecordingDao,
    private val mediaAttachmentDao: MediaAttachmentDao,
    private val modelPaths: ModelPaths,
    private val localAudioRecorder: LocalAudioRecorder,
    private val locationHelper: LocationHelper
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            memoryRepository.observeHomeStats().collect { stats ->
                _state.update {
                    it.copy(
                        recentCount = stats.recentCount,
                        weekCount = stats.weekCount,
                        pendingProcessingCount = stats.pendingProcessingCount
                    )
                }
            }
        }

        viewModelScope.launch {
            localAudioRecorder.errors.collect { error ->
                _state.update {
                    it.copy(
                        isRecording = false,
                        isPaused = false,
                        errorMessage = error
                    )
                }
            }
        }

        // Seed example data on first launch
        viewModelScope.launch {
            val existing = recordingDao.allRecordingsSnapshot()
            if (existing.isEmpty()) {
                seedExampleData()
            }
        }
    }

    private suspend fun seedExampleData() {
        val now = System.currentTimeMillis()
        val day = 86400_000L
        val examples = listOf(
            Triple("Welcome to KBot! Your private memory OS — all data stays on-device. Try voice, text, or photo capture.", now - day * 1, Pair(37.7749, -122.4194)),
            Triple("Meeting with Sarah re: Q2 roadmap. Decided to prioritize AI trading bot integration by June. Follow up next week.", now - day * 2, Pair(40.7128, -74.0060)),
            Triple("Idea: Build an aeroponic farming system using automated nutrient delivery and IoT sensors. Research hydroponics vs aeroponics.", now - day * 3, Pair(37.7749, -122.4194)),
            Triple("Reminder: Call John about project deadline. Final designs needed by Friday. Also need to review the budget spreadsheet.", now - day * 5, Pair(37.7750, -122.4189)),
            Triple("Studied ML concepts: transformer architecture, attention mechanisms, fine-tuning strategies for small LLMs. Great progress!", now - day * 7, Pair(34.0522, -118.2437))
        )
        examples.forEach { (text, ts, loc) ->
            recordingDao.insert(RecordingEntity(filePath = "", durationMs = 0, createdAt = ts, inputType = "text",
                textContent = text, latitude = loc.first, longitude = loc.second))
        }
        Log.i(TAG, "Seeded ${examples.size} example memories")
    }

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query, searchResults = emptyList()) }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        _state.update { it.copy(searching = true) }
        viewModelScope.launch {
            try {
                val keywords = q.lowercase().split(" ").filter { it.length > 1 }
                val recordings = recordingDao.allRecordingsSnapshot()
                val results = recordings.mapNotNull { rec ->
                    val text = rec.textContent
                    if (text != null && keywords.any { text.lowercase().contains(it) }) text.take(100)
                    else null
                }
                _state.update { it.copy(searchResults = results, searching = false) }
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, errorMessage = "Search failed") }
            }
        }
    }

    fun setInputTab(tab: Int) {
        _state.update { it.copy(selectedInputTab = tab, errorMessage = null) }
    }

    fun dismissSavedMessage() {
        _state.update { it.copy(savedMessage = null) }
    }

    // --- Voice recording ---

    fun toggleRecording() {
        viewModelScope.launch {
            try {
                if (_state.value.isRecording) {
                    // STOP: save recording directly first, then tell service
                    val finished = localAudioRecorder.stop()
                    if (finished != null) {
                        val audioFile = java.io.File(finished.filePath)
                        if (audioFile.exists() && audioFile.length() > 44L) {
                            val (lat, lng) = captureLocation()
                            recordingDao.insert(
                                RecordingEntity(
                                    filePath = finished.filePath,
                                    durationMs = finished.durationMs,
                                    createdAt = finished.createdAt,
                                    inputType = "audio",
                                    latitude = lat,
                                    longitude = lng
                                )
                            )
                            Log.i(TAG, "Saved recording path=${finished.filePath} dur=${finished.durationMs}ms")
                            _state.update {
                                it.copy(savedMessage = "Recording saved (${formatMs(finished.durationMs)})")
                            }
                            triggerProcessing()
                        } else {
                            Log.e(TAG, "Recording file empty or missing: ${finished.filePath} (${audioFile.length()} bytes)")
                            _state.update { it.copy(errorMessage = "Recording was empty — tap mic and speak clearly") }
                        }
                    }
                    // Always tell the service to clean up its foreground state
                    try { recorderController.stop() } catch (_: Exception) {}
                    _state.update { it.copy(isRecording = false, isPaused = false) }
                } else {
                    // START
                    recorderController.start()
                    _state.update { it.copy(isRecording = true, isPaused = false, errorMessage = null) }
                }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isRecording = false,
                        isPaused = false,
                        errorMessage = error.message ?: "Could not start recording."
                    )
                }
            }
        }
    }

    fun pauseRecording() {
        viewModelScope.launch {
            try {
                recorderController.pause()
                _state.update { it.copy(isPaused = true) }
            } catch (error: Throwable) {
                _state.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    fun resumeRecording() {
        viewModelScope.launch {
            try {
                recorderController.resume()
                _state.update { it.copy(isPaused = false) }
            } catch (error: Throwable) {
                _state.update { it.copy(errorMessage = error.message) }
            }
        }
    }

    // --- Text notes ---

    fun updateTextNote(text: String) {
        _state.update { it.copy(textNoteContent = text) }
    }

    fun saveTextNote() {
        val text = _state.value.textNoteContent.trim()
        if (text.isBlank()) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val (lat, lng) = captureLocation()
                recordingDao.insert(
                    RecordingEntity(
                        filePath = "",
                        durationMs = 0,
                        createdAt = System.currentTimeMillis(),
                        inputType = "text",
                        textContent = text,
                        latitude = lat,
                        longitude = lng
                    )
                )
                _state.update { it.copy(textNoteContent = "", saving = false, errorMessage = null, savedMessage = "Note saved") }
                triggerProcessing()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errorMessage = "Failed to save note") }
            }
        }
    }

    // --- Photo capture ---

    fun addPhotoPaths(paths: List<String>) {
        _state.update { it.copy(capturedPhotoPaths = it.capturedPhotoPaths + paths) }
    }

    fun removePhotoPath(path: String) {
        _state.update { it.copy(capturedPhotoPaths = it.capturedPhotoPaths - path) }
    }

    fun updatePhotoCaption(caption: String) {
        _state.update { it.copy(photoCaption = caption) }
    }

    fun savePhotoNote() {
        val paths = _state.value.capturedPhotoPaths
        val caption = _state.value.photoCaption.trim()
        if (paths.isEmpty()) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val (lat, lng) = captureLocation()
                val recordingId = recordingDao.insert(
                    RecordingEntity(
                        filePath = paths.firstOrNull() ?: "",
                        durationMs = 0,
                        createdAt = System.currentTimeMillis(),
                        inputType = "photo",
                        textContent = caption.ifBlank { null },
                        latitude = lat,
                        longitude = lng
                    )
                )
                mediaAttachmentDao.insertAll(
                    paths.map { path ->
                        MediaAttachmentEntity(
                            recordingId = recordingId,
                            filePath = path,
                            mediaType = "image/jpeg"
                        )
                    }
                )
                _state.update {
                    it.copy(
                        capturedPhotoPaths = emptyList(),
                        photoCaption = "",
                        saving = false,
                        errorMessage = null,
                        savedMessage = "${paths.size} photo(s) saved"
                    )
                }
                triggerProcessing()
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errorMessage = "Failed to save photo note") }
            }
        }
    }

    fun queueOfflineProcessing() {
        triggerProcessing()
    }

    private fun triggerProcessing() {
        val missing = buildList {
            if (!modelPaths.whisperBaseEn.exists()) add("Whisper")
            if (!modelPaths.miniLm.exists()) add("MiniLM onnx")
            if (!modelPaths.miniLmVocab.exists()) add("MiniLM vocab")
        }
        if (missing.isNotEmpty()) return // silently skip, models not installed
        memoryRepository.enqueuePendingProcessing()
    }

    private suspend fun captureLocation(): Pair<Double?, Double?> {
        return try {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                Log.d(TAG, "Location captured: ${loc.latitude}, ${loc.longitude}")
                Pair(loc.latitude, loc.longitude)
            } else {
                Log.w(TAG, "No location available")
                Pair(null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location capture failed", e)
            // Try last known as absolute fallback
            val last = locationHelper.lastKnownLocation
            if (last != null) Pair(last.latitude, last.longitude) else Pair(null, null)
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private fun formatMs(ms: Long): String {
            val seconds = ms / 1000
            val min = seconds / 60
            val sec = seconds % 60
            return "%d:%02d".format(min, sec)
        }
    }
}
