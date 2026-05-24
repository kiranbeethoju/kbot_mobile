package com.offlinebot.recorder.storage

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioRecorder(private val outputFile: File) {
    private val sampleRate = 16000
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var isRecording = false
    @Volatile private var isPaused = false

    @Volatile var totalFramesWritten: Long = 0
        private set

    private val bufferSize: Int = run {
        val minSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minSize <= 0) {
            Log.w(TAG, "getMinBufferSize returned $minSize, using fallback 4096")
            4096
        } else {
            minSize * 2 // double for safety margin
        }
    }

    fun start() {
        if (isRecording) return

        var recorder: AudioRecord
        try {
            recorder = buildAudioRecord(MediaRecorder.AudioSource.MIC)
        } catch (e: Exception) {
            Log.w(TAG, "MIC failed, falling back to VOICE_RECOGNITION", e)
            try {
                recorder = buildAudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            } catch (e2: Exception) {
                throw IllegalStateException(
                    "Mic not available. Check permissions and that no other app is using the mic.", e2
                )
            }
        }

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            recorder.release()
            throw IllegalStateException("startRecording() failed: ${e.message}", e)
        }

        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            throw IllegalStateException(
                "AudioRecord not recording (recordingState=${recorder.recordingState})"
            )
        }

        audioRecord = recorder
        isRecording = true
        isPaused = false
        totalFramesWritten = 0

        // Write placeholder WAV header (will be updated on stop)
        val header = createWavHeader(0)
        outputFile.outputStream().use { it.write(header) }

        recordingThread = Thread({
            val buffer = ByteArray(bufferSize)
            val fos = FileOutputStream(outputFile, true)
            try {
                Log.i(TAG, "Recording started → ${outputFile.absolutePath}")
                while (isRecording) {
                    if (isPaused) {
                        Thread.sleep(50)
                        continue
                    }
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read <= 0) {
                        if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                            read == AudioRecord.ERROR_DEAD_OBJECT) {
                            Log.e(TAG, "AudioRecord read error: $read — stopping")
                            break
                        }
                        continue
                    }
                    fos.write(buffer, 0, read)
                    totalFramesWritten += read / 2 // 16-bit = 2 bytes per frame
                }
                fos.fd.sync()
            } finally {
                fos.close()
                Log.i(TAG, "Recording stream closed (frames=$totalFramesWritten)")
            }
        }, "wav-recorder").also { it.start() }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun stop() {
        isRecording = false
        recordingThread?.join(2000)
        audioRecord?.apply {
            try {
                stop()
            } catch (_: Exception) {}
            try {
                release()
            } catch (_: Exception) {}
        }
        audioRecord = null
        recordingThread = null

        // Update WAV header with actual data size
        updateWavHeader(outputFile, totalFramesWritten)
        Log.i(TAG, "Recording stopped: ${outputFile.absolutePath} (${outputFile.length()} bytes, $totalFramesWritten frames)")
    }

    fun release() {
        if (isRecording) stop()
    }

    private fun createWavHeader(dataSizeFrames: Long): ByteArray {
        val dataSize = dataSizeFrames * 2 // 16-bit = 2 bytes per sample
        val fileSize = dataSize + 36
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt((fileSize).toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // chunk size
        buffer.putShort(1) // PCM format
        buffer.putShort(1) // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize.toInt())

        return buffer.array()
    }

    private fun updateWavHeader(file: File, dataSizeFrames: Long) {
        try {
            val raf = RandomAccessFile(file, "rw")
            raf.seek(4)
            raf.write(intToByteArrayLE((dataSizeFrames * 2 + 36).toInt()))
            raf.seek(40)
            raf.write(intToByteArrayLE((dataSizeFrames * 2).toInt()))
            raf.close()
        } catch (_: Exception) {
            // best-effort header update
        }
    }

    private fun intToByteArrayLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    companion object {
        private const val TAG = "WavAudioRecorder"
    }

    private fun buildAudioRecord(source: Int): AudioRecord {
        val rec = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("AudioRecord state=${rec.state} for source=$source")
        }
        return rec
    }
}
