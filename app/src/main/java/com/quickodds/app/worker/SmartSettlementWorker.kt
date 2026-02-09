package com.quickodds.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.quickodds.app.AppConfig
import com.quickodds.app.data.local.dao.PredictionRecordDao
import com.quickodds.app.data.local.dao.UserWalletDao
import com.quickodds.app.data.local.dao.VirtualBetDao
import com.quickodds.app.data.local.entity.BetStatus
import com.quickodds.app.data.remote.api.OddsCloudFunctionService
import com.quickodds.app.util.BettingFlowValidator
import com.quickodds.app.util.SettlementValidation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Smart Settlement Worker that checks match results and settles bets.
 *
 * This worker:
 * 1. Accepts betId, eventId, and sportKey as input
 * 2. Calls the /scores endpoint to get match results
 * 3. If match is completed, compares scores to user's selection
 * 4. Updates VirtualBet status (WON/LOST) and UserWallet balance
 * 5. If match is not yet completed, reschedules itself (up to MAX_SETTLEMENT_RESCHEDULES)
 * 6. After max reschedules, voids the bet and refunds the stake
 */
@HiltWorker
class SmartSettlementWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val betDao: VirtualBetDao,
    private val walletDao: UserWalletDao,
    private val oddsService: OddsCloudFunctionService,
    private val predictionRecordDao: PredictionRecordDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SmartSettlementWorker"

        const val KEY_BET_ID = "bet_id"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_SPORT_KEY = "sport_key"
        const val KEY_RESCHEDULE_COUNT = "reschedule_count"

        fun createInputData(betId: Long, eventId: String, sportKey: String, rescheduleCount: Int = 0): Data {
            return Data.Builder()
                .putLong(KEY_BET_ID, betId)
                .putString(KEY_EVENT_ID, eventId)
                .putString(KEY_SPORT_KEY, sportKey)
                .putInt(KEY_RESCHEDULE_COUNT, rescheduleCount)
                .build()
        }

        fun scheduleSettlement(
            context: Context,
            betId: Long,
            eventId: String,
            sportKey: String,
            commenceTime: Long
        ) {
            val currentTime = System.currentTimeMillis()
            val estimatedEndTime = commenceTime + TimeUnit.HOURS.toMillis(AppConfig.SETTLEMENT_DELAY_HOURS)
            val initialDelay = maxOf(0, estimatedEndTime - currentTime)

            val inputData = createInputData(betId, eventId, sportKey)

            val workRequest = OneTimeWorkRequestBuilder<SmartSettlementWorker>()
                .setInputData(inputData)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("settlement_$betId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "settlement_$betId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            Log.d(TAG, "Scheduled settlement for bet $betId in ${initialDelay / 1000 / 60} minutes")
        }
    }

    override suspend fun doWork(): Result {
        val betId = inputData.getLong(KEY_BET_ID, -1)
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val sportKey = inputData.getString(KEY_SPORT_KEY) ?: return Result.failure()
        val rescheduleCount = inputData.getInt(KEY_RESCHEDULE_COUNT, 0)

        if (betId == -1L) {
            Log.e(TAG, "Invalid bet ID")
            return Result.failure()
        }

        Log.d(TAG, "Starting settlement check for bet $betId, event $eventId (attempt $rescheduleCount)")

        return try {
            val bet = betDao.getBetById(betId)
            if (bet == null) {
                Log.e(TAG, "Bet $betId not found")
                return Result.failure()
            }

            if (bet.status != BetStatus.PENDING) {
                Log.d(TAG, "Bet $betId already settled: ${bet.status}")
                return Result.success()
            }

            val response = oddsService.getScores(
                sportKey = sportKey,
                eventIds = eventId,
                daysFrom = 3
            )

            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code()}")
                return rescheduleOrVoid(betId, eventId, sportKey, rescheduleCount)
            }

            val scores = response.body()
            if (scores.isNullOrEmpty()) {
                Log.d(TAG, "No score data found for event $eventId, rescheduling...")
                return rescheduleOrVoid(betId, eventId, sportKey, rescheduleCount)
            }

            val matchScore = scores.find { it.id == eventId }
            if (matchScore == null) {
                Log.d(TAG, "Event $eventId not found in scores, rescheduling...")
                return rescheduleOrVoid(betId, eventId, sportKey, rescheduleCount)
            }

            // CRITICAL: Settlement based on ACTUAL scores only - never AI predictions
            val matchScores = matchScore.scores
            val homeScore = matchScores?.find { it.name == matchScore.home_team }?.score?.toIntOrNull()
            val awayScore = matchScores?.find { it.name == matchScore.away_team }?.score?.toIntOrNull()

            val settlementValidation = BettingFlowValidator.validateSettlementData(
                matchCompleted = matchScore.completed,
                homeScore = homeScore,
                awayScore = awayScore,
                userSelection = bet.selectedTeam
            )

            when (settlementValidation) {
                is SettlementValidation.NotReady -> {
                    Log.d(TAG, "Settlement not ready: ${settlementValidation.reason}")
                    return rescheduleOrVoid(betId, eventId, sportKey, rescheduleCount)
                }
                is SettlementValidation.Invalid -> {
                    Log.e(TAG, "Invalid settlement data: ${settlementValidation.reason}")
                    return rescheduleOrVoid(betId, eventId, sportKey, rescheduleCount)
                }
                is SettlementValidation.Ready -> {
                    Log.d(TAG, "Final score: ${settlementValidation.finalScore}, Winner: ${settlementValidation.actualWinner}")

                    val outcome: String
                    if (settlementValidation.userWon) {
                        betDao.updateBetStatus(betId, BetStatus.WON)
                        val winnings = bet.potentialPayout
                        walletDao.addFunds(winnings)
                        outcome = "WON"
                        Log.d(TAG, "Bet $betId WON! Added $winnings to wallet")
                    } else {
                        betDao.updateBetStatus(betId, BetStatus.LOST)
                        outcome = "LOST"
                        Log.d(TAG, "Bet $betId LOST")
                    }

                    // Record prediction outcome (non-fatal)
                    try {
                        predictionRecordDao.recordOutcome(betId, outcome)
                        Log.d(TAG, "Recorded prediction outcome $outcome for bet $betId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to record prediction outcome (non-fatal): ${e.message}")
                    }
                }
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during settlement: ${e.message}", e)
            if (runAttemptCount < AppConfig.MAX_SETTLEMENT_RETRIES) {
                Result.retry()
            } else {
                rescheduleOrVoid(betId, eventId, sportKey, rescheduleCount)
            }
        }
    }

    /**
     * Reschedule if under the limit, otherwise void the bet and refund stake.
     */
    private suspend fun rescheduleOrVoid(
        betId: Long,
        eventId: String,
        sportKey: String,
        rescheduleCount: Int
    ): Result {
        if (rescheduleCount >= AppConfig.MAX_SETTLEMENT_RESCHEDULES) {
            Log.w(TAG, "Max reschedules ($rescheduleCount) reached for bet $betId. Voiding bet.")
            betDao.updateBetStatus(betId, BetStatus.VOID)
            val bet = betDao.getBetById(betId)
            if (bet != null) {
                walletDao.addFunds(bet.stakeAmount)
                Log.d(TAG, "Refunded stake ${bet.stakeAmount} for voided bet $betId")
            }
            return Result.success()
        }

        val inputData = createInputData(betId, eventId, sportKey, rescheduleCount + 1)

        val workRequest = OneTimeWorkRequestBuilder<SmartSettlementWorker>()
            .setInputData(inputData)
            .setInitialDelay(AppConfig.SETTLEMENT_RETRY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("settlement_$betId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "settlement_$betId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        Log.d(TAG, "Rescheduled bet $betId (attempt ${rescheduleCount + 1}/${AppConfig.MAX_SETTLEMENT_RESCHEDULES})")
        return Result.success()
    }
}
