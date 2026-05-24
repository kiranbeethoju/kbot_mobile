package com.offlinebot.data.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Converters {
    @TypeConverter
    fun stringsToDb(value: List<String>): String = value.joinToString(separator = "\u001F")

    @TypeConverter
    fun stringsFromDb(value: String): List<String> =
        value.takeIf { it.isNotBlank() }?.split("\u001F") ?: emptyList()

    @TypeConverter
    fun floatsToDb(value: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(value.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        value.forEach(buffer::putFloat)
        return buffer.array()
    }

    @TypeConverter
    fun floatsFromDb(value: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(value.size / Float.SIZE_BYTES) { buffer.float }
    }
}
