package com.example.stocksignal

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.stocksignal.data.settings.SettingsRepository
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
 * alarms are scheduled even if the app is killed.
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

        // Continue to listen for settings changes and reschedule as needed
        appScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                Log.d(TAG, "Settings changed - rescheduling notification alarms")
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
