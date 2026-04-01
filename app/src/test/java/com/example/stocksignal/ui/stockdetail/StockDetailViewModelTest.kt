package com.example.stocksignal.ui.stockdetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.translation.NewsTranslationService
import com.example.stocksignal.domain.export.ExportResult
import com.example.stocksignal.domain.export.IntradayDataExporter
import com.example.stocksignal.domain.model.AiScoreReason
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.StockNewsItem
import com.example.stocksignal.domain.model.StockOverview
import com.example.stocksignal.testutil.MainDispatcherRule
import com.example.stocksignal.ui.model.AiGenerationState
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stockRepository = mockk<StockRepository>()
    private val signalsRepository = mockk<SignalsRepository>()
    private val watchlistRepository = mockk<WatchlistRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val translationService = mockk<NewsTranslationService>()
    private val intradayDataExporter = mockk<IntradayDataExporter>()

    private lateinit var settingsFlow: MutableStateFlow<AppSettings>
    private lateinit var watchlistFlow: MutableStateFlow<List<WatchlistItemEntity>>
    private lateinit var historyFlow: MutableStateFlow<List<NotificationEvent>>

    private var storedWatchlistEntry: WatchlistItemEntity? = null
    private var localModelUsable = false
    private var localModelIncompatible = false
    private var localModelIncompatibilityMessage: String? = null
    private var warningMessage: String? = null
    private var onWifi = true
    private var enoughStorage = true
    private var downloadSucceeds = true
    private var assetPackSucceeds = false

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        settingsFlow = MutableStateFlow(testSettings())
        watchlistFlow = MutableStateFlow(emptyList())
        historyFlow = MutableStateFlow(emptyList())

        every { settingsRepository.settingsFlow } returns settingsFlow
        coEvery { settingsRepository.setSelectedChartRange(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(selectedChartRange = firstArg())
        }
        coEvery { settingsRepository.setOfflineTranslationEnabled(any()) } answers {
            settingsFlow.value = settingsFlow.value.copy(offlineTranslationEnabled = firstArg())
        }

        every { watchlistRepository.watchlistFlow } returns watchlistFlow
        coEvery { watchlistRepository.getBySymbol(any()) } answers {
            storedWatchlistEntry?.takeIf { it.symbol == firstArg<String>() }
        }
        coEvery { watchlistRepository.upsert(any()) } answers {
            setStoredWatchlistEntry(firstArg())
        }
        coEvery { watchlistRepository.deleteBySymbol(any()) } answers {
            if (storedWatchlistEntry?.symbol == firstArg<String>()) {
                setStoredWatchlistEntry(null)
            }
        }

        every { signalsRepository.eventsForTicker(any()) } returns historyFlow
        coEvery { signalsRepository.checkCachedSignal(any(), any(), any()) } returns null
        coEvery { signalsRepository.computeSignal(any(), any(), any(), any(), any()) } returns null

        coEvery { stockRepository.getSeriesForDetail(any(), any(), any()) } returns
            Result.Error(IllegalStateException("Series unavailable"), "Series unavailable")
        coEvery { stockRepository.refreshIntradayHistory(any(), any()) } just runs
        coEvery { stockRepository.getStockOverview(any(), any()) } returns
            Result.Error(IllegalStateException("Overview unavailable"), "Overview unavailable")
        coEvery { stockRepository.updateOverviewNews(any(), any()) } just runs

        coEvery { intradayDataExporter.exportToCSV(any(), any()) } returns ExportResult.NoData

        coEvery { translationService.isLocalModelAvailable() } answers { localModelUsable }
        coEvery { translationService.isLocalModelUsable() } answers { localModelUsable }
        every { translationService.getLocalModelRequiredBytes() } returns 584_417_280L
        every { translationService.getLocalModelExpectedBytes() } returns 584_417_280L
        every { translationService.getLocalModelSha256() } returns "abc123"
        every { translationService.getLocalModelFilePath() } returns "/tmp/stocksignal-model.litertlm"
        every { translationService.isLocalModelIncompatible() } answers { localModelIncompatible }
        every { translationService.getLocalModelIncompatibilityMessage() } answers {
            localModelIncompatibilityMessage
        }
        every { translationService.consumeWarningMessage() } answers {
            warningMessage.also { warningMessage = null }
        }
        every { translationService.hasEnoughStorage(any()) } answers { enoughStorage }
        every { translationService.isOnWifi() } answers { onWifi }
        coEvery { translationService.downloadLocalModel(any()) } answers {
            val progress = firstArg<(Int) -> Unit>()
            progress(25)
            progress(100)
            if (downloadSucceeds) {
                localModelUsable = true
                true
            } else {
                false
            }
        }
        coEvery { translationService.tryFetchLocalModelFromAssetPack() } answers {
            if (assetPackSucceeds) {
                localModelUsable = true
            }
            assetPackSucceeds
        }
        coEvery { translationService.translateWithLocalModel(any()) } answers {
            "Translated: ${firstArg<String>()}"
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `init with saved state loads stock detail history and ai result`() = runTest(mainDispatcherRule.dispatcher) {
        val series = sampleSeries()
        val cachedSignal = sampleSignal(score = 45, aiScore = 61, summary = "Cached AI score")
        val freshSignal = sampleSignal(score = 45, aiScore = 74, summary = "Fresh AI score")
        val historyEvent = sampleEvent("evt-1", "AAPL")
        historyFlow.value = listOf(historyEvent)
        settingsFlow.value = testSettings(selectedChartRange = ChartRange.ONE_MONTH)
        setStoredWatchlistEntry(
            watchlistEntry(
                symbol = "AAPL",
                companyName = "Apple Inc.",
                exchange = "NASDAQ",
                alertEnabled = true,
                tags = listOf("core")
            )
        )
        coEvery { stockRepository.getSeriesForDetail("AAPL", ChartRange.ONE_MONTH, false) } returns Result.Success(series)
        coEvery { stockRepository.getStockOverview("AAPL", false) } returns
            Result.Success(sampleOverview(symbol = "AAPL"))
        coEvery { signalsRepository.checkCachedSignal("AAPL", series, ChartRange.ONE_MONTH) } returns cachedSignal
        coEvery {
            signalsRepository.computeSignal("AAPL", series, ChartRange.ONE_MONTH, any(), false)
        } returns freshSignal

        val viewModel = createViewModel(
            SavedStateHandle(
                mapOf(
                    StockDetailViewModel.ARG_TICKER to "AAPL",
                    StockDetailViewModel.ARG_EVENT_ID to "evt-1",
                    StockDetailViewModel.ARG_OPEN_ALERTS to true
                )
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("AAPL", state.ticker)
        assertEquals("evt-1", state.highlightEventId)
        assertTrue(state.openAlerts)
        assertEquals(ChartRange.ONE_MONTH, state.range)
        assertEquals("Apple Inc.", state.companyName)
        assertEquals("NASDAQ", state.exchange)
        assertTrue(state.inWatchlist)
        assertTrue(state.alertEnabled)
        assertEquals(listOf("core"), state.tags)
        assertEquals(series, state.series)
        assertEquals(freshSignal, state.signal)
        assertEquals(listOf(historyEvent), state.history)
        assertEquals(AiGenerationState.COMPLETE, state.aiGenerationState)
        assertEquals(1_250_000_000.0, state.marketCap)
        assertEquals(18.5, state.peRatio)
        assertEquals(1.2, state.dividend)
        assertEquals(190.0, state.week52High)
        assertEquals(120.0, state.week52Low)
        assertNull(state.errorMessage)
        assertNull(state.overviewError)
        coVerify { stockRepository.refreshIntradayHistory("AAPL", ChartRange.ONE_MONTH) }
    }

    @Test
    fun `watchlist indicator alerts tags and toggle actions persist through repository`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTicker("MSFT")
        advanceUntilIdle()
        viewModel.loadIndicatorAlerts()
        advanceUntilIdle()

        assertEquals(IndicatorMetric.values().size, viewModel.uiState.value.indicatorAlerts.size)
        assertTrue(viewModel.uiState.value.indicatorAlerts.none { it.enabled })

        viewModel.updateIndicatorAlert(
            metric = IndicatorMetric.RSI_14,
            enabled = true,
            threshold = 35.0,
            direction = AlertDirection.BELOW
        )
        advanceUntilIdle()
        viewModel.saveIndicatorAlerts()
        advanceUntilIdle()

        val savedAlerts = IndicatorAlertJson.fromJson(storedWatchlistEntry?.indicatorAlertsJson)
        assertTrue(viewModel.uiState.value.inWatchlist)
        assertTrue(viewModel.uiState.value.alertEnabled)
        assertEquals(35.0, savedAlerts.first { it.metric == IndicatorMetric.RSI_14 }.threshold, 0.0)
        assertTrue(savedAlerts.first { it.metric == IndicatorMetric.RSI_14 }.enabled)

        viewModel.addTag("growth")
        advanceUntilIdle()
        assertEquals(listOf("growth"), storedWatchlistEntry?.tags)

        viewModel.addTag("Growth")
        advanceUntilIdle()
        assertEquals(listOf("growth"), storedWatchlistEntry?.tags)

        viewModel.saveIndicatorAlerts()
        advanceUntilIdle()
        assertEquals(listOf("growth"), storedWatchlistEntry?.tags)
        assertTrue(storedWatchlistEntry?.alertEnabled == true)

        viewModel.removeTag("growth")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), storedWatchlistEntry?.tags)

        viewModel.toggleWatchlist()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.inWatchlist)
        assertFalse(viewModel.uiState.value.alertEnabled)
        assertEquals(emptyList<String>(), viewModel.uiState.value.tags)
        assertNull(storedWatchlistEntry)

        viewModel.toggleWatchlist()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.inWatchlist)
        assertTrue(storedWatchlistEntry?.alertEnabled == true)
        assertEquals("MSFT", storedWatchlistEntry?.symbol)
    }

    @Test
    fun `offline model prompt downloads and translates news when retry succeeds`() = runTest(mainDispatcherRule.dispatcher) {
        val series = sampleSeries()
        val translatedSignal = sampleSignal(score = 52, aiScore = 67, summary = "AI translated")
        val news = listOf(sampleNews("Hola mundo"))
        settingsFlow.value = testSettings(selectedChartRange = ChartRange.ONE_MONTH, offlineTranslationEnabled = true)
        coEvery { stockRepository.getSeriesForDetail("TSLA", ChartRange.ONE_MONTH, false) } returns Result.Success(series)
        coEvery { stockRepository.getStockOverview("TSLA", false) } returns
            Result.Success(sampleOverview(symbol = "TSLA", news = news))
        coEvery { signalsRepository.checkCachedSignal("TSLA", series, ChartRange.ONE_MONTH) } returns translatedSignal
        coEvery {
            signalsRepository.computeSignal("TSLA", series, ChartRange.ONE_MONTH, any(), false)
        } returns translatedSignal

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTicker("TSLA")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showTranslationPrompt)
        assertEquals(TranslationPromptType.LOCAL_MODEL, viewModel.uiState.value.translationPromptType)

        viewModel.requestOfflineModelDownload()
        advanceUntilIdle()
        viewModel.retryTranslationDownload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showTranslationPrompt)
        assertFalse(state.translationDownloadInProgress)
        assertNull(state.translationDownloadProgress)
        assertFalse(state.showTranslationRetry)
        assertTrue(state.localModelAvailable)
        assertTrue(state.news.all { it.translatedTitle?.startsWith("Translated: ") == true })
        coVerify {
            stockRepository.updateOverviewNews(
                "TSLA",
                match { items -> items.single().translatedTitle == "Translated: Hola mundo" }
            )
        }
    }

    @Test
    fun `request download reports wifi requirement when retry cannot start`() = runTest(mainDispatcherRule.dispatcher) {
        val series = sampleSeries()
        val aiSignal = sampleSignal(score = 40, aiScore = 58, summary = "Cached")
        val news = listOf(sampleNews("Czesc swiecie"))
        onWifi = false
        settingsFlow.value = testSettings(selectedChartRange = ChartRange.ONE_MONTH, offlineTranslationEnabled = true)
        coEvery { stockRepository.getSeriesForDetail("NVDA", ChartRange.ONE_MONTH, false) } returns Result.Success(series)
        coEvery { stockRepository.getStockOverview("NVDA", false) } returns
            Result.Success(sampleOverview(symbol = "NVDA", news = news))
        coEvery { signalsRepository.checkCachedSignal("NVDA", series, ChartRange.ONE_MONTH) } returns aiSignal
        coEvery {
            signalsRepository.computeSignal("NVDA", series, ChartRange.ONE_MONTH, any(), false)
        } returns aiSignal

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTicker("NVDA")
        advanceUntilIdle()
        viewModel.requestOfflineModelDownload()
        advanceUntilIdle()
        viewModel.retryTranslationDownload()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Wi-Fi required to download the 1B offline model.", state.translationMessage)
        assertTrue(state.showTranslationRetry)
        assertFalse(state.showTranslationPrompt)
        assertFalse(state.translationDownloadInProgress)
    }

    @Test
    fun `select range refresh export and repository failures update ui state`() = runTest(mainDispatcherRule.dispatcher) {
        val series = sampleSeries()
        settingsFlow.value = testSettings(selectedChartRange = ChartRange.ONE_DAY)
        coEvery { stockRepository.getSeriesForDetail("NFLX", ChartRange.ONE_DAY, false) } returns
            Result.Error(IllegalStateException("Series down"), "Series down")
        coEvery { stockRepository.getSeriesForDetail("NFLX", ChartRange.FIVE_DAY, false) } returns
            Result.Error(IllegalStateException("Series still down"), "Series still down")
        coEvery { stockRepository.getSeriesForDetail("NFLX", ChartRange.FIVE_DAY, true) } returns
            Result.Success(series)
        coEvery { stockRepository.getStockOverview("NFLX", false) } returns
            Result.Error(IllegalStateException("Overview down"), "Overview down")
        coEvery { stockRepository.getStockOverview("NFLX", true) } returns
            Result.Success(sampleOverview(symbol = "NFLX"))
        coEvery { signalsRepository.checkCachedSignal("NFLX", series, ChartRange.FIVE_DAY) } returns null
        coEvery {
            signalsRepository.computeSignal("NFLX", series, ChartRange.FIVE_DAY, any(), false)
        } returns sampleSignal(score = 35, aiScore = 49, summary = "Recovered")
        coEvery { intradayDataExporter.exportToCSV("NFLX", any()) } returnsMany listOf(
            ExportResult.Success(
                filePath = "/tmp/nflx.csv",
                rowCount = 42,
                dateRange = LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 2, 11)
            ),
            ExportResult.NoData,
            ExportResult.Error("disk full")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setTicker("NFLX")
        advanceUntilIdle()
        assertEquals("Series down", viewModel.uiState.value.errorMessage)
        assertEquals("Overview down", viewModel.uiState.value.overviewError)

        viewModel.selectRange(ChartRange.FIVE_DAY)
        advanceUntilIdle()
        assertEquals(ChartRange.FIVE_DAY, viewModel.uiState.value.range)
        coVerify { settingsRepository.setSelectedChartRange(ChartRange.FIVE_DAY) }
        coVerify { stockRepository.refreshIntradayHistory("NFLX", ChartRange.FIVE_DAY) }

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(series, viewModel.uiState.value.series)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.overviewError)
        assertEquals(1_250_000_000.0, viewModel.uiState.value.marketCap)

        val exportFile = File.createTempFile("stocksignal-nflx", ".csv")
        try {
            viewModel.exportHistoricalData(exportFile)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.exportMessage!!.contains("Exported 42 candles"))

            viewModel.exportHistoricalData(exportFile)
            advanceUntilIdle()
            assertEquals("No historical data available for NFLX", viewModel.uiState.value.exportMessage)

            viewModel.exportHistoricalData(exportFile)
            advanceUntilIdle()
            assertEquals("Export failed: disk full", viewModel.uiState.value.exportMessage)
        } finally {
            exportFile.delete()
        }
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): StockDetailViewModel {
        return StockDetailViewModel(
            stockRepository = stockRepository,
            signalsRepository = signalsRepository,
            watchlistRepository = watchlistRepository,
            settingsRepository = settingsRepository,
            translationService = translationService,
            intradayDataExporter = intradayDataExporter,
            savedStateHandle = savedStateHandle
        )
    }

    private fun setStoredWatchlistEntry(entry: WatchlistItemEntity?) {
        storedWatchlistEntry = entry
        watchlistFlow.value = entry?.let(::listOf) ?: emptyList()
    }

    private fun sampleSeries(count: Int = 40): List<PriceCandle> {
        val start = LocalDateTime.of(2026, 1, 2, 9, 30)
        return (0 until count).map { index ->
            val close = if (index == count - 1) {
                120.0
            } else {
                100.0 + (index % 6) * 0.5 + index * 0.1
            }
            PriceCandle(
                time = start.plusDays(index.toLong()),
                open = close - 0.6,
                high = close + 1.0,
                low = close - 1.0,
                close = close,
                volume = if (index == count - 1) 20_000 else 1_000 + index * 15L
            )
        }
    }

    private fun sampleSignal(score: Int, aiScore: Int, summary: String): SignalResult {
        return SignalResult(
            score = score,
            averageScore = score,
            modeScore = score,
            confidence = 80,
            aiScore = aiScore,
            aiConfidence = 84,
            aiSummary = summary,
            aiReasons = listOf(AiScoreReason("AI", "Reason")),
            reasons = listOf(SignalReason("ma", "MA", "Moving average", score, "ma")),
            modelScores = mapOf("ma" to score),
            generatedAt = LocalDateTime.of(2026, 1, 31, 15, 0)
        )
    }

    private fun sampleOverview(symbol: String, news: List<StockNewsItem> = emptyList()): StockOverview {
        return StockOverview(
            symbol = symbol,
            marketCap = 1_250_000_000.0,
            peRatio = 18.5,
            dividend = 1.2,
            week52High = 190.0,
            week52Low = 120.0,
            news = news
        )
    }

    private fun sampleNews(title: String): StockNewsItem {
        return StockNewsItem(
            title = title,
            publishedAtText = "3h ago",
            publishedAt = Instant.parse("2026-03-31T13:00:00Z"),
            source = "Example",
            url = "https://example.com/$title"
        )
    }

    private fun sampleEvent(id: String, ticker: String): NotificationEvent {
        return NotificationEvent(
            id = id,
            type = NotificationEventType.WATCHLIST_SIGNAL,
            ticker = ticker,
            companyName = "$ticker Inc.",
            score = 55,
            averageScore = 52,
            modeScore = 50,
            confidence = 81,
            aiScore = 63,
            aiConfidence = 84,
            aiSummary = "AI summary",
            aiReasons = listOf(AiScoreReason("AI", "Reason")),
            price = 123.45,
            percentChange = 2.3,
            generatedAt = LocalDateTime.of(2026, 3, 31, 14, 0),
            notifiedAt = null,
            deepLink = "app://stock/$ticker",
            source = "watchlist",
            delivered = false,
            reasons = listOf(SignalReason("ma", "MA", "Moving average", 25, "ma"))
        )
    }

    private fun watchlistEntry(
        symbol: String,
        companyName: String,
        exchange: String? = "NYSE",
        alertEnabled: Boolean = true,
        tags: List<String> = emptyList(),
        indicatorAlertsJson: String? = null
    ): WatchlistItemEntity {
        return WatchlistItemEntity(
            symbol = symbol,
            companyName = companyName,
            exchange = exchange,
            addedAt = LocalDateTime.of(2026, 1, 1, 9, 30),
            alertEnabled = alertEnabled,
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
            tags = tags,
            muteMarketMovers = false,
            lastNotifiedAt = null,
            indicatorAlertsJson = indicatorAlertsJson
        )
    }

    private fun testSettings(
        selectedChartRange: ChartRange = ChartRange.ONE_DAY,
        offlineTranslationEnabled: Boolean = true
    ): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
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
                )
            ),
            weeklyDay = DayOfWeek.MONDAY,
            snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = selectedChartRange,
            immediatePostsEnabled = false,
            offlineTranslationEnabled = offlineTranslationEnabled,
            onboardingCompleted = false,
            holdingPeriod = HoldingPeriod.MONTHS
        )
    }
}
