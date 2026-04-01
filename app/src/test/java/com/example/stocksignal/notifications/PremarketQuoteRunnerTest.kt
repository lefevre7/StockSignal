package com.example.stocksignal.notifications

import android.util.Log
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.StockRepository
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
import com.example.stocksignal.data.stooq.model.PremarketQuote
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.data.stooq.repository.StooqRepository
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PremarketQuoteRunnerTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val watchlistRepository: WatchlistRepository = mockk(relaxed = true)
    private val stooqRepository: StooqRepository = mockk(relaxed = true)
    private val marketMoversRepository: MarketMoversRepository = mockk(relaxed = true)
    private val stockRepository: StockRepository = mockk(relaxed = true)
    private val diagnosticsRepository: NotificationDiagnosticsRepository = mockk(relaxed = true)
    private val backgroundRunPolicy: BackgroundStooqRunPolicy = mockk(relaxed = true)

    private lateinit var runner: PremarketQuoteRunner

    private val testWindow = ScheduleWindow(
        id = "win1",
        type = ScheduleWindowType.MARKET_OPEN_MINUS,
        hour = null,
        minute = null,
        zoneId = "America/New_York",
        offsetMinutes = -60
    )

    private fun testSettings() = AppSettings(
        frequency = NotificationFrequency.THREE_PER_DAY,
        notificationTypes = setOf(NotificationType.WATCHLIST),
        quietHours = QuietHours(false, "22:00", "07:00"),
        scheduleWindows = listOf(testWindow),
        weeklyDay = DayOfWeek.MONDAY,
        snoozeDuration = SnoozeDurationOption.ONE_HOUR,
        signalSensitivity = SignalSensitivity(25, 60, -60),
        selectedChartRange = ChartRange.ONE_DAY,
        immediatePostsEnabled = false,
        offlineTranslationEnabled = false,
        onboardingCompleted = true,
        holdingPeriod = HoldingPeriod.MONTHS
    )

    private fun eligibleItem(symbol: String = "AAPL.US") = WatchlistItemEntity(
        symbol = symbol,
        companyName = "Apple Inc",
        exchange = "XNAS",
        addedAt = LocalDateTime.now().minusDays(1),
        alertEnabled = true,
        minScoreForNotify = 25,
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
        lastNotifiedAt = null
    )

    /** Set up PremarketWindowUtils object mocks to allow run() to proceed past window checks. */
    private fun mockWindowsOpen() {
        every { PremarketWindowUtils.resolvePremarketWindow(any(), any(), any()) } returns testWindow
        every { PremarketWindowUtils.marketZone(any()) } returns ZoneId.of("America/New_York")
        every { PremarketWindowUtils.isDuringMarketHours(any()) } returns false
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        mockkObject(PremarketWindowUtils)
        // By default: policy passes (no skip reason)
        every { backgroundRunPolicy.premarketSkipReason(any(), any()) } returns null

        runner = PremarketQuoteRunner(
            settingsRepository,
            watchlistRepository,
            stooqRepository,
            marketMoversRepository,
            stockRepository,
            diagnosticsRepository,
            backgroundRunPolicy
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ============== Input validation ==============

    @Test
    fun `blank windowId returns FAILURE`() = runTest {
        val result = runner.run("", 0)
        assertEquals(PremarketQuoteRunner.RunOutcome.FAILURE, result)
    }

    @Test
    fun `negative sampleIndex returns FAILURE`() = runTest {
        val result = runner.run("win1", -1)
        assertEquals(PremarketQuoteRunner.RunOutcome.FAILURE, result)
    }

    // ============== Skip paths returning SUCCESS ==============

    @Test
    fun `watchlist notifications disabled returns SUCCESS skipped`() = runTest {
        val settings = testSettings().copy(notificationTypes = emptySet())
        every { settingsRepository.settingsFlow } returns flowOf(settings)
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "skipped", any(), null, 0, 0, null) }
    }

    @Test
    fun `ONLY_WHEN_OPEN frequency returns SUCCESS skipped`() = runTest {
        val settings = testSettings().copy(frequency = NotificationFrequency.ONLY_WHEN_OPEN)
        every { settingsRepository.settingsFlow } returns flowOf(settings)
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "skipped", any(), null, 0, 0, null) }
    }

    @Test
    fun `backgroundRunPolicy skip reason returns SUCCESS skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        every { backgroundRunPolicy.premarketSkipReason(any(), any()) } returns "stooq_blocked"
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
    }

    @Test
    fun `resolvePremarketWindow returns null returns SUCCESS skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        every { PremarketWindowUtils.resolvePremarketWindow(any(), any(), any()) } returns null
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "skipped", "window not in premarket", null, 0, 0, null) }
    }

    @Test
    fun `isDuringMarketHours returns SUCCESS skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        every { PremarketWindowUtils.isDuringMarketHours(any()) } returns true
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
    }

    @Test
    fun `empty eligible watchlist returns SUCCESS skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns emptyList()
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "skipped", "no eligible watchlist items", null, 0, 0, null) }
    }

    @Test
    fun `watchlist with all alerts disabled returns SUCCESS skipped`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(
            eligibleItem().copy(alertEnabled = false)
        )
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
    }

    @Test
    fun `watchlist with snoozed item is filtered out`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(
            eligibleItem().copy(snoozedUntil = LocalDateTime.now().plusHours(1))
        )
        val result = runner.run("win1", 1)
        // All items snoozed → empty eligible → skipped
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
    }

    // ============== sampleIndex == 0 triggers prefetch ==============

    @Test
    fun `sampleIndex 0 triggers prefetchIntraday`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { marketMoversRepository.getMarketMoversBatch(any(), any(), any()) } returns
            Result.Error(Exception("no movers"), "no movers")
        coEvery { stooqRepository.getIntradayData(any()) } returns
            Result.Error(Exception("prefetch fail"), "prefetch fail")
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(emptyMap())
        runner.run("win1", 0)
        // Verify prefetch was attempted (sampleIndex == 0)
        coVerify { stooqRepository.getIntradayData(any()) }
    }

    // ============== Quote processing outcomes ==============

    @Test
    fun `quote fetch failure with no upserts records no_data`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Error(Exception("network"), "network")
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        // quotes returned empty map after error → quoteCount=0, upsertedCount=0, errorCount=1 (from recordError)
        // result = "no_data" because upsertedCount==0 and errorCount > 0 (but no upserts)
        // Actually errorCount > 0 && upsertedCount == 0 → "failure"
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "failure", any(), any(), 0, 0, 1) }
    }

    @Test
    fun `quote with missing bid returns error recorded`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { stockRepository.getLatestCachedCandleForDate(any(), any()) } returns null
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(mapOf(
                "AAPL.US" to PremarketQuote("AAPL.US", bid = null, ask = 150.0, volume = null)
            ))
        val result = runner.run("win1", 1)
        // bid is null, fallback is null → recordError, upsertedCount=0, errorCount=1 → "failure"
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "failure", any(), any(), 0, 1, 1) }
    }

    @Test
    fun `successful quote upsert records success`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { stockRepository.getLatestCachedCandleForDate(any(), any()) } returns null
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(mapOf(
                "AAPL.US" to PremarketQuote("AAPL.US", bid = 148.5, ask = 150.0, volume = 10000L)
            ))
        coEvery { stockRepository.upsertPremarketCandle(any(), any()) } returns Unit
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "success", null, any(), 1, 1, null) }
    }

    @Test
    fun `partial upsert with errors records partial`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(
            eligibleItem("AAPL.US"), eligibleItem("TSLA.US")
        )
        coEvery { stockRepository.getLatestCachedCandleForDate(any(), any()) } returns null
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(mapOf(
                "AAPL.US" to PremarketQuote("AAPL.US", bid = 148.5, ask = 150.0, volume = null),
                "TSLA.US" to PremarketQuote("TSLA.US", bid = null, ask = null, volume = null)
            ))
        coEvery { stockRepository.upsertPremarketCandle("AAPL.US", any()) } returns Unit
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "partial", any(), any(), 1, 2, 1) }
    }

    @Test
    fun `quotes empty map records no_data`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(emptyMap())
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "no_data", null, any(), 0, 0, null) }
    }

    @Test
    fun `upsertPremarketCandle throwing exception records failure`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        coEvery { stockRepository.getLatestCachedCandleForDate(any(), any()) } returns null
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(mapOf(
                "AAPL.US" to PremarketQuote("AAPL.US", bid = 148.5, ask = 150.0, volume = null)
            ))
        coEvery { stockRepository.upsertPremarketCandle(any(), any()) } throws RuntimeException("db")
        val result = runner.run("win1", 1)
        // upsertedCount = 0, errorCount = 1 → "failure"
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "failure", any(), any(), 0, 1, 1) }
    }

    @Test
    fun `unexpected exception returns FAILURE`() = runTest {
        every { settingsRepository.settingsFlow } throws RuntimeException("unexpected")
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.FAILURE, result)
    }

    @Test
    fun `bid from fallback candle is used when quote bid is null`() = runTest {
        every { settingsRepository.settingsFlow } returns flowOf(testSettings())
        mockWindowsOpen()
        coEvery { watchlistRepository.getAll() } returns listOf(eligibleItem())
        val fallbackCandle = PriceCandle(LocalDateTime.now(), 147.0, 152.0, 146.0, 149.0, 5000L)
        coEvery { stockRepository.getLatestCachedCandleForDate(any(), any()) } returns fallbackCandle
        coEvery { stooqRepository.getPremarketQuotes(any()) } returns
            Result.Success(mapOf(
                "AAPL.US" to PremarketQuote("AAPL.US", bid = null, ask = null, volume = null)
            ))
        coEvery { stockRepository.upsertPremarketCandle(any(), any()) } returns Unit
        // bid=null → use fallback.open=147.0, ask=null → use fallback.close=149.0
        val result = runner.run("win1", 1)
        assertEquals(PremarketQuoteRunner.RunOutcome.SUCCESS, result)
        // upsertedCount = 1, errorCount = 0 → "success"
        coVerify { stockRepository.upsertPremarketCandle("AAPL.US", any()) }
        coVerify { diagnosticsRepository.recordPremarketRunResult(any(), "success", null, any(), 1, 1, null) }
    }
}
