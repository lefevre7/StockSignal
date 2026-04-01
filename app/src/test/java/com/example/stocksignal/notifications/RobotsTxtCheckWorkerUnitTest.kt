package com.example.stocksignal.notifications

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.domain.model.ChartRange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek

@RunWith(RobolectricTestRunner::class)
class RobotsTxtCheckWorkerUnitTest {

    private val runner = mockk<RobotsTxtCheckRunner>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val diagnosticsRepository = mockk<NotificationDiagnosticsRepository>(relaxed = true)
    private val backgroundGate = BackgroundStooqExecutionGate()

    @Test
    fun `returns success and schedules next check when runner succeeds`() = runTest {
        val settings = defaultSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)
        coEvery { runner.run() } returns RobotsTxtCheckRunner.RunOutcome.SUCCESS

        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { runner.run() }
        coVerify(exactly = 1) { scheduler.scheduleRobotsTxtCheck(settings) }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("robots_txt", "start", RobotsTxtCheckWorker.WORK_NAME)
        }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("robots_txt", "finish", RobotsTxtCheckWorker.WORK_NAME, match { it.contains("result=success") })
        }
    }

    @Test
    fun `returns failure and still schedules next check when runner fails`() = runTest {
        val settings = defaultSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)
        coEvery { runner.run() } returns RobotsTxtCheckRunner.RunOutcome.FAILURE

        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 1) { runner.run() }
        coVerify(exactly = 1) { scheduler.scheduleRobotsTxtCheck(settings) }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("robots_txt", "start", RobotsTxtCheckWorker.WORK_NAME)
        }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("robots_txt", "finish", RobotsTxtCheckWorker.WORK_NAME, match { it.contains("result=failure") })
        }
    }

    private fun buildWorker(): RobotsTxtCheckWorker {
        val context = RuntimeEnvironment.getApplication()
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                params: WorkerParameters
            ): ListenableWorker {
                return RobotsTxtCheckWorker(
                    appContext,
                    params,
                    runner,
                    settingsRepository,
                    scheduler,
                    diagnosticsRepository,
                    backgroundGate
                )
            }
        }
        return TestListenableWorkerBuilder<RobotsTxtCheckWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    private fun defaultSettings(): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(NotificationType.WATCHLIST),
            quietHours = QuietHours(enabled = false, start = "22:00", end = "07:00"),
            scheduleWindows = emptyList<ScheduleWindow>(),
            weeklyDay = DayOfWeek.MONDAY,
            snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.ONE_DAY,
            immediatePostsEnabled = false,
            offlineTranslationEnabled = false,
            onboardingCompleted = true,
            holdingPeriod = HoldingPeriod.DAYS
        )
    }
}
