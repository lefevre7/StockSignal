package com.example.stocksignal.notifications

import android.util.Log
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.model.Result as StooqResult
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Unit tests for [NotificationWindowRunner] early-exit paths and key branches
 * not covered by [NotificationWindowWorkerTest].
 */
@RunWith(RobolectricTestRunner::class)
class NotificationWindowRunnerTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val watchlistRepository = mockk<WatchlistRepository>(relaxed = true)
    private val marketMoversRepository = mockk<MarketMoversRepository>(relaxed = true)
    private val stockRepository = mockk<StockRepository>(relaxed = true)
    private val signalsRepository = mockk<SignalsRepository>(relaxed = true)
    private val notificationQueueProcessor = mockk<NotificationQueueProcessor>(relaxed = true)
    private val diagnosticsRepository = mockk<NotificationDiagnosticsRepository>(relaxed = true)
    private val backgroundRunPolicy = mockk<BackgroundStooqRunPolicy>(relaxed = true)

    private fun buildRunner(): NotificationWindowRunner {
        val context = RuntimeEnvironment.getApplication()
        return NotificationWindowRunner(
            context,
            settingsRepository,
            watchlistRepository,
            marketMoversRepository,
            stockRepository,
            signalsRepository,
            notificationQueueProcessor,
            diagnosticsRepository,
            backgroundRunPolicy
        )
    }

    private fun settings(
        notificationTypes: Set<NotificationType> = setOf(NotificationType.WATCHLIST),
        frequency: NotificationFrequency = NotificationFrequency.THREE_PER_DAY
    ) = AppSettings(
        frequency = frequency,
        notificationTypes = notificationTypes,
        quietHours = QuietHours(enabled = false, start = "22:00", end = "07:00"),
        scheduleWindows = emptyList<ScheduleWindow>(),
        weeklyDay = DayOfWeek.MONDAY,
        snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
        signalSensitivity = SignalSensitivity(60, 60, -60),
        selectedChartRange = ChartRange.ONE_DAY,
        immediatePostsEnabled = false,
        offlineTranslationEnabled = false,
        onboardingCompleted = true,
        holdingPeriod = HoldingPeriod.MONTHS
    )

    private fun disabledItem(symbol: String = "AAPL.US") = WatchlistItemEntity(
        symbol = symbol,
        companyName = "Apple",
        exchange = "XNAS",
        addedAt = LocalDateTime.now().minusDays(1),
        alertEnabled = false,
        minScoreForNotify = 60,
        quietHoursStart = null,
        quietHoursEnd = null,
        snoozedUntil = null,
        lastSignalScore = null,
        lastSignalLabel = null,
        lastSignalConfidence = null,
        lastSignalTime = null,
        notes = null,
        sortOrder = 0,
        tags = emptyList(),
        muteMarketMovers = false,
        lastNotifiedAt = null,
        indicatorAlertsJson = null
    )

    private fun snoozedItem() = disabledItem().copy(
        alertEnabled = true,
        snoozedUntil = LocalDateTime.now().plusHours(2)
    )

    private fun eligibleItem() = disabledItem().copy(alertEnabled = true)

    // ============== Early-exit skip paths ==============

    @Test
    fun `no notification sources enabled returns SUCCESS with skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(
            settings(notificationTypes = emptySet())
        )
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordWindowRun("win1", "skipped", any()) }
    }

    @Test
    fun `ONLY_WHEN_OPEN frequency returns SUCCESS with skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(
            settings(frequency = NotificationFrequency.ONLY_WHEN_OPEN)
        )
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordWindowRun("win1", "skipped", any()) }
    }

    @Test
    fun `backgroundRunPolicy skip reason returns SUCCESS with skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(settings())
        every { backgroundRunPolicy.windowSkipReason(any(), any()) } returns "stooq_blocked"
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordWindowRun("win1", "skipped", "stooq_blocked") }
    }

    // ============== Watchlist item skip paths ==============

    @Test
    fun `watchlist item with alerts disabled is skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(settings())
        every { backgroundRunPolicy.windowSkipReason(any(), any()) } returns null
        coEvery { watchlistRepository.getAll() } returns listOf(disabledItem())
        coEvery { notificationQueueProcessor.processQueued(any()) } just runs
        val result = buildRunner().run("win1")
        // Should complete successfully without fetching series
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        coVerify(exactly = 0) { stockRepository.getSeries(any(), any(), any(), any()) }
    }

    @Test
    fun `watchlist item snoozed is skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(settings())
        every { backgroundRunPolicy.windowSkipReason(any(), any()) } returns null
        coEvery { watchlistRepository.getAll() } returns listOf(snoozedItem())
        coEvery { notificationQueueProcessor.processQueued(any()) } just runs
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        coVerify(exactly = 0) { stockRepository.getSeries(any(), any(), any(), any()) }
    }

    // ============== Market movers path (movers-only settings) ==============

    @Test
    fun `market movers enabled with empty movers list completes successfully`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(
            settings(notificationTypes = setOf(NotificationType.MARKET_MOVERS))
        )
        every { backgroundRunPolicy.windowSkipReason(any(), any()) } returns null
        coEvery { watchlistRepository.getAll() } returns emptyList()
        // Market movers batch returns error → empty movers list
        coEvery { marketMoversRepository.getMarketMoversBatch(any(), any(), any()) } returns
            StooqResult.Error(Exception("network"), "network")
        coEvery { notificationQueueProcessor.processQueued(any()) } just runs
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
    }

    // ============== Exception handling ==============

    @Test
    fun `unexpected exception in run returns FAILURE`() = runTest {
        every { settingsRepository.settingsFlow } throws RuntimeException("unexpected crash")
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.FAILURE, result)
    }

    // ============== allowAiGeneration flag ==============

    @Test
    fun `allowAiGeneration false skips AI generation in signal computation`() = runTest {
        val now = LocalDateTime.now()
        every { settingsRepository.settingsFlow } returns flowOf(settings())
        every { backgroundRunPolicy.windowSkipReason(any(), any()) } returns null
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { stockRepository.getSeries(any(), any(), any(), any()) } returns StooqResult.Success(
            sampleCandles(now, 25)
        )
        coEvery { stockRepository.getStockOverview(any()) } returns StooqResult.Error(Exception("no overview"), "no overview")
        coEvery { signalsRepository.computeSignal(any(), any(), any(), any(), any()) } returns sampleSignal(now)
        coEvery { signalsRepository.isInCooldown(any(), any(), any()) } returns false
        coEvery { signalsRepository.recordEvent(any()) } just runs
        coEvery { notificationQueueProcessor.processCandidates(any(), any()) } just runs

        val result = buildRunner().run("win1", allowAiGeneration = false)
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        // With allowAiGeneration=false, AI is skipped
        coVerify { signalsRepository.computeSignal(any(), any(), any(), any(), skipAiGeneration = true) }
    }

    // ============== Watchlist + market movers both enabled ==============

    @Test
    fun `both sources enabled processes watchlist and movers`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(
            settings(notificationTypes = setOf(NotificationType.WATCHLIST, NotificationType.MARKET_MOVERS))
        )
        every { backgroundRunPolicy.windowSkipReason(any(), any()) } returns null
        coEvery { watchlistRepository.getAll() } returns emptyList()
        coEvery { marketMoversRepository.getMarketMoversBatch(any(), any(), any()) } returns
            StooqResult.Error(Exception("network"), "network")
        coEvery { notificationQueueProcessor.processQueued(any()) } just runs
        val result = buildRunner().run("win1")
        assertEquals(NotificationWindowRunner.RunOutcome.SUCCESS, result)
        // Both paths were attempted
        coVerify { marketMoversRepository.getMarketMoversBatch(any(), any(), any()) }
    }

    // ============== Helpers ==============

    private fun sampleCandles(now: LocalDateTime, count: Int) =
        (0 until count).map { i ->
            PriceCandle(
                time = now.minusMinutes((count - i) * 5L),
                open = 100.0 + i, high = 105.0 + i, low = 99.0 + i,
                close = 104.0 + i, volume = 1_000L + i
            )
        }

    private fun sampleSignal(now: LocalDateTime, score: Int = 80) = SignalResult(
        score = score, averageScore = score, modeScore = score, confidence = 85,
        aiScore = null, aiConfidence = null, aiSummary = null,
        aiReasons = emptyList(), reasons = emptyList(),
        modelScores = emptyMap(), generatedAt = now
    )
}
