package com.offlinebot.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.offlinebot.data.database.dao.ChatMessageDao
import com.offlinebot.data.database.dao.BucketDao
import com.offlinebot.data.database.dao.EmbeddingDao
import com.offlinebot.data.database.dao.AutomationLogDao
import com.offlinebot.data.database.dao.AutomationRuleDao
import com.offlinebot.data.database.dao.MediaAttachmentDao
import com.offlinebot.data.database.dao.PhoneContextDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.SystemPromptDao
import com.offlinebot.data.database.dao.TranscriptDao
import com.offlinebot.data.database.entities.AutomationLogEntity
import com.offlinebot.data.database.entities.AutomationRuleEntity
import com.offlinebot.data.database.entities.ChatMessageEntity
import com.offlinebot.data.database.entities.BucketEntity
import com.offlinebot.data.database.entities.ContactContextEntity
import com.offlinebot.data.database.entities.EmbeddingEntity
import com.offlinebot.data.database.entities.MediaAttachmentEntity
import com.offlinebot.data.database.entities.MessageContextEntity
import com.offlinebot.data.database.entities.RecordingEntity
import com.offlinebot.data.database.entities.SystemPromptEntity
import com.offlinebot.data.database.entities.TranscriptEntity

@Database(
    entities = [
        RecordingEntity::class,
        TranscriptEntity::class,
        BucketEntity::class,
        EmbeddingEntity::class,
        ContactContextEntity::class,
        MessageContextEntity::class,
        MediaAttachmentEntity::class,
        ChatMessageEntity::class,
        SystemPromptEntity::class,
        AutomationRuleEntity::class,
        AutomationLogEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OfflineBotDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun bucketDao(): BucketDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun phoneContextDao(): PhoneContextDao
    abstract fun mediaAttachmentDao(): MediaAttachmentDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun systemPromptDao(): SystemPromptDao
    abstract fun automationRuleDao(): AutomationRuleDao
    abstract fun automationLogDao(): AutomationLogDao
}
