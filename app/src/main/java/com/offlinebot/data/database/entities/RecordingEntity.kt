package com.offlinebot.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val processed: Boolean = false,
    @ColumnInfo(name = "transcript_id") val transcriptId: Long? = null,
    @ColumnInfo(name = "bucket_id") val bucketId: Long? = null,
    @ColumnInfo(name = "embedding_id") val embeddingId: Long? = null,
    @ColumnInfo(name = "input_type") val inputType: String = "audio",
    @ColumnInfo(name = "text_content") val textContent: String? = null,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null
)
