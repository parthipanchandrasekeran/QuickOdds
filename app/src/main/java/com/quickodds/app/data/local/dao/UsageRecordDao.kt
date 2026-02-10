package com.quickodds.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quickodds.app.data.local.entity.UsageRecord

@Dao
interface UsageRecordDao {

    @Insert
    suspend fun insertUsage(record: UsageRecord)

    @Query("SELECT COUNT(*) FROM usage_records WHERE actionType = :actionType AND dateString = :dateString")
    suspend fun getUsageCount(actionType: String, dateString: String): Int

    @Query("DELETE FROM usage_records WHERE dateString < :cutoffDate")
    suspend fun deleteOldRecords(cutoffDate: String)
}
