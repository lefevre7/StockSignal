package com.example.stocksignal.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NotificationWindowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: NotificationWindowRunner
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val windowId = inputData.getString(KEY_WINDOW_ID) ?: return Result.failure()
        return when (runner.run(windowId, runAttemptCount, allowAiGeneration = false)) {
            NotificationWindowRunner.RunOutcome.SUCCESS -> Result.success()
            NotificationWindowRunner.RunOutcome.RETRY -> Result.retry()
            NotificationWindowRunner.RunOutcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_WINDOW_ID = "window_id"
    }
}
