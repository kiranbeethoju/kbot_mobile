package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.offlinebot.data.database.entities.MediaAttachmentEntity

@Dao
interface MediaAttachmentDao {
    @Insert
    suspend fun insert(attachment: MediaAttachmentEntity): Long

    @Insert
    suspend fun insertAll(attachments: List<MediaAttachmentEntity>): List<Long>

    @Query("SELECT * FROM media_attachments WHERE recording_id = :recordingId")
    suspend fun getByRecordingId(recordingId: Long): List<MediaAttachmentEntity>

    @Query("DELETE FROM media_attachments WHERE recording_id = :recordingId")
    suspend fun deleteByRecordingId(recordingId: Long)
}
