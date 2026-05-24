package com.offlinebot.recorder.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStorage @Inject constructor(
    @ApplicationContext val context: Context
) {
    var activeFile: File? = null
        private set

    fun prepareNextRecording(): File {
        val directory = File(context.getExternalFilesDir(null), "audio").also { it.mkdirs() }
        val file = File(directory, "memory-${System.currentTimeMillis()}.wav")
        activeFile = file
        return file
    }

    fun finishRecording(): File? =
        activeFile.also { activeFile = null }
}
