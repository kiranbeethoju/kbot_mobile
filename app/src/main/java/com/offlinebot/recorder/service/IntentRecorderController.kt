package com.offlinebot.recorder.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentRecorderController @Inject constructor(
    @ApplicationContext private val context: Context
) : RecorderController {
    override suspend fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw RecorderPermissionException(Manifest.permission.RECORD_AUDIO)
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START)
        )
    }

    override suspend fun pause() {
        context.startService(
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_PAUSE)
        )
    }

    override suspend fun resume() {
        context.startService(
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_RESUME)
        )
    }

    override suspend fun stop() {
        context.startService(
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP)
        )
    }
}
