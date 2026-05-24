package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.offlinebot.data.database.entities.SystemPromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemPromptDao {
    @Insert
    suspend fun insert(prompt: SystemPromptEntity): Long

    @Update
    suspend fun update(prompt: SystemPromptEntity)

    @Query("DELETE FROM system_prompts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM system_prompts ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SystemPromptEntity>>

    @Query("SELECT * FROM system_prompts WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<SystemPromptEntity?>

    @Query("UPDATE system_prompts SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE system_prompts SET is_active = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Query("SELECT * FROM system_prompts WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveNow(): SystemPromptEntity?
}
