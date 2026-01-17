package com.example.stocksignal.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.domain.model.ChartRange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

/**
 * Tests for NotificationBootstrapWorker to ensure it properly:
 * - Reads settings from repository
 * - Calls reconcileState on the queue processor
 * - Calls schedule on the notification scheduler
 * - Returns success on completion
 */
@RunWith(AndroidJUnit4::class)
class NotificationBootstrapWorkerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var notificationQueueProcessor: NotificationQueueProcessor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = mockk(relaxed = true)
        notificationScheduler = mockk(relaxed = true)
        notificationQueueProcessor = mockk(relaxed = true)
    }

    @Test
    fun bootstrapWorkerReadsSettingsAndSchedulesNotificationWindows() = runBlocking {
        val settings = createTestSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { settingsRepository.settingsFlow }
        coVerify(exactly = 1) { notificationQueueProcessor.reconcileState(settings) }
        coVerify(exactly = 1) { notificationScheduler.schedule(settings) }
    }

    @Test
    fun bootstrapWorkerReconcilesStateBeforeScheduling() = runBlocking {
        val settings = createTestSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val callOrder = mutableListOf<String>()
        coEvery { notificationQueueProcessor.reconcileState(any()) } answers {
            callOrder.add("reconcile")
        }
        coEvery { notificationScheduler.schedule(any()) } answers {
            callOrder.add("schedule")
        }

        val worker = buildWorker()
        worker.doWork()

        assertEquals(listOf("reconcile", "schedule"), callOrder)
    }

    @Test
    fun bootstrapWorkerHandlesExceptionAndRetries() = runBlocking {
        coEvery { settingsRepository.settingsFlow } throws Exception("Settings fetch failed")

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun bootstrapWorkerSucceedsWithThreePerDayFrequency() = runBlocking {
        val settings = createTestSettings(frequency = NotificationFrequency.THREE_PER_DAY)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.THREE_PER_DAY }) }
    }

    @Test
    fun bootstrapWorkerSucceedsWithOnePerDayFrequency() = runBlocking {
        val settings = createTestSettings(frequency = NotificationFrequency.ONE_PER_DAY)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.ONE_PER_DAY }) }
    }

    @Test
    fun bootstrapWorkerSucceedsWithOnePerWeekFrequency() = runBlocking {
        val settings = createTestSettings(frequency = NotificationFrequency.ONE_PER_WEEK)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.ONE_PER_WEEK }) }
    }

    @Test
    fun bootstrapWorkerHandlesOnlyWhenOpenFrequency() = runBlocking {
        val settings = createTestSettings(frequency = NotificationFrequency.ONLY_WHEN_OPEN)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        // Worker should still succeed even though scheduler won't schedule background work
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.ONLY_WHEN_OPEN }) }
    }

    @Test
    fun bootstrapWorkerPassesNotificationTypesToScheduler() = runBlocking {
        val types = setOf(NotificationType.WATCHLIST, NotificationType.MARKET_MOVERS)
        val settings = createTestSettings(notificationTypes = types)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        worker.doWork()

        coVerify { notificationScheduler.schedule(match { it.notificationTypes == types }) }
    }

    @Test
    fun bootstrapWorkerHandlesEmptyNotificationTypes() = runBlocking {
        val settings = createTestSettings(notificationTypes = emptySet())
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        // Worker should succeed even though no notifications are enabled
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { notificationScheduler.schedule(match { it.notificationTypes.isEmpty() }) }
    }

    private fun buildWorker(): NotificationBootstrapWorker {
        return TestListenableWorkerBuilder<NotificationBootstrapWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker {
                    return NotificationBootstrapWorker(
                        appContext,
                        workerParameters,
                        settingsRepository,
                        notificationScheduler,
                        notificationQueueProcessor
                    )
                }
            })
            .build()
    }

    private fun createTestSettings(
        frequency: NotificationFrequency = NotificationFrequency.THREE_PER_DAY,
        notificationTypes: Set<NotificationType> = setOf(
            NotificationType.WATCHLIST,
            NotificationType.MARKET_MOVERS
        )
    ): AppSettings {
        return AppSettings(
            frequency = frequency,
            notificationTypes = notificationTypes,
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
            scheduleWindows = listOf(
                ScheduleWindow(
                    id = "market_open_minus_10",
                    type = ScheduleWindowType.MARKET_OPEN_MINUS,
                    hour = null,
                    minute = null,
                    zoneId = "America/New_York",
                    offsetMinutes = -10
                ),
                ScheduleWindow(
                    id = "local_1100",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = 11,
                    minute = 0,
                    zoneId = null,
                    offsetMinutes = null
                ),
                ScheduleWindow(
                    id = "local_1400",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = 14,
                    minute = 0,
                    zoneId = null,
                    offsetMinutes = null
                )
            ),
            weeklyDay = DayOfWeek.MONDAY,
            snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.SIX_MONTH,
            holdingPeriod = HoldingPeriod.MONTHS,
            immediatePostsEnabled = false,
            onboardingCompleted = true
        )
    }
}
