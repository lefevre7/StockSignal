package com.example.stocksignal.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RobotsTxtCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: RobotsTxtCheckRunner
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (runner.run()) {
            RobotsTxtCheckRunner.RunOutcome.SUCCESS -> Result.success()
            RobotsTxtCheckRunner.RunOutcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "robots_txt_check"
    }
}
