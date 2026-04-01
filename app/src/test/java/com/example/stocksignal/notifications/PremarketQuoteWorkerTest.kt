package com.example.stocksignal.notifications

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
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
class PremarketQuoteWorkerTest {

    private val runner = mockk<PremarketQuoteRunner>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val scheduler = mockk<NotificationScheduler>(relaxed = true)
    private val diagnosticsRepository = mockk<NotificationDiagnosticsRepository>(relaxed = true)
    private val backgroundGate = BackgroundStooqExecutionGate()

    @Test
    fun `returns success and schedules next sample when runner succeeds`() = runTest {
        val settings = defaultSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)
        coEvery { runner.run("market_open_minus_10", 2) } returns PremarketQuoteRunner.RunOutcome.SUCCESS

        val worker = buildWorker(windowId = "market_open_minus_10", sampleIndex = 2)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { runner.run("market_open_minus_10", 2) }
        coVerify(exactly = 1) { scheduler.schedulePremarketSample(settings, "market_open_minus_10", 2) }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("premarket_quote", "start", "market_open_minus_10:2")
        }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("premarket_quote", "finish", "market_open_minus_10:2", match { it.contains("result=success") })
        }
    }

    @Test
    fun `returns failure and still schedules next sample when runner fails`() = runTest {
        val settings = defaultSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)
        coEvery { runner.run("market_open_minus_10", 3) } returns PremarketQuoteRunner.RunOutcome.FAILURE

        val worker = buildWorker(windowId = "market_open_minus_10", sampleIndex = 3)
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 1) { runner.run("market_open_minus_10", 3) }
        coVerify(exactly = 1) { scheduler.schedulePremarketSample(settings, "market_open_minus_10", 3) }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("premarket_quote", "start", "market_open_minus_10:3")
        }
        coVerify(exactly = 1) {
            diagnosticsRepository.recordBackgroundWorkerEvent("premarket_quote", "finish", "market_open_minus_10:3", match { it.contains("result=failure") })
        }
    }

    private fun buildWorker(windowId: String, sampleIndex: Int): PremarketQuoteWorker {
        val context = RuntimeEnvironment.getApplication()
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                params: WorkerParameters
            ): ListenableWorker {
                return PremarketQuoteWorker(
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
        return TestListenableWorkerBuilder<PremarketQuoteWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(
                workDataOf(
                    PremarketQuoteWorker.KEY_WINDOW_ID to windowId,
                    PremarketQuoteWorker.KEY_SAMPLE_INDEX to sampleIndex
                )
            )
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
