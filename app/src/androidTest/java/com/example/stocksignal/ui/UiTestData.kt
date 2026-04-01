package com.example.stocksignal.ui

import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.AlertSettings
import com.example.stocksignal.domain.model.AiScoreReason
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.RecentSearch
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.SignalSnapshot
import com.example.stocksignal.domain.model.StockNewsItem
import com.example.stocksignal.domain.model.TechnicalIndicators
import com.example.stocksignal.domain.model.WatchlistItem
import com.example.stocksignal.ui.model.AiGenerationState
import com.example.stocksignal.ui.stockdetail.StockDetailUiState
import com.example.stocksignal.ui.stockdetail.TranslationPromptType
import com.example.stocksignal.ui.watchlist.WatchlistCardState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime

internal fun samplePriceCandles(count: Int = 8): List<PriceCandle> {
    val start = LocalDateTime.of(2026, 3, 31, 9, 30)
    return List(count) { index ->
        val base = 180.0 + index
        PriceCandle(
            time = start.plusMinutes(index.toLong()),
            open = base,
            high = base + 1.5,
            low = base - 1.5,
            close = base + 0.75,
            volume = 1_000_000L + index * 10_000L
        )
    }
}

internal fun sampleSignalResult(
    score: Int = 68,
    aiScore: Int? = 75,
    aiConfidence: Int? = 84,
    generatedAt: LocalDateTime = LocalDateTime.of(2026, 3, 31, 10, 15)
): SignalResult {
    return SignalResult(
        score = score,
        averageScore = 61,
        modeScore = 64,
        confidence = 72,
        aiScore = aiScore,
        aiConfidence = aiConfidence,
        aiSummary = "Momentum and breadth remain supportive.",
        aiReasons = listOf(
            AiScoreReason(title = "Momentum", detail = "Short-term price action is constructive."),
            AiScoreReason(title = "Trend", detail = "Trend remains above key moving averages.")
        ),
        reasons = listOf(
            SignalReason(
                id = "rsi",
                title = "RSI recovering",
                explanation = "RSI moved back above neutral.",
                impactScore = 18,
                model = "rsi"
            ),
            SignalReason(
                id = "ma",
                title = "Price above moving averages",
                explanation = "Price remains above the 50-day average.",
                impactScore = 22,
                model = "ma"
            )
        ),
        modelScores = mapOf(
            "ma" to 20,
            "rsi" to 18,
            "macd" to 14
        ),
        generatedAt = generatedAt
    )
}

internal fun sampleWatchlistItem(
    symbol: String = "AAPL",
    tags: List<String> = listOf("Tech", "Core"),
    lastNotifiedAt: LocalDateTime? = LocalDateTime.of(2026, 3, 31, 9, 45)
): WatchlistItem {
    return WatchlistItem(
        symbol = symbol,
        companyName = "Apple Inc.",
        exchange = "NASDAQ",
        addedAt = LocalDateTime.of(2026, 3, 30, 9, 30),
        alertSettings = AlertSettings(
            enabled = true,
            minScoreForNotify = 60,
            quietHoursStart = null,
            quietHoursEnd = null,
            snoozedUntil = LocalDateTime.of(2026, 3, 31, 12, 0),
            alwaysNotify = false,
            ignoreMarketMovers = false
        ),
        lastSignal = SignalSnapshot(
            score = 68,
            averageScore = 61,
            modeScore = 64,
            confidence = 72,
            generatedAt = LocalDateTime.of(2026, 3, 31, 10, 15)
        ),
        notes = "Watching for earnings follow-through.",
        tags = tags,
        sortOrder = 0,
        lastNotifiedAt = lastNotifiedAt,
        notificationActive = true,
        indicatorAlerts = listOf(
            IndicatorAlertSetting(
                metric = IndicatorMetric.RSI_14,
                threshold = 30.0,
                direction = AlertDirection.BELOW,
                enabled = true
            )
        )
    )
}

internal fun sampleWatchlistCardState(
    symbol: String = "AAPL",
    aiGenerationState: AiGenerationState = AiGenerationState.GENERATING
): WatchlistCardState {
    return WatchlistCardState(
        item = sampleWatchlistItem(symbol = symbol),
        series = samplePriceCandles(),
        signal = sampleSignalResult(),
        price = 186.42,
        percentChange = 2.31,
        updatedAt = LocalDateTime.of(2026, 3, 31, 10, 20),
        aiGenerationState = aiGenerationState
    )
}

internal fun sampleMarketMoverItem(
    ticker: String = "NVDA",
    rank: Int = 1,
    percentChange: Double = 4.2,
    series: List<PriceCandle> = samplePriceCandles(6)
): MarketMoverItem {
    return MarketMoverItem(
        ticker = ticker,
        companyName = "NVIDIA Corporation",
        exchange = "NASDAQ",
        price = 932.15,
        percentChange = percentChange,
        rank = rank,
        signalScore = 74,
        signalLabel = "Strong Buy",
        series = series
    )
}

