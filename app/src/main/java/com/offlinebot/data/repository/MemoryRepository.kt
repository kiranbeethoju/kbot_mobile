package com.offlinebot.data.repository

import com.offlinebot.data.database.dao.RecordingDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class HomeStats(
    val recentCount: Int,
    val weekCount: Int,
    val pendingProcessingCount: Int
)

@Singleton
class MemoryRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val processingQueue: ProcessingQueue
) {
    fun observeHomeStats(): Flow<HomeStats> {
        val dayAgo = System.currentTimeMillis() - ONE_DAY_MS
        val weekAgo = System.currentTimeMillis() - ONE_WEEK_MS
        return combine(
            recordingDao.countSince(dayAgo),
            recordingDao.countSince(weekAgo),
            recordingDao.countPending()
        ) { recent, week, pending ->
            HomeStats(recent, week, pending)
        }
    }

    fun enqueuePendingProcessing() {
        processingQueue.enqueuePendingMemories()
    }

    private companion object {
        const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
        const val ONE_WEEK_MS = 7L * ONE_DAY_MS
    }
}
