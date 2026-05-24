package com.offlinebot.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import com.offlinebot.data.database.entities.BucketEntity

@Dao
interface BucketDao {
    @Insert
    suspend fun insert(bucket: BucketEntity): Long
}
