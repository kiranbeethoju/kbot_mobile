package com.offlinebot.data.database.dao

import androidx.room.*
import com.offlinebot.data.database.entities.AutomationLogEntity
import com.offlinebot.data.database.entities.AutomationRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
    @Insert suspend fun insert(rule: AutomationRuleEntity): Long
    @Update suspend fun update(rule: AutomationRuleEntity)
    @Delete suspend fun delete(rule: AutomationRuleEntity)
    @Query("SELECT * FROM automation_rules WHERE enabled = 1 ORDER BY next_run ASC")
    fun observeEnabled(): Flow<List<AutomationRuleEntity>>
    @Query("SELECT * FROM automation_rules ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>
    @Query("SELECT * FROM automation_rules WHERE id = :id")
    suspend fun getById(id: Long): AutomationRuleEntity?
    @Query("UPDATE automation_rules SET last_run = :ts, next_run = :next WHERE id = :id")
    suspend fun updateRunTimestamps(id: Long, ts: Long, next: Long)
    @Query("UPDATE automation_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("SELECT COUNT(*) FROM automation_rules")
    suspend fun count(): Int
    @Query("SELECT * FROM automation_rules WHERE enabled = 1 AND trigger_type = 'time' AND next_run IS NOT NULL AND next_run <= :now ORDER BY next_run ASC")
    suspend fun pendingTimeBasedRules(now: Long): List<AutomationRuleEntity>
}

@Dao
interface AutomationLogDao {
    @Insert suspend fun insert(log: AutomationLogEntity): Long
    @Query("SELECT * FROM automation_logs ORDER BY created_at DESC LIMIT 100")
    fun observeRecent(): Flow<List<AutomationLogEntity>>
    @Query("SELECT * FROM automation_logs WHERE rule_id = :ruleId ORDER BY created_at DESC LIMIT 20")
    fun observeByRule(ruleId: Long): Flow<List<AutomationLogEntity>>
}
