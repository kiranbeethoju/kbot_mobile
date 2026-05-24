package com.offlinebot.data.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_context")
data class ContactContextEntity(
    @PrimaryKey val lookupKey: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
