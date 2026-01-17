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
 * Tests for enhanced NotificationReconcileWorker to ensure it:
 * - Reconciles notification state
 * - Schedules notification windows
 * - Schedules after reconciliation (correct order)
 */
@RunWith(AndroidJUnit4::class)
class NotificationReconcileWorkerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationQueueProcessor: NotificationQueueProcessor
    private lateinit var notificationScheduler: NotificationScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = mockk(relaxed = true)
        notificationQueueProcessor = mockk(relaxed = true)
        notificationScheduler = mockk(relaxed = true)
    }

    @Test
    fun reconcileWorkerCallsBothReconcileStateAndSchedule() = runBlocking {
        val settings = createTestSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { notificationQueueProcessor.reconcileState(settings) }
        coVerify(exactly = 1) { notificationScheduler.schedule(settings) }
    }

    @Test
    fun reconcileWorkerReconcilesBeforeScheduling() = runBlocking {
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
    fun reconcileWorkerReturnsSuccessAfterBothOperations() = runBlocking {
        val settings = createTestSettings()
        coEvery { settingsRepository.settingsFlow } returns flowOf(settings)

        val worker = buildWorker()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private fun buildWorker(): NotificationReconcileWorker {
        return TestListenableWorkerBuilder<NotificationReconcileWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): ListenableWorker {
                    return NotificationReconcileWorker(
                        appContext,
                        workerParameters,
                        settingsRepository,
                        notificationQueueProcessor,
                        notificationScheduler
                    )
                }
            })
            .build()
    }

    private fun createTestSettings(): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(NotificationType.WATCHLIST),
            quietHours = QuietHours(enabled = false, start = "22:00", end = "07:00"),
            scheduleWindows = listOf(
                ScheduleWindow(
                    id = "local_1100",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = 11,
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
