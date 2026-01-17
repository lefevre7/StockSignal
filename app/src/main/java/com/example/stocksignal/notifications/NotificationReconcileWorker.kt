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

@HiltWorker
class NotificationReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val notificationQueueProcessor: NotificationQueueProcessor,
    private val notificationScheduler: NotificationScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Reconciling notification state and ensuring workers are scheduled")
        val settings = settingsRepository.settingsFlow.first()
        notificationQueueProcessor.reconcileState(settings)
        
        // Also ensure notification window workers are scheduled
        // This is important after boot or if the app was killed
        notificationScheduler.schedule(settings)
        Log.d(TAG, "Reconcile complete and workers scheduled")
        return Result.success()
    }
    
    companion object {
        private const val TAG = "NotificationReconcileWorker"
    }
}
