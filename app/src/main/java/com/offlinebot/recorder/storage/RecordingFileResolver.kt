package com.offlinebot.recorder.storage

import android.content.Context
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingFileResolver @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    fun resolve(storedPath: String): File? {
        if (storedPath.isBlank()) return null

        val direct = File(storedPath)
        if (direct.isFile && direct.length() > 44L) return direct

        val name = direct.name
        if (name.isNotBlank()) {
            val underAudio = File(context.getExternalFilesDir(null), "audio/$name")
            if (underAudio.isFile && underAudio.length() > 44L) return underAudio
        }

        return null
    }
}
