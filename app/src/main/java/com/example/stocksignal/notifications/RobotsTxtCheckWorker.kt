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
class RobotsTxtCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: RobotsTxtCheckRunner,
    private val settingsRepository: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val diagnosticsRepository: NotificationDiagnosticsRepository,
    private val backgroundGate: BackgroundStooqExecutionGate
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return backgroundGate.withPermit {
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "robots_txt",
                phase = "start",
                key = WORK_NAME
            )
            val runOutcome = runner.run()
            var scheduleFailure: String? = null
            runCatching {
                val settings = settingsRepository.settingsFlow.first()
                scheduler.scheduleRobotsTxtCheck(settings)
            }.onFailure { error ->
                scheduleFailure = "${error::class.java.simpleName}:${error.message}"
                Log.e(TAG, "Failed to schedule next robots.txt check", error)
            }
            when (runOutcome) {
                RobotsTxtCheckRunner.RunOutcome.SUCCESS -> Result.success()
                RobotsTxtCheckRunner.RunOutcome.FAILURE -> Result.failure()
            }.also { result ->
                val resultLabel = when {
                    result == Result.success() -> "success"
                    result == Result.failure() -> "failure"
                    result == Result.retry() -> "retry"
                    else -> "unknown"
                }
                diagnosticsRepository.recordBackgroundWorkerEvent(
                    worker = "robots_txt",
                    phase = "finish",
                    key = WORK_NAME,
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
    }

    companion object {
        private const val TAG = "RobotsTxtCheckWorker"
        const val WORK_NAME = "robots_txt_check"
    }
}
