package com.offlinebot.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_context")
data class MessageContextEntity(
    @PrimaryKey val id: Long,
    val address: String?,
    val body: String,
    @ColumnInfo(name = "sent_at") val sentAt: Long,
    @ColumnInfo(name = "imported_at") val importedAt: Long
)
