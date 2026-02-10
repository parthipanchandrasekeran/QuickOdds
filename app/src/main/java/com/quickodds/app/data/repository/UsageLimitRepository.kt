package com.quickodds.app.data.repository

import com.quickodds.app.AppConfig
import com.quickodds.app.billing.BillingRepository
import com.quickodds.app.data.local.dao.UsageRecordDao
import com.quickodds.app.data.local.entity.UsageRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageLimitRepository @Inject constructor(
    private val usageRecordDao: UsageRecordDao,
    private val billingRepository: BillingRepository
) {
    private fun todayString(): String =
        LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    suspend fun canScanAll(): Boolean {
        if (billingRepository.isSubscribed.value) return true
        val count = usageRecordDao.getUsageCount("SCAN_ALL", todayString())
        return count < AppConfig.FREE_SCAN_ALL_LIMIT_PER_DAY
    }

    suspend fun canAnalyze(): Boolean {
        if (billingRepository.isSubscribed.value) return true
        val count = usageRecordDao.getUsageCount("ANALYZE", todayString())
        return count < AppConfig.FREE_ANALYZE_LIMIT_PER_DAY
    }

    suspend fun recordScanAll() {
        usageRecordDao.insertUsage(
            UsageRecord(actionType = "SCAN_ALL", dateString = todayString())
        )
    }

    suspend fun recordAnalyze() {
        usageRecordDao.insertUsage(
            UsageRecord(actionType = "ANALYZE", dateString = todayString())
        )
    }

    suspend fun getRemainingScans(): Int {
        if (billingRepository.isSubscribed.value) return Int.MAX_VALUE
        val used = usageRecordDao.getUsageCount("SCAN_ALL", todayString())
        return (AppConfig.FREE_SCAN_ALL_LIMIT_PER_DAY - used).coerceAtLeast(0)
    }

    suspend fun getRemainingAnalyzes(): Int {
        if (billingRepository.isSubscribed.value) return Int.MAX_VALUE
        val used = usageRecordDao.getUsageCount("ANALYZE", todayString())
        return (AppConfig.FREE_ANALYZE_LIMIT_PER_DAY - used).coerceAtLeast(0)
    }
}
