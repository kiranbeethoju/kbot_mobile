package com.offlinebot.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "enabled") val enabled: Boolean = true,
    @ColumnInfo(name = "trigger_type") val triggerType: String, // time, event, nlp
    @ColumnInfo(name = "schedule") val schedule: String? = null, // cron-like or "daily_9pm"
    @ColumnInfo(name = "condition_json") val conditionJson: String? = null,
    @ColumnInfo(name = "action_json") val actionJson: String? = null,
    @ColumnInfo(name = "priority") val priority: String = "low", // low, high
    @ColumnInfo(name = "permission_level") val permissionLevel: String = "basic", // basic, advanced, experimental
    @ColumnInfo(name = "last_run") val lastRun: Long? = null,
    @ColumnInfo(name = "next_run") val nextRun: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_logs")
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    @ColumnInfo(name = "status") val status: String, // success, failed, skipped
    @ColumnInfo(name = "output") val output: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "battery_state") val batteryState: String? = null, // charging, low, normal
    @ColumnInfo(name = "skip_reason") val skipReason: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
