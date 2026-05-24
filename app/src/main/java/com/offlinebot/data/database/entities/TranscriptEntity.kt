package com.offlinebot.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    val transcript: String,
    val summary: String? = null,
    val keywords: List<String> = emptyList(),
    val sentiment: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
