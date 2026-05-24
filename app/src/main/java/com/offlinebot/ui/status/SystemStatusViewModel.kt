package com.offlinebot.ui.status

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinebot.ai.ModelPaths
import com.offlinebot.ai.llm.LlamaCppEngine
import com.offlinebot.ai.llm.LlmLoadState
import com.offlinebot.ai.llm.ModelMetrics
import com.offlinebot.data.database.dao.EmbeddingDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.TranscriptDao
import com.offlinebot.utils.PerformanceManager
import com.offlinebot.utils.PerformanceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SystemStatusState(
    // Models
    val gemmaState: LlmLoadState = LlmLoadState.Unloaded,
    val gemmaMetrics: ModelMetrics = ModelMetrics(),
    val gemmaLoadedSince: Long = 0L,
    val whisperStatus: String = "IDLE",
    val whisperLastUsed: Long = 0L,
    val embeddingQueueSize: Int = 0,
    val searchLatencyMs: Long = 0L,

    // Device
    val availableRamMb: Long = 0,
    val totalRamMb: Long = 0,
    val ramPressurePercent: Int = 0,
    val batteryTemp: Float = 0f,
    val cpuUsage: Int = 0,
    val storageFreeMb: Long = 0,
    val storageTotalMb: Long = 0,
    val appStorageMb: Long = 0,

    // Stats
    val totalRecordings: Int = 0,
    val totalTranscripts: Int = 0,
    val totalEmbeddings: Int = 0,
    val totalLocalInferences: Int = 0,
    val totalCloudRequests: Int = 0,

    // Performance
    val performanceMode: PerformanceMode = PerformanceMode.Balanced
)

@HiltViewModel
class SystemStatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llamaEngine: LlamaCppEngine,
    private val modelPaths: ModelPaths,
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val embeddingDao: EmbeddingDao,
    private val performanceManager: PerformanceManager
) : ViewModel() {
    private val _state = MutableStateFlow(SystemStatusState())
    val state: StateFlow<SystemStatusState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            llamaEngine.loadState.collect { s -> _state.update { it.copy(gemmaState = s) } }
        }
        viewModelScope.launch {
            llamaEngine.metrics.collect { m -> _state.update { it.copy(gemmaMetrics = m) } }
        }
        viewModelScope.launch {
            recordingDao.countAll().collect { c -> _state.update { it.copy(totalRecordings = c) } }
        }
        viewModelScope.launch {
            performanceManager.config.collect { c -> _state.update { it.copy(performanceMode = c.mode) } }
        }

        // Periodic device health refresh
        viewModelScope.launch {
            while (true) {
                refreshDeviceHealth()
                delay(2000)
            }
        }

        // Load counts
        viewModelScope.launch {
            _state.update { it.copy(whisperStatus = if (modelPaths.whisperBaseEn.exists()) "READY" else "MISSING") }
            val embeddings = embeddingDao.all()
            _state.update { it.copy(totalEmbeddings = embeddings.size, embeddingQueueSize = recordingDao.countPendingSnapshot()) }
        }
    }

    private fun refreshDeviceHealth() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val totalStorage = statFs.blockCountLong * statFs.blockSizeLong
        val freeStorage = statFs.availableBlocksLong * statFs.blockSizeLong

        val appDir = context.getExternalFilesDir(null)
        val appSize = if (appDir != null) getDirSize(appDir) else 0L

        _state.update {
            it.copy(
                availableRamMb = memInfo.availMem / (1024 * 1024),
                totalRamMb = memInfo.totalMem / (1024 * 1024),
                ramPressurePercent = ((1f - memInfo.availMem.toFloat() / memInfo.totalMem) * 100).toInt(),
                storageFreeMb = freeStorage / (1024 * 1024),
                storageTotalMb = totalStorage / (1024 * 1024),
                appStorageMb = appSize / (1024 * 1024)
            )
        }
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        performanceManager.setMode(mode)
    }
}
