package com.offlinebot.ai.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.offlinebot.ai.ModelPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class LlmLoadState {
    Unloaded, Loading, Ready, IdleCached, Unloading, Failed
}

data class ModelMetrics(
    val deviceRamMb: Long = 0,
    val availableRamMb: Long = 0,
    val modelRamEstimateMb: Int = 700,
    val contextTokens: Int = 0,
    val tokensPerSec: Float = 0f,
    val loadTimeMs: Long = 0
)

@Singleton
class LlamaCppEngine @Inject constructor(
    private val modelPaths: ModelPaths,
    @ApplicationContext private val context: Context
) : LocalLlmEngine {
    private var modelLoaded = false
    private val loadMutex = Mutex()
    private var lastInferenceTimestamp: Long = 0L
    private var unloadJob: Job? = null
    private var loadStartTime: Long = 0L
    private var totalTokensGenerated: Long = 0L
    private var inferenceStartTime: Long = 0L

    private val _loadState = MutableStateFlow(
        if (modelPaths.gemma3nQ4.exists()) LlmLoadState.Unloaded else LlmLoadState.Unloaded
    )
    val loadState: StateFlow<LlmLoadState> = _loadState.asStateFlow()

    private val _metrics = MutableStateFlow(ModelMetrics())
    val metrics: StateFlow<ModelMetrics> = _metrics.asStateFlow()

    private val nativeAvailable: Boolean by lazy {
        runCatching { System.loadLibrary("offlinebot-llama") }.isSuccess
    }

    private val activityManager: ActivityManager
        get() = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private fun updateRamMetrics() {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        _metrics.value = _metrics.value.copy(
            deviceRamMb = memInfo.totalMem / (1024 * 1024),
            availableRamMb = memInfo.availMem / (1024 * 1024)
        )
    }

    override suspend fun summarize(prompt: String): String {
        return chat("You are a helpful assistant.", prompt)
    }

    override suspend fun tag(text: String): List<String> {
        val result = chat("Extract 3-5 keywords. Output only comma-separated.", text)
        return result.split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() }
    }

    suspend fun chat(systemPrompt: String, userMessage: String): String {
        if (!nativeAvailable) return "[LLM library not built]"
        if (!modelPaths.gemma3nQ4.exists()) return "[Gemma model not installed. Expected at: ${modelPaths.gemma3nQ4.absolutePath}]"

        ensureLoaded()
        lastInferenceTimestamp = System.currentTimeMillis()
        inferenceStartTime = System.currentTimeMillis()
        updateRamMetrics()

        return try {
            val raw = nativeChat(systemPrompt, userMessage, 512, 0.7f, 0.9f)
            val elapsed = (System.currentTimeMillis() - inferenceStartTime).toFloat() / 1000f
            totalTokensGenerated += raw.split(" ").size
            _metrics.value = _metrics.value.copy(
                tokensPerSec = if (elapsed > 0) raw.split(" ").size / elapsed else 0f
            )
            updateRamMetrics()
            startUnloadTimer()
            cleanOutput(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            "[Generation error: ${e.message}]"
        }
    }

    override fun unload() {
        if (!modelLoaded) return
        try {
            _loadState.value = LlmLoadState.Unloading
            nativeFree()
            modelLoaded = false
            _loadState.value = LlmLoadState.Unloaded
            _metrics.value = ModelMetrics()
            unloadJob?.cancel()
            Log.i(TAG, "Model unloaded")
        } catch (e: Exception) {
            Log.e(TAG, "Unload failed", e)
        }
    }

    fun isReady(): Boolean = nativeAvailable && modelPaths.gemma3nQ4.exists()
    fun isModelLoaded(): Boolean = modelLoaded

    /** Load model on demand (only when chat opens or inference requested) */
    suspend fun load() = loadMutex.withLock {
        if (!isReady()) {
            _loadState.value = LlmLoadState.Unloaded
            return@withLock
        }
        if (modelLoaded) {
            _loadState.value = LlmLoadState.Ready
            updateRamMetrics()
            startUnloadTimer()
            return@withLock
        }
        _loadState.value = LlmLoadState.Loading
        loadStartTime = System.currentTimeMillis()
        withContext(Dispatchers.Default) {
            runCatching {
                val path = modelPaths.gemma3nQ4.absolutePath
                Log.i(TAG, "Loading model from $path...")
                val ok = nativeLoadModel(path, 2048)
                if (!ok) throw IllegalStateException("nativeLoadModel returned false")
                if (!nativeWarmup()) throw IllegalStateException("Warmup decode failed")
            }
                .onSuccess {
                    modelLoaded = true
                    val loadTime = System.currentTimeMillis() - loadStartTime
                    _loadState.value = LlmLoadState.Ready
                    _metrics.value = _metrics.value.copy(loadTimeMs = loadTime, contextTokens = 512)
                    updateRamMetrics()
                    startUnloadTimer()
                    Log.i(TAG, "Model loaded in ${loadTime}ms")
                }
                .onFailure { e ->
                    modelLoaded = false
                    try { nativeFree() } catch (_: Exception) {}
                    _loadState.value = LlmLoadState.Failed
                    Log.e(TAG, "Model load failed", e)
                }
        }
    }

    private suspend fun ensureLoaded() = loadMutex.withLock {
        if (!isReady()) throw IllegalStateException("Gemma model not installed")
        if (modelLoaded) {
            updateRamMetrics()
            return@withLock
        }
        _loadState.value = LlmLoadState.Loading
        loadStartTime = System.currentTimeMillis()
        withContext(Dispatchers.Default) {
            val path = modelPaths.gemma3nQ4.absolutePath
            val ok = nativeLoadModel(path, 2048)
            if (!ok || !nativeWarmup()) {
                nativeFree()
                modelLoaded = false
                _loadState.value = LlmLoadState.Failed
                throw IllegalStateException("Model warmup decode failed")
            }
        }
        modelLoaded = true
        val loadTime = System.currentTimeMillis() - loadStartTime
        _loadState.value = LlmLoadState.Ready
        _metrics.value = _metrics.value.copy(loadTimeMs = loadTime, contextTokens = 512)
        updateRamMetrics()
        startUnloadTimer()
    }

    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Auto-unload after 5 minutes of inactivity */
    private fun startUnloadTimer() {
        unloadJob?.cancel()
        unloadJob = timerScope.launch {
            delay(5 * 60 * 1000L)
            if (modelLoaded && System.currentTimeMillis() - lastInferenceTimestamp >= 5 * 60 * 1000L) {
                Log.i(TAG, "Auto-unloading after 5min inactivity")
                loadMutex.withLock {
                    if (modelLoaded && nativeAvailable) {
                        _loadState.value = LlmLoadState.Unloading
                        nativeFree()
                        modelLoaded = false
                        _loadState.value = LlmLoadState.Unloaded
                    }
                }
            }
        }
    }

    private fun cleanOutput(raw: String): String {
        return raw.replace("<turn|>", "").replace("<|turn>", "").trim()
    }

    private external fun nativeLoadModel(modelPath: String, nCtx: Int): Boolean
    private external fun nativeChat(systemPrompt: String, userMessage: String, maxTokens: Int, temperature: Float, topP: Float): String
    private external fun nativeWarmup(): Boolean
    private external fun nativeFree()

    companion object {
        private const val TAG = "LlamaCppEngine"
    }
}
