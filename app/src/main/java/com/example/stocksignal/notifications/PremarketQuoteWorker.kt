package com.example.stocksignal.notifications

import android.util.Log
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stocksignal.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PremarketQuoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: PremarketQuoteRunner,
    private val settingsRepository: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val windowId = inputData.getString(KEY_WINDOW_ID)
        val sampleIndex = inputData.getInt(KEY_SAMPLE_INDEX, -1)
        if (windowId.isNullOrBlank() || sampleIndex < 0) {
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "premarket_quote",
                phase = "invalid_input",
                key = null,
                note = "windowId=$windowId sampleIndex=$sampleIndex"
            )
            return Result.failure()
        }
        val workerKey = NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex)
        diagnosticsRepository.recordBackgroundWorkerEvent(
            worker = "premarket_quote",
            phase = "start",
            key = workerKey
        )
        val runOutcome = runner.run(windowId, sampleIndex)
        var scheduleFailure: String? = null
        runCatching {
            val settings = settingsRepository.settingsFlow.first()
            scheduler.schedulePremarketSample(settings, windowId, sampleIndex)
        }.onFailure { error ->
            scheduleFailure = "${error::class.java.simpleName}:${error.message}"
            Log.e(TAG, "Failed to schedule next premarket sample for $windowId/$sampleIndex", error)
        }
        return when (runOutcome) {
            PremarketQuoteRunner.RunOutcome.SUCCESS -> Result.success()
            PremarketQuoteRunner.RunOutcome.FAILURE -> Result.failure()
        }.also { result ->
            val resultLabel = when (result) {
                is Result.Success -> "success"
                is Result.Failure -> "failure"
                is Result.Retry -> "retry"
                else -> "unknown"
            }
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "premarket_quote",
                phase = "finish",
                key = workerKey,
                note = buildString {
                    append("result=")
                    append(resultLabel)
                    if (!scheduleFailure.isNullOrBlank()) {
                        append(" schedule=")
                        append(scheduleFailure)
                    }
                }
            )
        }
    }

    companion object {
        private const val TAG = "PremarketQuoteWorker"
        const val KEY_WINDOW_ID = "premarket_window_id"
        const val KEY_SAMPLE_INDEX = "premarket_sample_index"
        const val WORK_TAG = "premarket_quote"
    }
}
