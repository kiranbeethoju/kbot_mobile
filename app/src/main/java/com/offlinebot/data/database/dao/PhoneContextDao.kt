package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.offlinebot.data.database.entities.ContactContextEntity
import com.offlinebot.data.database.entities.MessageContextEntity

@Dao
interface PhoneContextDao {
    @Upsert
    suspend fun upsertContacts(contacts: List<ContactContextEntity>)

    @Upsert
    suspend fun upsertMessages(messages: List<MessageContextEntity>)

    @Query("SELECT COUNT(*) FROM contact_context")
    suspend fun contactCount(): Int

    @Query("SELECT COUNT(*) FROM message_context")
    suspend fun messageCount(): Int
}
