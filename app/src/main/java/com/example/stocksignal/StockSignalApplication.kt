package com.example.stocksignal

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.notifications.NotificationBootstrapWorker
import com.example.stocksignal.notifications.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for StockSignal app.
 * Initializes Hilt dependency injection framework and ensures notification
 * workers are scheduled even if the app is killed.
 */
@HiltAndroidApp
class StockSignalApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var notificationScheduler: NotificationScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Hilt ready")

        // Schedule a one-time bootstrap to ensure workers are set up
        // This provides a fallback in case the app is killed before settings flow emits
        val bootstrapRequest = OneTimeWorkRequestBuilder<NotificationBootstrapWorker>()
            .addTag("app_init_bootstrap")
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "notification_bootstrap_init",
            ExistingWorkPolicy.KEEP, // Don't replace if already scheduled
            bootstrapRequest
        )
        Log.d(TAG, "Bootstrap worker enqueued for initial setup")

        // Continue to listen for settings changes and reschedule as needed
        appScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                Log.d(TAG, "Settings changed - rescheduling notification workers")
                notificationScheduler.schedule(settings)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "StockSignalApplication"
    }
}
