package com.offlinebot.ai.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
            sendWorkerNotification(applicationContext, "Daily Summary", output)
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
                    .setRequiresBatteryNotLow(false)
                    .setRequiresDeviceIdle(false)
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
            sendWorkerNotification(applicationContext, "Forgotten Thoughts", output)
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
                    .setRequiresBatteryNotLow(false)
                    .setRequiresDeviceIdle(false)
                    .build())
                .addTag("automation")
                .build()
            workManager.enqueueUniquePeriodicWork("resurface", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/** Runs every 15 minutes — picks up any time-based rules whose nextRun has passed */
@HiltWorker
class RuleSchedulerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ruleDao: AutomationRuleDao,
    private val logDao: AutomationLogDao,
    private val recordingDao: RecordingDao,
    private val activityLogger: ActivityLogger
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (shouldSkip(applicationContext)) return Result.success()
        val now = System.currentTimeMillis()
        val pending = ruleDao.pendingTimeBasedRules(now)
        if (pending.isEmpty()) return Result.success()

        activityLogger.log("RuleScheduler", "Found ${pending.size} pending rules")
        for (rule in pending) {
            val start = System.currentTimeMillis()
            try {
                val output = executeRuleLogic(rule)
                logDao.insert(AutomationLogEntity(
                    ruleId = rule.id, status = "success", output = output,
                    durationMs = System.currentTimeMillis() - start,
                    batteryState = getBatteryState(applicationContext)
                ))
                val nextRun = parseRuleSchedule(rule.schedule)
                ruleDao.updateRunTimestamps(rule.id, now, nextRun)
                sendWorkerNotification(applicationContext, rule.name, output)
                activityLogger.log("RuleScheduler", "Ran: ${rule.name} → $output")
            } catch (e: Exception) {
                logDao.insert(AutomationLogEntity(
                    ruleId = rule.id, status = "failed", output = e.message,
                    durationMs = System.currentTimeMillis() - start,
                    skipReason = e.message
                ))
            }
        }
        return Result.success()
    }

    private suspend fun executeRuleLogic(rule: com.offlinebot.data.database.entities.AutomationRuleEntity): String {
        val action = rule.actionJson ?: ""
        val condition = rule.conditionJson ?: ""
        return when {
            action.contains("summary") -> {
                val count = recordingDao.allRecordingsSnapshot().count {
                    it.createdAt > System.currentTimeMillis() - 24 * 3600_000
                }
                if (count > 0) "Today: $count memories captured." else "No memories recorded today."
            }
            action.contains("resurface") -> {
                val daysAgo = Regex(""""days_ago"\s*:\s*(\d+)""").find(condition)?.groupValues?.get(1)?.toIntOrNull() ?: 30
                val oldMemories = recordingDao.allRecordingsSnapshot().filter {
                    it.createdAt < System.currentTimeMillis() - daysAgo.toLong() * 24 * 3600_000
                }.take(5)
                if (oldMemories.isNotEmpty())
                    "Resurfaced ${oldMemories.size} memories from >$daysAgo days."
                else "No old memories to resurface."
            }
            action.contains("reflection") -> {
                val recent = recordingDao.allRecordingsSnapshot().filter {
                    it.createdAt > System.currentTimeMillis() - 24 * 3600_000
                }
                "Reflection on ${recent.size} memories from the past day."
            }
            action.contains("cleanup") -> {
                val pending = recordingDao.countPendingSnapshot()
                "Cleanup: $pending unprocessed recordings."
            }
            else -> "Rule '${rule.name}' executed."
        }
    }

    companion object {
        var intervalMinutes: Long = 5 // Configurable polling interval

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<RuleSchedulerWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder()
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(false)
                    .setRequiresDeviceIdle(false)
                    .build())
                .addTag("automation")
                .build()
            workManager.enqueueUniquePeriodicWork("rule_scheduler", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

private fun parseRuleSchedule(schedule: String?): Long {
    if (schedule == null) return System.currentTimeMillis() + 3600_000
    // "daily_21" or "daily_8"
    val dailyHour = Regex("daily_(\\d+)").find(schedule)?.groupValues?.get(1)?.toIntOrNull()
    if (dailyHour != null) return computeNextOccurrence(dailyHour, 0)
    // "Every day at 9 PM" or "Every day at 8 AM"
    val everyDayAt = Regex("""every\s+day\s+at\s+(\d+)\s*(am|pm)""", RegexOption.IGNORE_CASE).find(schedule)
    if (everyDayAt != null) {
        var hour = everyDayAt.groupValues[1].toIntOrNull() ?: return System.currentTimeMillis() + 24 * 3600_000
        val ampm = everyDayAt.groupValues[2].lowercase()
        if (ampm == "pm" && hour < 12) hour += 12
        if (ampm == "am" && hour == 12) hour = 0
        return computeNextOccurrence(hour, 0)
    }
    // "weekly_sun", "weekly_mon", etc.
    val weeklyDay = Regex("weekly_(sun|mon|tue|wed|thu|fri|sat)", RegexOption.IGNORE_CASE)
        .find(schedule)?.groupValues?.get(1)?.lowercase()
    if (weeklyDay != null) {
        val dayMap = mapOf("sun" to 1, "mon" to 2, "tue" to 3, "wed" to 4, "thu" to 5, "fri" to 6, "sat" to 7)
        return computeNextDayOfWeek(dayMap[weeklyDay] ?: 1)
    }
    // "Every Sunday"
    if (schedule.contains("Sunday", ignoreCase = true)) return computeNextDayOfWeek(1)
    return System.currentTimeMillis() + 24 * 3600_000
}

private fun computeNextOccurrence(hour: Int, minute: Int): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

private fun computeNextDayOfWeek(targetDay: Int): Long {
    val cal = java.util.Calendar.getInstance()
    val currentDay = cal.get(java.util.Calendar.DAY_OF_WEEK)
    val daysUntil = (targetDay - currentDay + 7) % 7
    if (daysUntil == 0) cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
    else cal.add(java.util.Calendar.DAY_OF_MONTH, daysUntil)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    return cal.timeInMillis
}

/** Send a notification from a worker context */
private fun sendWorkerNotification(context: Context, title: String, body: String) {
    try {
        val channelId = "kbot_automation_v2"
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = android.app.NotificationChannel(channelId, "KBot Automation", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body.take(120))
            .setSmallIcon(com.offlinebot.R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(title.hashCode(), notification)
    } catch (_: Exception) {}
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
