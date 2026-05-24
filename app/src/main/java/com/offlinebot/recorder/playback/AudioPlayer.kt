package com.offlinebot.recorder.playback

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AudioPlayer @Inject constructor() {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val positionRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                _currentPosition.value = mp.currentPosition
                handler.postDelayed(this, 200)
            }
        }
    }

    fun play(filePath: String): Boolean {
        stop()

        val file = File(filePath)
        if (!file.exists() || file.length() <= 44L) {
            Log.e(TAG, "Audio file not found or empty: $filePath")
            return false
        }

        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            currentFile = filePath
            _isPlaying.value = true
            handler.post(positionRunnable)

            mp.setOnCompletionListener {
                _isPlaying.value = false
                _currentPosition.value = 0
                currentFile = null
                handler.removeCallbacks(positionRunnable)
                mp.release()
                if (mediaPlayer == mp) mediaPlayer = null
            }

            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra for $filePath")
                _isPlaying.value = false
                currentFile = null
                handler.removeCallbacks(positionRunnable)
                mp.release()
                if (mediaPlayer == mp) mediaPlayer = null
                true
            }

            Log.i(TAG, "Playing: $filePath (${file.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play: $filePath", e)
            _isPlaying.value = false
            false
        }
    }

    fun pause() {
        mediaPlayer?.apply {
            if (isPlaying) {
                pause()
                _isPlaying.value = false
                handler.removeCallbacks(positionRunnable)
            }
        }
    }

    fun resume() {
        mediaPlayer?.apply {
            if (!isPlaying) {
                start()
                _isPlaying.value = true
                handler.post(positionRunnable)
            }
        }
    }

    fun stop() {
        handler.removeCallbacks(positionRunnable)
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) {}
            try {
                release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        currentFile = null
        _isPlaying.value = false
        _currentPosition.value = 0
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}
