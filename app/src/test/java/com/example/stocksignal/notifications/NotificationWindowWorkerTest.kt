package com.example.stocksignal.notifications

import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
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
import io.mockk.slot
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

@RunWith(RobolectricTestRunner::class)
class NotificationWindowWorkerTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val watchlistRepository = mockk<WatchlistRepository>()
    private val marketMoversRepository = mockk<MarketMoversRepository>(relaxed = true)
    private val stockRepository = mockk<StockRepository>()
    private val signalsRepository = mockk<SignalsRepository>()
    private val notificationQueueProcessor = mockk<NotificationQueueProcessor>()
    private val diagnosticsRepository = mockk<NotificationDiagnosticsRepository>(relaxed = true)

    @Test
    fun `processes watchlist candidates`() = runTest {
        val now = LocalDateTime.now()
        every { settingsRepository.settingsFlow } returns flowOf(defaultSettings())
        coEvery { watchlistRepository.getAll() } returns listOf(sampleWatchlistItem("AAPL", now))
        coEvery { stockRepository.getSeries(any(), any(), any(), any()) } returns StooqResult.Success(
            sampleCandles(now, 25) // Need at least 20 candles for signal computation
        )
        coEvery { stockRepository.getStockOverview(any()) } returns StooqResult.Error(Exception("no overview"))
        coEvery { signalsRepository.computeSignal(any(), any(), any(), any(), any()) } returns sampleSignal(now)
        coEvery { signalsRepository.isInCooldown(any(), any(), any()) } returns false
        coEvery { signalsRepository.recordEvent(any()) } just runs
        val candidatesSlot = slot<List<com.example.stocksignal.domain.model.NotificationEvent>>()
        coEvery { notificationQueueProcessor.processCandidates(capture(candidatesSlot), any()) } just runs

        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(candidatesSlot.captured.isNotEmpty())
        coVerify { notificationQueueProcessor.processCandidates(any(), any()) }
    }

    @Test
    fun `processes multiple watchlist candidates`() = runTest {
        val now = LocalDateTime.now()
        every { settingsRepository.settingsFlow } returns flowOf(defaultSettings())
        coEvery { watchlistRepository.getAll() } returns listOf(
            sampleWatchlistItem("AAPL", now),
            sampleWatchlistItem("MSFT", now)
        )
        coEvery { stockRepository.getSeries(any(), any(), any(), any()) } returns StooqResult.Success(
            sampleCandles(now, 25) // Need at least 20 candles for signal computation
        )
        coEvery { stockRepository.getStockOverview(any()) } returns StooqResult.Error(Exception("no overview"))
        coEvery { signalsRepository.computeSignal(any(), any(), any(), any(), any()) } returns sampleSignal(now)
        coEvery { signalsRepository.isInCooldown(any(), any(), any()) } returns false
        coEvery { signalsRepository.recordEvent(any()) } just runs
        val candidatesSlot = slot<List<com.example.stocksignal.domain.model.NotificationEvent>>()
        coEvery { notificationQueueProcessor.processCandidates(capture(candidatesSlot), any()) } just runs

        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(2, candidatesSlot.captured.size)
        assertTrue(candidatesSlot.captured.map { it.ticker }.containsAll(listOf("AAPL", "MSFT")))
    }

    @Test
    fun `uses ai score for watchlist thresholds`() = runTest {
        val now = LocalDateTime.now()
        every { settingsRepository.settingsFlow } returns flowOf(defaultSettings())
        coEvery { watchlistRepository.getAll() } returns listOf(sampleWatchlistItem("AAPL", now))
        coEvery { stockRepository.getSeries(any(), any(), any(), any()) } returns StooqResult.Success(
            sampleCandles(now, 25)
        )
        coEvery { stockRepository.getStockOverview(any()) } returns StooqResult.Error(Exception("no overview"))
        coEvery { signalsRepository.computeSignal(any(), any(), any(), any(), any()) } returns sampleSignal(
            now,
            score = 10,
            aiScore = 80,
            aiConfidence = 88
        )
        coEvery { signalsRepository.isInCooldown(any(), any(), any()) } returns false
        coEvery { signalsRepository.recordEvent(any()) } just runs
        val candidatesSlot = slot<List<com.example.stocksignal.domain.model.NotificationEvent>>()
        coEvery { notificationQueueProcessor.processCandidates(capture(candidatesSlot), any()) } just runs

        val worker = buildWorker()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(candidatesSlot.captured.isNotEmpty())
        assertEquals(80, candidatesSlot.captured.first().displayScore)
    }

    private fun buildWorker(): NotificationWindowWorker {
        val context = RuntimeEnvironment.getApplication()
        val runner = NotificationWindowRunner(
            context,
            settingsRepository,
            watchlistRepository,
            marketMoversRepository,
            stockRepository,
            signalsRepository,
            notificationQueueProcessor,
            diagnosticsRepository
        )
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: android.content.Context,
                workerClassName: String,
                params: WorkerParameters
            ): ListenableWorker {
                return NotificationWindowWorker(
                    appContext,
                    params,
                    runner
                )
            }
        }
        return TestListenableWorkerBuilder<NotificationWindowWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(NotificationWindowWorker.KEY_WINDOW_ID to "window_test"))
            .build()
    }

    private fun sampleWatchlistItem(symbol: String, now: LocalDateTime): WatchlistItemEntity {
        return WatchlistItemEntity(
            symbol = symbol,
            companyName = symbol,
            exchange = null,
            addedAt = now.minusDays(1),
            alertEnabled = true,
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
    }

    private fun sampleSignal(
        now: LocalDateTime,
        score: Int = 80,
        aiScore: Int? = null,
        aiConfidence: Int? = null
    ): SignalResult {
        return SignalResult(
            score = score,
            averageScore = score,
            modeScore = score,
            confidence = 85,
            aiScore = aiScore,
            aiConfidence = aiConfidence,
            aiSummary = null,
            aiReasons = emptyList(),
            reasons = emptyList(),
            modelScores = emptyMap(),
            generatedAt = now
        )
    }

    private fun sampleCandles(now: LocalDateTime, count: Int): List<PriceCandle> {
        return (0 until count).map { i ->
            PriceCandle(
                time = now.minusMinutes((count - i) * 5L),
                open = 100.0 + i,
                high = 105.0 + i,
                low = 99.0 + i,
                close = 104.0 + i,
                volume = 1_000L + i
            )
        }
    }

    private fun defaultSettings(): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(NotificationType.WATCHLIST, NotificationType.DIGESTS),
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
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
