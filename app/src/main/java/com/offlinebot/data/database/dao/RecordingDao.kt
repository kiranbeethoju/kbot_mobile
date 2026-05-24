package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.offlinebot.data.database.entities.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("SELECT COUNT(*) FROM recordings WHERE created_at >= :since")
    fun countSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM recordings")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recordings WHERE processed = 0")
    fun countPending(): Flow<Int>

    @Query("SELECT * FROM recordings WHERE processed = 0 ORDER BY created_at ASC")
    suspend fun pending(): List<RecordingEntity>

    @Query(
        """
        UPDATE recordings
        SET processed = 1, transcript_id = :transcriptId, embedding_id = :embeddingId
        WHERE id = :recordingId
        """
    )
    suspend fun markProcessed(recordingId: Long, transcriptId: Long, embeddingId: Long)

    @Query(
        """
        UPDATE recordings
        SET processed = 1, transcript_id = :transcriptId
        WHERE id = :recordingId
        """
    )
    suspend fun markTranscribed(recordingId: Long, transcriptId: Long)

    @Query("SELECT * FROM recordings ORDER BY created_at DESC")
    fun allRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recordings ORDER BY created_at DESC")
    suspend fun allRecordingsSnapshot(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE text_content IS NOT NULL AND text_content != '' ORDER BY created_at DESC LIMIT :limit")
    suspend fun recentTextNotes(limit: Int = 10): List<RecordingEntity>

    @Query("UPDATE recordings SET text_content = :text WHERE id = :id")
    suspend fun updateTextContent(id: Long, text: String)

    @Query("SELECT COUNT(*) FROM recordings WHERE processed = 0")
    suspend fun countPendingSnapshot(): Int
}