internal fun sampleNotificationEvent(
    id: String = "evt-1",
    ticker: String = "AAPL",
    type: NotificationEventType = NotificationEventType.WATCHLIST_SIGNAL
): NotificationEvent {
    return NotificationEvent(
        id = id,
        type = type,
        ticker = ticker,
        companyName = "Apple Inc.",
        score = 61,
        averageScore = 58,
        modeScore = 60,
        confidence = 74,
        aiScore = 76,
        aiConfidence = 85,
        aiSummary = "AI favors continuation if support holds.",
        aiReasons = listOf(
            AiScoreReason(title = "Volume", detail = "Participation remains healthy."),
            AiScoreReason(title = "Trend", detail = "Trend still slopes upward.")
        ),
        price = 186.42,
        percentChange = 2.31,
        generatedAt = LocalDateTime.of(2026, 3, 31, 10, 18),
        notifiedAt = LocalDateTime.of(2026, 3, 31, 10, 19),
        deepLink = "stocksignal://stock/$ticker?eventId=$id",
        source = "watchlist",
        delivered = true,
        reasons = listOf(
            SignalReason(
                id = "macd",
                title = "MACD bullish",
                explanation = "MACD stayed above signal.",
                impactScore = 14,
                model = "macd"
            ),
            SignalReason(
                id = "bb",
                title = "Band expansion",
                explanation = "Price pushed the upper band with strength.",
                impactScore = 12,
                model = "bb"
            )
        )
    )
}

internal fun sampleSearchResult(symbol: String = "AAPL"): SearchResult {
    return SearchResult(
        symbol = symbol,
        companyName = "Apple Inc.",
        exchange = "NASDAQ",
        price = 186.42,
        percentChange = 2.31
    )
}

internal fun sampleRecentSearch(query: String = "AAPL"): RecentSearch {
    return RecentSearch(
        query = query,
        lastSearchedAt = LocalDateTime.of(2026, 3, 31, 8, 0),
        count = 3
    )
}

internal fun sampleAppSettings(
    frequency: NotificationFrequency = NotificationFrequency.THREE_PER_DAY,
    notificationTypes: Set<NotificationType> = setOf(
        NotificationType.WATCHLIST,
        NotificationType.MARKET_MOVERS,
        NotificationType.DIGESTS
    ),
    holdingPeriod: HoldingPeriod = HoldingPeriod.MONTHS
): AppSettings {
    return AppSettings(
        frequency = frequency,
        notificationTypes = notificationTypes,
        quietHours = QuietHours(
            enabled = true,
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
        weeklyDay = DayOfWeek.WEDNESDAY,
        snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
        signalSensitivity = SignalSensitivity(
            minScoreForNotify = 60,
            strongBuyThreshold = 60,
            strongSellThreshold = -60
        ),
        selectedChartRange = ChartRange.SIX_MONTH,
        immediatePostsEnabled = false,
        offlineTranslationEnabled = true,
        onboardingCompleted = true,
        holdingPeriod = holdingPeriod
    )
}

internal fun sampleStockDetailUiState(
    openAlerts: Boolean = false,
    showTranslationPrompt: Boolean = false
): StockDetailUiState {
    return StockDetailUiState(
        ticker = "AAPL",
        companyName = "Apple Inc.",
        exchange = "NASDAQ",
        inWatchlist = true,
        alertEnabled = true,
        tags = listOf("Tech", "Core"),
        range = ChartRange.ONE_DAY,
        series = samplePriceCandles(12),
        signal = sampleSignalResult(),
        indicators = TechnicalIndicators(
            rsi14 = 54.2,
            macd = 1.6,
            macdSignal = 1.2,
            macdHistogram = 0.4,
            sma5 = 184.0,
            sma20 = 180.0,
            sma50 = 176.0,
            sma200 = 165.0,
            atr14 = 3.5
        ),
        history = listOf(
            sampleNotificationEvent(id = "evt-1"),
            sampleNotificationEvent(
                id = "evt-2",
                ticker = "AAPL",
                type = NotificationEventType.MARKET_MOVER
            )
        ),
        marketCap = 2_900_000_000_000.0,
        peRatio = 28.4,
        dividend = 0.52,
        week52High = 199.62,
        week52Low = 142.10,
        news = listOf(
            StockNewsItem(
                title = "Apple launches a new device line",
                publishedAtText = "Mar 31, 2026",
                publishedAt = Instant.parse("2026-03-31T14:00:00Z"),
                source = "Example Wire",
                translatedTitle = "Apple launches a new device line"
            )
        ),
        overviewError = null,
        indicatorAlerts = listOf(
            IndicatorAlertSetting(
                metric = IndicatorMetric.RSI_14,
                threshold = 30.0,
                direction = AlertDirection.BELOW,
                enabled = true
            ),
            IndicatorAlertSetting(
                metric = IndicatorMetric.MACD_LINE,
                threshold = 0.0,
                direction = AlertDirection.ABOVE,
                enabled = false
            )
        ),
        openAlerts = openAlerts,
        translationMessage = "Offline translations are ready.",
        showTranslationRetry = true,
        showTranslationPrompt = showTranslationPrompt,
        translationPromptTitle = "Download translation and scoring model",
        translationPromptMessage = "Download the 1B offline model to translate headlines?",
        translationPromptType = TranslationPromptType.LOCAL_MODEL,
        translationDownloadProgress = 55,
        translationDownloadInProgress = showTranslationPrompt,
        offlineTranslationEnabled = true,
        localModelAvailable = true,
        aiGenerationState = AiGenerationState.QUEUED
    )
}
