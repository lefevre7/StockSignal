package com.example.stocksignal.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PremarketQuoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: PremarketQuoteRunner
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val windowId = inputData.getString(KEY_WINDOW_ID) ?: return Result.failure()
        val sampleIndex = inputData.getInt(KEY_SAMPLE_INDEX, -1)
        return when (runner.run(windowId, sampleIndex)) {
            PremarketQuoteRunner.RunOutcome.SUCCESS -> Result.success()
            PremarketQuoteRunner.RunOutcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_WINDOW_ID = "premarket_window_id"
        const val KEY_SAMPLE_INDEX = "premarket_sample_index"
        const val WORK_TAG = "premarket_quote"
    }
}
