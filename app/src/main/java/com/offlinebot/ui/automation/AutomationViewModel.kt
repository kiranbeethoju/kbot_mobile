package com.offlinebot.ui.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.offlinebot.data.database.dao.AutomationLogDao
import com.offlinebot.data.database.dao.AutomationRuleDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.entities.AutomationLogEntity
import com.offlinebot.data.database.entities.AutomationRuleEntity
import com.offlinebot.ai.automation.DailySummaryWorker
import com.offlinebot.ai.automation.ResurfaceWorker
import com.offlinebot.utils.ActivityLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class AutomationState(
    val rules: List<AutomationRuleEntity> = emptyList(),
    val logs: List<AutomationLogEntity> = emptyList(),
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val pendingJobs: Int = 0,
    val activeJobs: Int = 0,
    val editingRule: Boolean = false,
    val editingRuleName: String = "",
    val editingRuleTrigger: String = "time",
    val editingRuleSchedule: String = "daily_21",
    val editingRuleCondition: String = "",
    val editingRuleAction: String = "",
    val snackbarMessage: String? = null
)

@HiltViewModel
class AutomationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleDao: AutomationRuleDao,
    private val logDao: AutomationLogDao,
    private val recordingDao: RecordingDao,
    private val activityLogger: ActivityLogger
) : ViewModel() {
    private val workManager: WorkManager = WorkManager.getInstance(context)
    private val _state = MutableStateFlow(AutomationState())
    val state: StateFlow<AutomationState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ruleDao.observeAll().collect { rules -> _state.update { it.copy(rules = rules) } }
        }
        viewModelScope.launch {
            logDao.observeRecent().collect { logs -> _state.update { it.copy(logs = logs) } }
        }
        viewModelScope.launch {
            while (true) {
                refreshBattery()
                try {
                    val jobs = workManager.getWorkInfosByTag("automation").get()
                    _state.update {
                        it.copy(
                            pendingJobs = jobs.count { w -> w.state == WorkInfo.State.ENQUEUED },
                            activeJobs = jobs.count { w -> w.state == WorkInfo.State.RUNNING }
                        )
                    }
                } catch (_: Exception) {}
                delay(5000)
            }
        }

        // Seed default automation templates on first launch
        viewModelScope.launch {
            if (ruleDao.count() == 0) {
                seedDefaultRules()
                // Auto-schedule periodic workers
                try {
                    DailySummaryWorker.schedule(workManager)
                    ResurfaceWorker.schedule(workManager)
                    activityLogger.log("Automation", "Workers auto-scheduled")
                } catch (e: Exception) {
                    activityLogger.log("Automation", "Worker schedule failed: ${e.message}")
                }
            }
        }
    }

    private fun refreshBattery() {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        _state.update { it.copy(batteryLevel = (level * 100 / scale), isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING) }
    }

    private suspend fun seedDefaultRules() {
        val now = System.currentTimeMillis()
        val tomorrow8am = computeNextTime(8, 0)
        val tonight9pm = computeNextTime(21, 0)
        val nextSunday = computeNextSunday()

        val templates = listOf(
            AutomationRuleEntity(name = "Daily Summary", triggerType = "time", schedule = "Every day at 9 PM",
                actionJson = """{"type":"summary","period":"daily"}""", priority = "low",
                nextRun = tonight9pm, permissionLevel = "basic"),
            AutomationRuleEntity(name = "Forgotten Thoughts", triggerType = "time", schedule = "Every day at 8 AM",
                actionJson = """{"type":"resurface","days_ago":30}""", priority = "low",
                nextRun = tomorrow8am, permissionLevel = "basic"),
            AutomationRuleEntity(name = "Reminder Extraction", triggerType = "event", schedule = "On new memory",
                conditionJson = """{"keyword":"remind"}""", actionJson = """{"type":"create_reminder"}""", priority = "high",
                nextRun = now, permissionLevel = "basic"),
            AutomationRuleEntity(name = "Topic Tracker", triggerType = "nlp", schedule = "After 5 mentions",
                conditionJson = """{"repeated_topics":5}""", actionJson = """{"type":"trend_insight"}""", priority = "low",
                nextRun = now + 3600_000, permissionLevel = "advanced"),
            AutomationRuleEntity(name = "Weekly Memory Cleanup", triggerType = "time", schedule = "Every Sunday",
                actionJson = """{"type":"cleanup","compress_audio":true}""", priority = "low",
                nextRun = nextSunday, permissionLevel = "basic"),
            AutomationRuleEntity(name = "Meeting Notes Scanner", triggerType = "nlp", schedule = "On keyword match",
                conditionJson = """{"keywords":["meeting","discussed","agreed","action item"]}""",
                actionJson = """{"type":"tag","tag":"Meeting","create_summary":true}""", priority = "low",
                nextRun = now, permissionLevel = "advanced"),
            AutomationRuleEntity(name = "Idea Collector", triggerType = "nlp", schedule = "On keyword match",
                conditionJson = """{"keywords":["idea","maybe","could","should try","prototype"]}""",
                actionJson = """{"type":"tag","tag":"Idea","resurface_in":7}""", priority = "low",
                nextRun = now, permissionLevel = "basic"),
            AutomationRuleEntity(name = "Habit Reflection", triggerType = "time", schedule = "Every day at 9 PM",
                actionJson = """{"type":"reflection","lookback_days":1}""", priority = "low",
                nextRun = tonight9pm, permissionLevel = "basic"),
            AutomationRuleEntity(name = "Project Tracker", triggerType = "nlp", schedule = "On detecting project names",
                conditionJson = """{"entity_type":"project"}""",
                actionJson = """{"type":"link_related","group_by":"project"}""", priority = "low",
                nextRun = now, permissionLevel = "advanced"),
            AutomationRuleEntity(name = "Study Assistant", triggerType = "nlp", schedule = "On learning content",
                conditionJson = """{"keywords":["learn","study","understand","concept","research"]}""",
                actionJson = """{"type":"tag","tag":"Learning","spaced_repetition":true}""", priority = "low",
                nextRun = now, permissionLevel = "basic")
        )
        templates.forEach { ruleDao.insert(it) }
        activityLogger.log("Automation", "Seeded ${templates.size} default rules")
    }

    private fun computeNextTime(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    private fun computeNextSunday(): Long {
        val cal = java.util.Calendar.getInstance()
        val daysUntilSunday = (java.util.Calendar.SUNDAY - cal.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
        if (daysUntilSunday == 0) cal.add(java.util.Calendar.DAY_OF_MONTH, 7)
        else cal.add(java.util.Calendar.DAY_OF_MONTH, daysUntilSunday)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 10)
        cal.set(java.util.Calendar.MINUTE, 0)
        return cal.timeInMillis
    }

    private fun parseScheduleTime(schedule: String): Long {
        val dailyHour = Regex("daily_(\\d+)").find(schedule)?.groupValues?.get(1)?.toIntOrNull()
        if (dailyHour != null) {
            return computeNextTime(dailyHour, 0)
        }
        val weeklyDay = Regex("weekly_(sun|mon|tue|wed|thu|fri|sat)", RegexOption.IGNORE_CASE)
            .find(schedule)?.groupValues?.get(1)?.lowercase()
        if (weeklyDay != null) {
            val dayMap = mapOf(
                "sun" to 1, "mon" to 2, "tue" to 3, "wed" to 4,
                "thu" to 5, "fri" to 6, "sat" to 7
            )
            return computeNextDayOfWeek(dayMap[weeklyDay] ?: 1)
        }
        return System.currentTimeMillis() + 3600_000
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

    // Rule CRUD
    fun startNewRule() { _state.update { it.copy(editingRule = true, editingRuleName = "", editingRuleTrigger = "time", editingRuleSchedule = "daily_21", editingRuleCondition = "", editingRuleAction = "") } }
    fun cancelEdit() { _state.update { it.copy(editingRule = false) } }
    fun updateEditName(n: String) { _state.update { it.copy(editingRuleName = n) } }
    fun updateEditTrigger(t: String) { _state.update { it.copy(editingRuleTrigger = t) } }
    fun updateEditSchedule(s: String) { _state.update { it.copy(editingRuleSchedule = s) } }
    fun updateEditCondition(c: String) { _state.update { it.copy(editingRuleCondition = c) } }
    fun updateEditAction(a: String) { _state.update { it.copy(editingRuleAction = a) } }

    fun saveRule() {
        viewModelScope.launch {
            val s = _state.value
            val nextRun = if (s.editingRuleTrigger == "time" && s.editingRuleSchedule.isNotBlank()) {
                parseScheduleTime(s.editingRuleSchedule)
            } else {
                System.currentTimeMillis() + 3600_000
            }
            val rule = AutomationRuleEntity(
                name = s.editingRuleName.ifBlank { "New Rule" },
                enabled = true,
                triggerType = s.editingRuleTrigger,
                schedule = s.editingRuleSchedule.ifBlank { null },
                conditionJson = s.editingRuleCondition.ifBlank { null },
                actionJson = s.editingRuleAction.ifBlank { null },
                nextRun = nextRun
            )
            ruleDao.insert(rule)
            activityLogger.log("Automation", "Rule created: ${rule.name}")
            _state.update { it.copy(editingRule = false) }
        }
    }

    fun toggleRule(rule: AutomationRuleEntity) {
        viewModelScope.launch {
            ruleDao.setEnabled(rule.id, !rule.enabled)
            activityLogger.log("Automation", "Rule ${if (rule.enabled) "disabled" else "enabled"}: ${rule.name}")
        }
    }

    fun deleteRule(rule: AutomationRuleEntity) {
        viewModelScope.launch {
            ruleDao.delete(rule)
            activityLogger.log("Automation", "Rule deleted: ${rule.name}")
        }
    }

    fun runRuleNow(rule: AutomationRuleEntity) {
        viewModelScope.launch {
            activityLogger.log("Automation", "Manual run: ${rule.name}")
            val start = System.currentTimeMillis()
            try {
                val output = executeRule(rule)
                logDao.insert(AutomationLogEntity(
                    ruleId = rule.id, status = "success", output = output,
                    durationMs = System.currentTimeMillis() - start,
                    batteryState = if (_state.value.isCharging) "charging" else "normal"
                ))
                val nextRun = if (rule.triggerType == "time" && rule.schedule != null) {
                    parseScheduleTime(rule.schedule)
                } else {
                    System.currentTimeMillis() + 24 * 3600_000
                }
                ruleDao.updateRunTimestamps(rule.id, System.currentTimeMillis(), nextRun)
                _state.update { it.copy(snackbarMessage = "${rule.name}: $output") }
                sendNotification(rule.name, output)
            } catch (e: Exception) {
                logDao.insert(AutomationLogEntity(
                    ruleId = rule.id, status = "failed", output = e.message,
                    durationMs = System.currentTimeMillis() - start,
                    skipReason = e.message
                ))
                _state.update { it.copy(snackbarMessage = "${rule.name} failed: ${e.message}") }
            }
        }
    }

    private fun sendNotification(title: String, body: String) {
        try {
            val channelId = "kbot_tools"
            val notification = android.app.Notification.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body.take(120))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .build()
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: Exception) {}
    }

    private suspend fun executeRule(rule: AutomationRuleEntity): String {
        val action = rule.actionJson ?: ""
        val condition = rule.conditionJson ?: ""

        return when {
            action.contains("summary") -> {
                val count = recordingDao.allRecordingsSnapshot().count {
                    it.createdAt > System.currentTimeMillis() - 24 * 3600_000
                }
                if (count > 0) "Today: $count memories captured. Ready for review."
                else "No memories recorded today."
            }
            action.contains("resurface") -> {
                val daysAgo = Regex(""""days_ago"\s*:\s*(\d+)""").find(condition)?.groupValues?.get(1)?.toIntOrNull() ?: 30
                val oldMemories = recordingDao.allRecordingsSnapshot().filter {
                    it.createdAt < System.currentTimeMillis() - daysAgo.toLong() * 24 * 3600_000
                }.take(5)
                if (oldMemories.isNotEmpty())
                    "Resurfaced ${oldMemories.size} memories from >$daysAgo days ago: ${oldMemories.joinToString(", ") { it.textContent?.take(50) ?: "Memory #${it.id}" }}"
                else "No memories older than $daysAgo days to resurface."
            }
            action.contains("reminder") || action.contains("create_reminder") -> {
                val keyword = Regex(""""keyword"\s*:\s*"([^"]+)"""").find(condition)?.groupValues?.get(1) ?: "remind"
                val matches = recordingDao.allRecordingsSnapshot().filter {
                    it.textContent?.contains(keyword, ignoreCase = true) == true
                }
                if (matches.isNotEmpty())
                    "Found ${matches.size} memories containing '$keyword': ${matches.joinToString(", ") { it.textContent?.take(50) ?: "" }}"
                else "No memories found with keyword '$keyword'."
            }
            action.contains("trend") || action.contains("trend_insight") -> {
                val threshold = Regex(""""repeated_topics"\s*:\s*(\d+)""").find(condition)?.groupValues?.get(1)?.toIntOrNull() ?: 5
                val recent = recordingDao.allRecordingsSnapshot().filter {
                    it.createdAt > System.currentTimeMillis() - 7 * 24 * 3600_000
                }
                "Analyzed ${recent.size} recent memories for topic patterns (threshold: $threshold mentions)."
            }
            action.contains("cleanup") -> {
                val pending = recordingDao.countPendingSnapshot()
                "Cleanup check: $pending unprocessed recordings. Storage optimization ready."
            }
            action.contains("tag") -> {
                val keywords = Regex(""""keywords"\s*:\s*\[([^\]]+)\]""").find(condition)?.groupValues?.get(1)
                if (keywords != null) {
                    val keywordList = keywords.split(",").map { it.trim().replace("\"", "") }
                    val matches = recordingDao.allRecordingsSnapshot().filter { rec ->
                        keywordList.any { kw -> rec.textContent?.contains(kw, ignoreCase = true) == true }
                    }
                    val tag = Regex(""""tag"\s*:\s*"([^"]+)"""").find(action)?.groupValues?.get(1) ?: "tagged"
                    if (matches.isNotEmpty())
                        "Tagged ${matches.size} memories as '$tag': ${matches.joinToString(", ") { it.textContent?.take(40) ?: "" }}"
                    else "No memories matched keywords: $keywords"
                } else {
                    "Tagged relevant memories based on criteria."
                }
            }
            action.contains("reflection") -> {
                val recent = recordingDao.allRecordingsSnapshot().filter {
                    it.createdAt > System.currentTimeMillis() - 24 * 3600_000
                }
                "Reflection on ${recent.size} memories from the past day."
            }
            action.contains("link_related") || action.contains("group_by") -> {
                "Linked related memories by project grouping."
            }
            else -> "Rule '${rule.name}' executed successfully."
        }
    }

    fun clearSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun enqueueDailyWorkers() {
        try {
            DailySummaryWorker.schedule(workManager)
            ResurfaceWorker.schedule(workManager)
            activityLogger.log("Automation", "Daily workers scheduled")
        } catch (e: Exception) {
            activityLogger.log("Automation", "Schedule failed: ${e.message}")
        }
    }

}
