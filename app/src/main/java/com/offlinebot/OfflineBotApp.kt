package com.offlinebot

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.offlinebot.ai.ModelAssetInstaller
import com.offlinebot.ai.automation.DailySummaryWorker
import com.offlinebot.ai.automation.ResurfaceWorker
import com.offlinebot.ai.automation.RuleSchedulerWorker
import com.offlinebot.ai.llm.LlamaCppEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OfflineBotApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var llamaEngine: LlamaCppEngine
    @Inject lateinit var modelAssetInstaller: ModelAssetInstaller

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            // Install bundled models on first launch (NO preload — model loads on demand)
            modelAssetInstaller.installIfNeeded()
            // Schedule automation workers on every launch (idempotent)
            try {
                val wm = WorkManager.getInstance(this@OfflineBotApp)
                DailySummaryWorker.schedule(wm)
                ResurfaceWorker.schedule(wm)
                RuleSchedulerWorker.schedule(wm)
            } catch (_: Exception) {}
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
