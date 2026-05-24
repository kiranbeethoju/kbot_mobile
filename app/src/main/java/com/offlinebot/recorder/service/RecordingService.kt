package com.offlinebot.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.offlinebot.R
import com.offlinebot.data.database.dao.RecordingDao
import com.offlinebot.data.database.entities.RecordingEntity
import com.offlinebot.recorder.storage.LocalAudioRecorder
import com.offlinebot.utils.LocationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var recorder: LocalAudioRecorder
    @Inject lateinit var recordingDao: RecordingDao
    @Inject lateinit var locationHelper: LocationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        try {
            ensureChannel()
            startForeground(NOTIFICATION_ID, notification("Recording locally"))
            recorder.start()
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to start recording", error)
            Toast.makeText(this, error.message ?: "Could not start recording. Check microphone permission.", Toast.LENGTH_LONG).show()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecording() {
        if (recorder.isRecording && !recorder.isPaused) {
            recorder.pause()
            updateNotification("Recording paused")
        }
    }

    private fun resumeRecording() {
        if (recorder.isRecording && recorder.isPaused) {
            recorder.resume()
            updateNotification("Recording locally")
        }
    }

    private fun stopRecording() {
        val finished = recorder.stop()
        if (finished != null) {
            serviceScope.launch {
                try {
                    val audioFile = java.io.File(finished.filePath)
                    if (!audioFile.exists() || audioFile.length() <= 44L) {
                        Log.e(TAG, "Recording file missing or empty: ${finished.filePath}")
                        return@launch
                    }
                    val loc = try { locationHelper.lastKnownLocation } catch (_: Exception) { null }
                    val id = recordingDao.insert(
                        RecordingEntity(
                            filePath = finished.filePath,
                            durationMs = finished.durationMs,
                            createdAt = finished.createdAt,
                            inputType = "audio",
                            latitude = loc?.latitude,
                            longitude = loc?.longitude
                        )
                    )
                    Log.i(TAG, "Saved recording id=$id path=${finished.filePath} dur=${finished.durationMs}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save recording to database", e)
                } finally {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (recorder.isRecording) {
            recorder.stop()
        }
        super.onDestroy()
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(text))
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val ACTION_START = "com.offlinebot.action.START_RECORDING"
        const val ACTION_PAUSE = "com.offlinebot.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.offlinebot.action.RESUME_RECORDING"
        const val ACTION_STOP = "com.offlinebot.action.STOP_RECORDING"
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
    }
}
