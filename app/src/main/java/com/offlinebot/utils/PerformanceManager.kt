package com.offlinebot.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class PerformanceMode(val label: String) {
    Eco("Eco Mode"),
    Balanced("Balanced"),
    Performance("Performance")
}

data class PerformanceConfig(
    val mode: PerformanceMode = PerformanceMode.Balanced,
    val modelCacheTimeoutMs: Long = 5 * 60 * 1000L,
    val animationEnabled: Boolean = true,
    val realtimeTranscription: Boolean = true,
    val backgroundBatchingOnly: Boolean = false,
    val inferencePriority: Boolean = false
)

@Singleton
class PerformanceManager @Inject constructor() {
    private val _config = MutableStateFlow(PerformanceConfig())
    val config: StateFlow<PerformanceConfig> = _config.asStateFlow()

    fun setMode(mode: PerformanceMode) {
        _config.value = when (mode) {
            PerformanceMode.Eco -> PerformanceConfig(
                mode = mode,
                modelCacheTimeoutMs = 2 * 60 * 1000L,
                animationEnabled = false,
                realtimeTranscription = false,
                backgroundBatchingOnly = true,
                inferencePriority = false
            )
            PerformanceMode.Balanced -> PerformanceConfig(
                mode = mode,
                modelCacheTimeoutMs = 5 * 60 * 1000L,
                animationEnabled = true,
                realtimeTranscription = true,
                backgroundBatchingOnly = false,
                inferencePriority = false
            )
            PerformanceMode.Performance -> PerformanceConfig(
                mode = mode,
                modelCacheTimeoutMs = 15 * 60 * 1000L,
                animationEnabled = true,
                realtimeTranscription = true,
                backgroundBatchingOnly = false,
                inferencePriority = true
            )
        }
    }

    fun getAutoUnloadTimeout(): Long = _config.value.modelCacheTimeoutMs
    fun shouldAnimate(): Boolean = _config.value.animationEnabled
    fun shouldBatchBackground(): Boolean = _config.value.backgroundBatchingOnly
}
