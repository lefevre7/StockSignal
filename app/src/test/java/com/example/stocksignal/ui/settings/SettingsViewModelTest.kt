package com.example.stocksignal.ui.settings

import android.util.Log
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
import com.example.stocksignal.data.stooq.network.StooqRequestBlocker
import com.example.stocksignal.data.translation.NewsTranslationService
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import com.example.stocksignal.notifications.NotificationScheduler
import com.example.stocksignal.notifications.NotificationTestSender
import com.example.stocksignal.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = mockk<SettingsRepository>()
    private val notificationTestSender = mockk<NotificationTestSender>()
    private val notificationScheduler = mockk<NotificationScheduler>()
    private val diagnosticsRepository = mockk<NotificationDiagnosticsRepository>()
    private val stooqRequestBlocker = mockk<StooqRequestBlocker>()
    private val translationService = mockk<NewsTranslationService>()

    private lateinit var settingsFlow: MutableStateFlow<AppSettings>

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        settingsFlow = MutableStateFlow(testSettings())

        every { settingsRepository.settingsFlow } returns settingsFlow
        coEvery { settingsRepository.setHoldingPeriod(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(holdingPeriod = firstArg())
        }
        coEvery { settingsRepository.setFrequency(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(frequency = firstArg())
        }
        coEvery { settingsRepository.setNotificationTypes(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(notificationTypes = firstArg())
        }
        coEvery { settingsRepository.setQuietHours(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(quietHours = firstArg())
        }
        coEvery { settingsRepository.setScheduleWindows(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(scheduleWindows = firstArg())
        }
        coEvery { settingsRepository.setWeeklyDay(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(weeklyDay = firstArg())
        }
        coEvery { settingsRepository.setSnoozeDuration(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(snoozeDuration = firstArg())
        }
        coEvery { settingsRepository.setSignalSensitivity(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(signalSensitivity = firstArg())
        }
        coEvery { settingsRepository.setSelectedChartRange(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(selectedChartRange = firstArg())
        }
        coEvery { settingsRepository.setImmediatePostsEnabled(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(immediatePostsEnabled = firstArg())
        }
        coEvery { settingsRepository.setOfflineTranslationEnabled(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(offlineTranslationEnabled = firstArg())
        }

        coEvery { notificationScheduler.schedule(any(), any()) } just runs
        coEvery { notificationTestSender.sendTestNotification() } returns 7

        coEvery { diagnosticsRepository.getStooqBlockedInfo() } returns
            NotificationDiagnosticsRepository.StooqBlockedInfo(null, null, null)
        coEvery { diagnosticsRepository.clearStooqBlocked() } just runs
        coEvery { diagnosticsRepository.getWindowRunInfo(any()) } returns emptyMap()
        coEvery { diagnosticsRepository.getWindowPreNotifyRunInfo(any()) } returns emptyMap()
        coEvery { diagnosticsRepository.getNextWindowRunTimes(any()) } returns emptyMap()
        coEvery { diagnosticsRepository.getWindowPreNotifyNextRuns(any()) } returns emptyMap()
        coEvery { diagnosticsRepository.getRobotsNextRun() } returns null
        coEvery { diagnosticsRepository.getRobotsRunInfo() } returns
            NotificationDiagnosticsRepository.RobotsRunInfo(null, null, null)
        coEvery { diagnosticsRepository.getLastExactAlarmAllowed() } returns null
        coEvery { diagnosticsRepository.getStooqTimeoutStreakInfo() } returns
            NotificationDiagnosticsRepository.StooqTimeoutStreakInfo(0, null)
        coEvery { diagnosticsRepository.getStooqRequestLog() } returns emptyList()
        coEvery { diagnosticsRepository.getBackgroundWorkerLog() } returns emptyList()
        coEvery { diagnosticsRepository.getSerialGateMetricsLog() } returns emptyList()
        coEvery { diagnosticsRepository.getAlarmScheduleErrorInfo() } returns
            NotificationDiagnosticsRepository.AlarmScheduleErrorInfo(null, null)
        coEvery { diagnosticsRepository.getScheduledPremarketKeys() } returns emptySet()
        coEvery { diagnosticsRepository.getPremarketNextRuns(any()) } returns emptyMap()
        coEvery { diagnosticsRepository.getPremarketRunInfo(any()) } returns emptyMap()

        every { stooqRequestBlocker.clearBlock() } just runs

        every { translationService.getLocalModelFilePath() } returns "/tmp/stocksignal-missing-model.litertlm"
        every { translationService.deleteLocalModel() } returns true
        coEvery { translationService.downloadLocalModel(any()) } answers {
            firstArg<(Int) -> Unit>().invoke(100)
            true
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `init force-enables offline translation and loads stooq block message`() = runTest(mainDispatcherRule.dispatcher) {
        settingsFlow.value = testSettings(offlineTranslationEnabled = false)
        val blockedUntil = System.currentTimeMillis() + 60_000
        coEvery { diagnosticsRepository.getStooqBlockedInfo() } returns
            NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = blockedUntil - 10_000,
                blockedUntilMillis = blockedUntil,
                message = "Requests paused until tomorrow at 07:24."
            )

        val viewModel = createViewModel()
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        coVerify { settingsRepository.setOfflineTranslationEnabled(true) }
        assertTrue(viewModel.uiState.value.settings.offlineTranslationEnabled)
        assertEquals(
            "Requests paused until tomorrow at 07:24.",
            viewModel.uiState.value.stooqBlockedMessage
        )
    }

    @Test
    fun `settings mutators update settings flow and toast state`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.setHoldingPeriod(HoldingPeriod.YEARS)
        advanceUntilIdle()
        viewModel.toggleNotificationType(NotificationType.DIGESTS, enabled = false)
        advanceUntilIdle()
        viewModel.setQuietHoursEnabled(true)
        advanceUntilIdle()
        viewModel.setQuietHours("21:00", "06:00")
        advanceUntilIdle()
        viewModel.updateScheduleWindow(settingsFlow.value.scheduleWindows.first().copy(hour = 10))
        advanceUntilIdle()
        viewModel.setSignalSensitivity(
            settingsFlow.value.signalSensitivity.copy(minScoreForNotify = 75)
        )
        advanceUntilIdle()
        viewModel.setWeeklyDay(DayOfWeek.FRIDAY)
        advanceUntilIdle()
        viewModel.setSnoozeDuration(SnoozeDurationOption.FIVE_HOURS)
        advanceUntilIdle()
        viewModel.setImmediatePostsEnabled(true)
        advanceUntilIdle()
        viewModel.setOfflineTranslationEnabled(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value.settings
        assertEquals(HoldingPeriod.YEARS, state.holdingPeriod)
        assertFalse(state.notificationTypes.contains(NotificationType.DIGESTS))
        assertTrue(state.quietHours.enabled)
        assertEquals("21:00", state.quietHours.start)
        assertEquals("06:00", state.quietHours.end)
        assertEquals(10, state.scheduleWindows.first().hour)
        assertEquals(75, state.signalSensitivity.minScoreForNotify)
        assertEquals(DayOfWeek.FRIDAY, state.weeklyDay)
        assertEquals(SnoozeDurationOption.FIVE_HOURS, state.snoozeDuration)
        assertTrue(state.immediatePostsEnabled)
        assertFalse(state.offlineTranslationEnabled)
        assertEquals("Signal sensitivity updated", viewModel.uiState.value.toastMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clear block and notification actions update ui state`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { diagnosticsRepository.getStooqBlockedInfo() } returns
            NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = System.currentTimeMillis() - 5_000,
                blockedUntilMillis = System.currentTimeMillis() + 30_000,
                message = "Blocked"
            )
        coEvery { notificationTestSender.sendTestNotification() } returnsMany listOf(7, 0)
        val viewModel = createViewModel()
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.clearStooqBlock()
        advanceUntilIdle()
        viewModel.sendTestNotification()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.sendTestNotification()
        advanceUntilIdle()

        coVerify { diagnosticsRepository.clearStooqBlocked() }
        assertEquals("Stooq block cleared", viewModel.uiState.value.toastMessage)
        assertNull(viewModel.uiState.value.stooqBlockedMessage)
        assertEquals("Failed to post test notification.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `model helpers and download flows reflect filesystem and failures`() = runTest(mainDispatcherRule.dispatcher) {
        val tempFile = File.createTempFile("stocksignal-model", ".litertlm")
        try {
            every { translationService.getLocalModelFilePath() } returns tempFile.absolutePath
            every { translationService.deleteLocalModel() } returns false
            coEvery { translationService.downloadLocalModel(any()) } answers {
                firstArg<(Int) -> Unit>().invoke(25)
                firstArg<(Int) -> Unit>().invoke(100)
                true
            }
            val viewModel = createViewModel()
            startUiStateCollection(viewModel)
            advanceUntilIdle()

            assertTrue(viewModel.isModelDownloaded())
            assertEquals("Gemma 3 1B int4", viewModel.getModelInfo().first)
            assertTrue(viewModel.getModelInfo().second.endsWith("MB"))

            viewModel.downloadModel()
            advanceUntilIdle()
            assertEquals("Model downloaded successfully", viewModel.uiState.value.toastMessage)
            assertFalse(viewModel.uiState.value.isDownloadingModel)
            assertNull(viewModel.uiState.value.modelDownloadProgress)

            viewModel.deleteOfflineTranslationModel()
            advanceUntilIdle()
            waitUntil { viewModel.uiState.value.errorMessage != null }
            assertEquals(
                "Failed to delete offline translation model.",
                viewModel.uiState.value.errorMessage
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `check worker status formats diagnostics summary`() = runTest(mainDispatcherRule.dispatcher) {
        val now = System.currentTimeMillis()
        val settings = testSettings()
        settingsFlow.value = settings

        coEvery { diagnosticsRepository.getWindowRunInfo(any()) } answers {
            firstArg<Set<String>>().associateWith { windowId ->
                NotificationDiagnosticsRepository.WindowRunInfo(
                    lastRunAtMillis = now - 3_600_000,
                    lastResult = "success",
                    lastReason = "window=$windowId"
                )
            }
        }
        coEvery { diagnosticsRepository.getWindowPreNotifyRunInfo(any()) } answers {
            firstArg<Set<String>>().associateWith { windowId ->
                NotificationDiagnosticsRepository.WindowPreNotifyRunInfo(
                    lastRunAtMillis = now - 1_800_000,
                    lastResult = "started",
                    lastReason = "pre=$windowId"
                )
            }
        }
        coEvery { diagnosticsRepository.getNextWindowRunTimes(any()) } answers {
            firstArg<Set<String>>().associateWith { now + 3_600_000 }
        }
        coEvery { diagnosticsRepository.getWindowPreNotifyNextRuns(any()) } answers {
            firstArg<Set<String>>().associateWith { now + 1_800_000 }
        }
        coEvery { diagnosticsRepository.getRobotsNextRun() } returns now + 7_200_000
        coEvery { diagnosticsRepository.getRobotsRunInfo() } returns
            NotificationDiagnosticsRepository.RobotsRunInfo(
                lastRunAtMillis = now - 600_000,
                lastResult = "success",
                lastReason = "ok"
            )
        coEvery { diagnosticsRepository.getLastExactAlarmAllowed() } returns true
        coEvery { diagnosticsRepository.getStooqBlockedInfo() } returns
            NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = now - 300_000,
                blockedUntilMillis = now + 86_400_000,
                message = "Blocked for 24 hours."
            )
        coEvery { diagnosticsRepository.getStooqTimeoutStreakInfo() } returns
            NotificationDiagnosticsRepository.StooqTimeoutStreakInfo(3, now - 120_000)
        coEvery { diagnosticsRepository.getStooqRequestLog() } returns
            listOf("07:05:32.273 GET /q/ wait=4081ms")
        coEvery { diagnosticsRepository.getBackgroundWorkerLog() } returns
            listOf("07:20:00 robots_txt start key=robots_txt_check")
        coEvery { diagnosticsRepository.getSerialGateMetricsLog() } returns
            listOf("07:05:40.710 stooq_http wait=15356ms hold=3585ms")
        coEvery { diagnosticsRepository.getAlarmScheduleErrorInfo() } returns
            NotificationDiagnosticsRepository.AlarmScheduleErrorInfo(
                lastAtMillis = now - 90_000,
                reason = "reschedule requested"
            )
        coEvery { diagnosticsRepository.getScheduledPremarketKeys() } returns
            setOf("market_open_minus_10:0")
        coEvery { diagnosticsRepository.getPremarketNextRuns(any()) } returns
            mapOf("market_open_minus_10:0" to now + 600_000)
        coEvery { diagnosticsRepository.getPremarketRunInfo(any()) } returns
            mapOf(
                "market_open_minus_10:0" to NotificationDiagnosticsRepository.PremarketRunInfo(
                    lastStartAtMillis = now - 900_000,
                    lastRunAtMillis = now - 840_000,
                    lastResult = "success",
                    lastReason = "quotes ok",
                    lastCandleLabel = "2026-03-31 08:50",
                    lastUpsertedCount = 1,
                    lastQuoteCount = 2,
                    lastErrorCount = 0
                )
            )

        val viewModel = createViewModel()
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.checkWorkerStatus()
        advanceUntilIdle()

        val status = viewModel.uiState.value.errorMessage
        assertNotNull(status)
        assertTrue(status!!.contains("Alarm Schedule Status:"))
        assertTrue(status.contains("Window 1: market_open_minus_10"))
        assertTrue(status.contains("Premarket samples: 1"))
        assertTrue(status.contains("Robots.txt last result: success"))
        assertTrue(status.contains("Stooq blocked: Blocked for 24 hours."))
        assertTrue(status.contains("Stooq request pacing (recent):"))
        assertTrue(status.contains("Background worker queue (recent):"))
        assertTrue(status.contains("Serial gate timing (recent):"))
        assertTrue(status.contains("Exact alarms allowed: true"))
        assertTrue(status.contains("Tap 'Force schedule' to reschedule alarms now"))
    }

    @Test
    fun `frequency and force schedule success and failure paths update messages`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.setFrequency(NotificationFrequency.ONE_PER_DAY)
        advanceUntilIdle()
        assertEquals(NotificationFrequency.ONE_PER_DAY, viewModel.uiState.value.settings.frequency)
        assertTrue(viewModel.uiState.value.toastMessage!!.contains("1x/day"))
        coVerify {
            notificationScheduler.schedule(
                match { it.frequency == NotificationFrequency.ONE_PER_DAY },
                false
            )
        }

        coEvery { notificationScheduler.schedule(any(), true) } throws IllegalStateException("boom")
        viewModel.forceScheduleWorkers()
        advanceUntilIdle()
        assertEquals(
            "Failed to schedule alarms: boom",
            viewModel.uiState.value.errorMessage
        )
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            settingsRepository = settingsRepository,
            notificationTestSender = notificationTestSender,
            notificationScheduler = notificationScheduler,
            diagnosticsRepository = diagnosticsRepository,
            stooqRequestBlocker = stooqRequestBlocker,
            translationService = translationService
        )
    }

    private fun TestScope.startUiStateCollection(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun TestScope.waitUntil(
        timeoutMillis: Long = 1_000L,
        predicate: () -> Boolean
    ) {
        val deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000
        while (!predicate() && System.nanoTime() < deadlineNanos) {
            advanceUntilIdle()
            Thread.sleep(10)
        }
        advanceUntilIdle()
    }

    private fun testSettings(
        frequency: NotificationFrequency = NotificationFrequency.THREE_PER_DAY,
        offlineTranslationEnabled: Boolean = true
    ): AppSettings {
        return AppSettings(
            frequency = frequency,
            notificationTypes = setOf(
                NotificationType.WATCHLIST,
                NotificationType.MARKET_MOVERS,
                NotificationType.DIGESTS
            ),
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
            immediatePostsEnabled = false,
            offlineTranslationEnabled = offlineTranslationEnabled,
            onboardingCompleted = true,
            holdingPeriod = HoldingPeriod.MONTHS
        )
    }
}
