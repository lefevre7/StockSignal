package com.example.stocksignal.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stocksignal.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Bootstrap worker that runs on boot or app initialization to ensure
 * notification window workers are properly scheduled even if the app
 * process was killed and never restarted.
 */
@HiltWorker
class NotificationBootstrapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val notificationQueueProcessor: NotificationQueueProcessor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Bootstrap worker starting - will schedule notification windows")
            val settings = settingsRepository.settingsFlow.first()
            
            // Reconcile any stale notification state
            notificationQueueProcessor.reconcileState(settings)
            
            // Schedule all notification window workers based on current settings
            notificationScheduler.schedule(settings)
            
            Log.d(TAG, "Bootstrap complete - workers scheduled for frequency: ${settings.frequency}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap worker failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotificationBootstrapWorker"
    }
}
