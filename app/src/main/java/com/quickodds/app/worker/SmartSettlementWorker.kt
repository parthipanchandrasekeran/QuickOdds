package com.quickodds.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
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
 * 5. If match is not yet completed, reschedules itself for 30 minutes later
 */
@HiltWorker
class SmartSettlementWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val betDao: VirtualBetDao,
    private val walletDao: UserWalletDao,
    private val oddsService: OddsCloudFunctionService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "SmartSettlementWorker"

        // Input data keys
        const val KEY_BET_ID = "bet_id"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_SPORT_KEY = "sport_key"

        // Retry delay in minutes
        private const val RETRY_DELAY_MINUTES = 30L

        /**
         * Create input data for the worker.
         */
        fun createInputData(betId: Long, eventId: String, sportKey: String): Data {
            return Data.Builder()
                .putLong(KEY_BET_ID, betId)
                .putString(KEY_EVENT_ID, eventId)
                .putString(KEY_SPORT_KEY, sportKey)
                .build()
        }

        /**
         * Schedule settlement check for a specific bet.
         * Schedules to run after the match commence time plus a buffer.
         */
        fun scheduleSettlement(
            context: Context,
            betId: Long,
            eventId: String,
            sportKey: String,
            commenceTime: Long
        ) {
            val currentTime = System.currentTimeMillis()
            // Add 2 hours buffer after match start for completion
            val estimatedEndTime = commenceTime + TimeUnit.HOURS.toMillis(2)
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

        if (betId == -1L) {
            Log.e(TAG, "Invalid bet ID")
            return Result.failure()
        }

        Log.d(TAG, "Starting settlement check for bet $betId, event $eventId")

        return try {
            // Get the bet from database
            val bet = betDao.getBetById(betId)
            if (bet == null) {
                Log.e(TAG, "Bet $betId not found")
                return Result.failure()
            }

            // Check if already settled
            if (bet.status != BetStatus.PENDING) {
                Log.d(TAG, "Bet $betId already settled: ${bet.status}")
                return Result.success()
            }

            // Fetch scores from API
            val response = oddsService.getScores(
                sportKey = sportKey,
                eventIds = eventId,
                daysFrom = 3
            )

            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code()}")
                return reschedule(betId, eventId, sportKey)
            }

            val scores = response.body()
            if (scores.isNullOrEmpty()) {
                Log.d(TAG, "No score data found for event $eventId, rescheduling...")
                return reschedule(betId, eventId, sportKey)
            }

            val matchScore = scores.find { it.id == eventId }
            if (matchScore == null) {
                Log.d(TAG, "Event $eventId not found in scores, rescheduling...")
                return reschedule(betId, eventId, sportKey)
            }

            // ═══════════════════════════════════════════════════════════════════════
            // CRITICAL: Settlement based on ACTUAL scores only - never AI predictions
            // ═══════════════════════════════════════════════════════════════════════

            // Get home and away scores from API
            val matchScores = matchScore.scores
            val homeScore = matchScores?.find { it.name == matchScore.home_team }?.score?.toIntOrNull()
            val awayScore = matchScores?.find { it.name == matchScore.away_team }?.score?.toIntOrNull()

            // Use validator to ensure settlement is safe
            val settlementValidation = BettingFlowValidator.validateSettlementData(
                matchCompleted = matchScore.completed,
                homeScore = homeScore,
                awayScore = awayScore,
                userSelection = bet.selectedTeam
            )

            when (settlementValidation) {
                is SettlementValidation.NotReady -> {
                    Log.d(TAG, "Settlement not ready: ${settlementValidation.reason}")
                    return reschedule(betId, eventId, sportKey)
                }
                is SettlementValidation.Invalid -> {
                    Log.e(TAG, "Invalid settlement data: ${settlementValidation.reason}")
                    return reschedule(betId, eventId, sportKey)
                }
                is SettlementValidation.Ready -> {
                    // SAFE: Settlement based on ACTUAL match results
                    Log.d(TAG, "Final score: ${matchScore.home_team} ${settlementValidation.finalScore} ${matchScore.away_team}")
                    Log.d(TAG, "Actual winner: ${settlementValidation.actualWinner}, User bet on: ${bet.selectedTeam}")

                    if (settlementValidation.userWon) {
                        // User won - update bet status and add winnings to wallet
                        betDao.updateBetStatus(betId, BetStatus.WON)
                        val winnings = bet.potentialPayout
                        walletDao.addFunds(winnings)
                        Log.d(TAG, "Bet $betId WON! Added $winnings to wallet (based on actual score: ${settlementValidation.finalScore})")
                    } else {
                        // User lost - just update bet status (stake already deducted)
                        betDao.updateBetStatus(betId, BetStatus.LOST)
                        Log.d(TAG, "Bet $betId LOST (actual winner: ${settlementValidation.actualWinner})")
                    }
                }
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during settlement: ${e.message}", e)
            // Retry with exponential backoff
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                reschedule(betId, eventId, sportKey)
            }
        }
    }

    /**
     * Reschedule the settlement check for later.
     */
    private fun reschedule(betId: Long, eventId: String, sportKey: String): Result {
        val inputData = createInputData(betId, eventId, sportKey)

        val workRequest = OneTimeWorkRequestBuilder<SmartSettlementWorker>()
            .setInputData(inputData)
            .setInitialDelay(RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
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

        Log.d(TAG, "Rescheduled settlement for bet $betId in $RETRY_DELAY_MINUTES minutes")

        return Result.success() // Return success since we've rescheduled
    }
}
