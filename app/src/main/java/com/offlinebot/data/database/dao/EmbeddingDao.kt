package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.offlinebot.data.database.entities.EmbeddingEntity

@Dao
interface EmbeddingDao {
    @Insert
    suspend fun insert(embedding: EmbeddingEntity): Long

    @Query("SELECT * FROM embeddings")
    suspend fun all(): List<EmbeddingEntity>
}
