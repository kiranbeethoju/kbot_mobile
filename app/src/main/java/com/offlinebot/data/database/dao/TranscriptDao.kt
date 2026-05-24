package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.offlinebot.data.database.entities.TranscriptEntity

@Dao
interface TranscriptDao {
    @Insert
    suspend fun insert(transcript: TranscriptEntity): Long

    @Query("SELECT * FROM transcripts WHERE transcript LIKE '%' || :query || '%' ORDER BY created_at DESC")
    suspend fun lexicalSearch(query: String): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts WHERE recording_id = :recordingId ORDER BY created_at DESC")
    suspend fun getByRecordingId(recordingId: Long): List<TranscriptEntity>

    @Query("SELECT * FROM transcripts ORDER BY created_at DESC")
    suspend fun allTranscripts(): List<TranscriptEntity>
}
