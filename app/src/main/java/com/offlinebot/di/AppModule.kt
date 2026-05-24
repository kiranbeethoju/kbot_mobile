package com.offlinebot.di

import android.content.Context
import androidx.room.Room
import com.offlinebot.ai.ModelPaths
import com.offlinebot.ai.embeddings.EmbeddingEngine
import com.offlinebot.ai.embeddings.MiniLmOnnxEmbeddingEngine
import com.offlinebot.ai.llm.LlamaCppEngine
import com.offlinebot.ai.llm.LocalLlmEngine
import com.offlinebot.ai.whisper.WhisperCppEngine
import com.offlinebot.ai.whisper.WhisperEngine
import com.offlinebot.data.database.OfflineBotDatabase
import com.offlinebot.data.database.dao.ChatMessageDao
import com.offlinebot.data.database.dao.EmbeddingDao
import com.offlinebot.data.database.dao.AutomationLogDao
import com.offlinebot.data.database.dao.AutomationRuleDao
import com.offlinebot.data.database.dao.MediaAttachmentDao
import com.offlinebot.data.database.dao.PhoneContextDao
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.dao.SystemPromptDao
import com.offlinebot.data.database.dao.TranscriptDao
import com.offlinebot.recorder.service.IntentRecorderController
import com.offlinebot.recorder.service.RecorderController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    abstract fun bindRecorderController(controller: IntentRecorderController): RecorderController

    @Binds
    abstract fun bindEmbeddingEngine(engine: MiniLmOnnxEmbeddingEngine): EmbeddingEngine

    @Binds
    abstract fun bindWhisperEngine(engine: WhisperCppEngine): WhisperEngine

    @Binds
    abstract fun bindLlmEngine(engine: LlamaCppEngine): LocalLlmEngine
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OfflineBotDatabase =
        Room.databaseBuilder(context, OfflineBotDatabase::class.java, "offlinebot.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecordingDao(database: OfflineBotDatabase): RecordingDao = database.recordingDao()

    @Provides
    fun provideTranscriptDao(database: OfflineBotDatabase): TranscriptDao = database.transcriptDao()

    @Provides
    fun provideEmbeddingDao(database: OfflineBotDatabase): EmbeddingDao = database.embeddingDao()

    @Provides
    fun providePhoneContextDao(database: OfflineBotDatabase): PhoneContextDao = database.phoneContextDao()

    @Provides
    fun provideMediaAttachmentDao(database: OfflineBotDatabase): MediaAttachmentDao = database.mediaAttachmentDao()

    @Provides
    fun provideChatMessageDao(database: OfflineBotDatabase): ChatMessageDao = database.chatMessageDao()

    @Provides
    fun provideSystemPromptDao(database: OfflineBotDatabase): SystemPromptDao = database.systemPromptDao()

    @Provides
    fun provideAutomationRuleDao(database: OfflineBotDatabase): AutomationRuleDao = database.automationRuleDao()
    @Provides
    fun provideAutomationLogDao(database: OfflineBotDatabase): AutomationLogDao = database.automationLogDao()
    @Provides
    fun provideModelPaths(@ApplicationContext context: Context): ModelPaths = ModelPaths(context)
}
