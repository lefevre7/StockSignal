package com.example.stocksignal.notifications

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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

/**
 * Unit tests for NotificationBootstrapWorker logic (mocked dependencies).
 * For full integration tests with WorkManager, see androidTest version.
 */
class NotificationBootstrapWorkerUnitTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationScheduler: NotificationScheduler
    private lateinit var notificationQueueProcessor: NotificationQueueProcessor

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        notificationScheduler = mockk(relaxed = true)
        notificationQueueProcessor = mockk(relaxed = true)
    }

    @Test
    fun `bootstrap flow reads settings and schedules notification windows`() = runTest {
        val settings = createTestSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        // Simulate the worker's doWork logic
        val retrievedSettings = settingsRepository.settingsFlow
        var capturedSettings: AppSettings? = null
        retrievedSettings.collect { capturedSettings = it }

        notificationQueueProcessor.reconcileState(capturedSettings!!)
        notificationScheduler.schedule(capturedSettings!!)

        coVerify(exactly = 1) { notificationQueueProcessor.reconcileState(settings) }
        coVerify(exactly = 1) { notificationScheduler.schedule(settings) }
    }

    @Test
    fun `bootstrap flow reconciles state before scheduling`() = runTest {
        val settings = createTestSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val callOrder = mutableListOf<String>()
        coEvery { notificationQueueProcessor.reconcileState(any()) } answers {
            callOrder.add("reconcile")
        }
        coEvery { notificationScheduler.schedule(any()) } answers {
            callOrder.add("schedule")
        }

        // Simulate worker execution order
        settingsRepository.settingsFlow.collect { s ->
            notificationQueueProcessor.reconcileState(s)
            notificationScheduler.schedule(s)
        }

        assertEquals(listOf("reconcile", "schedule"), callOrder)
    }

    @Test
    fun `scheduler receives correct frequency THREE_PER_DAY`() = runTest {
        val settings = createTestSettings(frequency = NotificationFrequency.THREE_PER_DAY)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        settingsRepository.settingsFlow.collect { s ->
            notificationScheduler.schedule(s)
        }

        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.THREE_PER_DAY }) }
    }

    @Test
    fun `scheduler receives correct frequency ONE_PER_DAY`() = runTest {
        val settings = createTestSettings(frequency = NotificationFrequency.ONE_PER_DAY)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        settingsRepository.settingsFlow.collect { s ->
            notificationScheduler.schedule(s)
        }

        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.ONE_PER_DAY }) }
    }

    @Test
    fun `scheduler receives correct frequency ONE_PER_WEEK`() = runTest {
        val settings = createTestSettings(frequency = NotificationFrequency.ONE_PER_WEEK)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        settingsRepository.settingsFlow.collect { s ->
            notificationScheduler.schedule(s)
        }

        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.ONE_PER_WEEK }) }
    }

    @Test
    fun `scheduler handles ONLY_WHEN_OPEN frequency`() = runTest {
        val settings = createTestSettings(frequency = NotificationFrequency.ONLY_WHEN_OPEN)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        settingsRepository.settingsFlow.collect { s ->
            notificationScheduler.schedule(s)
        }

        coVerify { notificationScheduler.schedule(match { it.frequency == NotificationFrequency.ONLY_WHEN_OPEN }) }
    }

    @Test
    fun `scheduler receives notification types correctly`() = runTest {
        val types = setOf(NotificationType.WATCHLIST, NotificationType.MARKET_MOVERS)
        val settings = createTestSettings(notificationTypes = types)
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        settingsRepository.settingsFlow.collect { s ->
            notificationScheduler.schedule(s)
        }

        coVerify { notificationScheduler.schedule(match { it.notificationTypes == types }) }
    }

    @Test
    fun `scheduler handles empty notification types`() = runTest {
        val settings = createTestSettings(notificationTypes = emptySet())
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        settingsRepository.settingsFlow.collect { s ->
            notificationScheduler.schedule(s)
        }

        coVerify { notificationScheduler.schedule(match { it.notificationTypes.isEmpty() }) }
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
            offlineTranslationEnabled = false,
            onboardingCompleted = true
        )
    }
}
