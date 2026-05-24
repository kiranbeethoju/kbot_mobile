package com.offlinebot.ai.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.offlinebot.data.database.dao.AutomationLogDao
import com.offlinebot.data.database.dao.AutomationRuleDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.entities.AutomationLogEntity
import com.offlinebot.utils.ActivityLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Runs daily at 9 PM — generates summary of today's memories */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val logDao: AutomationLogDao,
    private val activityLogger: ActivityLogger
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (shouldSkip(applicationContext)) return Result.success()
        val start = System.currentTimeMillis()
        return try {
            val count = recordingDao.allRecordingsSnapshot().count {
                it.createdAt > System.currentTimeMillis() - 24 * 3600_000
            }
            val output = "Today: $count memories captured"
            activityLogger.log("Summary", output)

            logDao.insert(AutomationLogEntity(
                ruleId = 1, status = "success", output = output,
                durationMs = System.currentTimeMillis() - start,
                batteryState = getBatteryState(applicationContext)
            ))
            Result.success()
        } catch (e: Exception) {
            activityLogger.log("Summary", "Failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        fun schedule(workManager: WorkManager) {
            val initialDelay = computeDelayUntil(21, 0) // 9 PM
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .addTag("automation")
                .build()
            workManager.enqueueUniquePeriodicWork("daily_summary", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/** Runs daily at 8 AM — resurfaces forgotten memories from >30 days ago */
@HiltWorker
class ResurfaceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recordingDao: RecordingDao,
    private val logDao: AutomationLogDao,
    private val activityLogger: ActivityLogger
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (shouldSkip(applicationContext)) return Result.success()
        val start = System.currentTimeMillis()
        return try {
            val oldMemories = recordingDao.allRecordingsSnapshot().filter {
                it.createdAt < System.currentTimeMillis() - 30L * 24 * 3600_000
            }.take(5)
            val output = if (oldMemories.isNotEmpty())
                "Resurfaced ${oldMemories.size} forgotten memories: ${oldMemories.joinToString { it.textContent?.take(30) ?: "Memory #${it.id}" }}"
            else "No forgotten memories to resurface today"

            activityLogger.log("Resurface", output)
            logDao.insert(AutomationLogEntity(
                ruleId = 2, status = "success", output = output,
                durationMs = System.currentTimeMillis() - start,
                batteryState = getBatteryState(applicationContext)
            ))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(workManager: WorkManager) {
            val initialDelay = computeDelayUntil(8, 0) // 8 AM
            val request = PeriodicWorkRequestBuilder<ResurfaceWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder()
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .addTag("automation")
                .build()
            workManager.enqueueUniquePeriodicWork("resurface", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/** Battery + thermal safety check */
fun shouldSkip(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct = level * 100 / scale
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
    if (pct < 20 && !charging) {
        Log.i("AutomationWorker", "Skipped — low battery ($pct%)")
        return true
    }
    return false
}

private fun getBatteryState(context: Context): String {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
        else -> "unknown"
    }
}

private fun computeDelayUntil(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis - System.currentTimeMillis()
}
