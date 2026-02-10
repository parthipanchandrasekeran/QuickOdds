package com.quickodds.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.quickodds.app.billing.BillingRepository
import com.quickodds.app.di.DatabaseInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for Quick Odds.
 *
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection
 * throughout the application.
 *
 * Implements Configuration.Provider to support Hilt-injected WorkManager workers.
 */
@HiltAndroidApp
class QuickOddsApp : Application(), Configuration.Provider {

    // Application-scoped coroutine scope for initialization tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    @Inject
    lateinit var billingRepository: BillingRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Initialize database with default wallet if needed
        applicationScope.launch(Dispatchers.IO) {
            databaseInitializer.initializeWallet()
        }

        // Initialize billing connection
        billingRepository.startConnection()
    }

    /**
     * Provide WorkManager configuration with Hilt worker factory.
     * This allows WorkManager workers to be injected with Hilt dependencies.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
