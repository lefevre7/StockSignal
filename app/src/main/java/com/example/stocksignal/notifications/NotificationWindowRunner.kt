package com.example.stocksignal.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.stocksignal.R
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.model.MarketMoversSnapshot
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result as StooqResult
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertDefaults
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.StockOverview
import com.example.stocksignal.domain.signal.IndicatorAlertEvaluator
import com.example.stocksignal.util.DebugConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationWindowRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketMoversRepository: MarketMoversRepository,
    private val stockRepository: StockRepository,
    private val signalsRepository: SignalsRepository,
    private val notificationQueueProcessor: NotificationQueueProcessor,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) {
    private val overviewCache = mutableMapOf<String, StockOverview?>()

    private data class RunDiagnostics(
        var watchlistTotal: Int = 0,
        var watchlistDisabled: Int = 0,
        var watchlistSnoozed: Int = 0,
        var watchlistFetchFailures: Int = 0,
        var watchlistFallbacks: Int = 0,
        var watchlistLastFetchError: String? = null,
        val watchlistFailedTickers: MutableList<String> = mutableListOf(),
        val watchlistStaleTickers: MutableList<String> = mutableListOf(),
        val watchlistCacheUsedTickers: MutableList<String> = mutableListOf(),
        val watchlistEmptyTickers: MutableList<String> = mutableListOf(),
        val watchlistLastCandleTickers: MutableList<String> = mutableListOf(),
        val watchlistLiveFetchTimes: MutableList<String> = mutableListOf(),
        val watchlistCacheFetchTimes: MutableList<String> = mutableListOf(),
        val watchlistDailyFetchTimes: MutableList<String> = mutableListOf(),
        val watchlistOverviewFetchTimes: MutableList<String> = mutableListOf(),
        val watchlistTimeoutRemaining: MutableList<String> = mutableListOf(),
        var watchlistEmpty: Int = 0,
        var watchlistStale: Int = 0,
        var watchlistSignalNull: Int = 0,
        var watchlistCooldown: Int = 0,
        var watchlistBelowThreshold: Int = 0,
        var watchlistExceptions: Int = 0,
        var aiSkipped: Int = 0,
        val aiSkippedTickers: MutableList<String> = mutableListOf(),
        var aiErrors: Int = 0,
        val aiErrorTickers: MutableList<String> = mutableListOf(),
        var moversListFetchFailures: Int = 0,
        var moversListFallbacks: Int = 0,
        var moversListLastError: String? = null,
        val moversListErrors: MutableList<String> = mutableListOf(),
        var moversListStale: Int = 0,
        var moversTotal: Int = 0,
        var moversFetchFailures: Int = 0,
        var moversFallbacks: Int = 0,
        var moversLastFetchError: String? = null,
        val moversFailedTickers: MutableList<String> = mutableListOf(),
        val moversStaleTickers: MutableList<String> = mutableListOf(),
        val moversCacheUsedTickers: MutableList<String> = mutableListOf(),
        val moversEmptyTickers: MutableList<String> = mutableListOf(),
        val moversLastCandleTickers: MutableList<String> = mutableListOf(),
        val moversLiveFetchTimes: MutableList<String> = mutableListOf(),
        val moversCacheFetchTimes: MutableList<String> = mutableListOf(),
        val moversDailyFetchTimes: MutableList<String> = mutableListOf(),
        val moversOverviewFetchTimes: MutableList<String> = mutableListOf(),
        val moversListFetchTimes: MutableList<String> = mutableListOf(),
        val moversTimeoutRemaining: MutableList<String> = mutableListOf(),
        var moversEmpty: Int = 0,
        var moversStale: Int = 0,
        var moversSignalNull: Int = 0,
        var moversCooldown: Int = 0,
        var moversBelowThreshold: Int = 0,
        var moversExceptions: Int = 0
    )

    private data class SeriesFetchOutcome(
        val series: List<PriceCandle>?,
        val usedFallback: Boolean,
        val errorMessage: String?
    )

    private data class MoversSnapshotOutcome(
        val snapshot: MarketMoversSnapshot?,
        val usedFallback: Boolean,
        val liveErrorMessage: String?
    )

    enum class RunOutcome {
        SUCCESS,
        RETRY,
        FAILURE
    }

    private class FetchTimingTracker {
        var lastFetchEndMillis: Long? = null

        fun waitMillis(startMillis: Long): Long {
            val last = lastFetchEndMillis ?: return 0L
            return (startMillis - last).coerceAtLeast(0L)
        }

        fun markEnd(endMillis: Long) {
            lastFetchEndMillis = endMillis
        }
    }

    private class RequestPacer {
        suspend fun awaitGap(): Long {
            val targetGap = REQUEST_GAP_BASE_MS + Random.nextLong(REQUEST_GAP_JITTER_MS + 1)
            delay(targetGap)
            return targetGap
        }
    }

    suspend fun run(
        windowId: String,
        runAttemptCount: Int = 0,
        allowAiGeneration: Boolean = true
    ): RunOutcome {
        if (DebugConfig.ENABLE_DEV_MODE) {
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "🔔 NotificationWindowRunner STARTED")
            Log.i(TAG, "   Window ID: $windowId")
            Log.i(TAG, "   Time: ${LocalDateTime.now()}")
            Log.i(TAG, "   Run Attempt: $runAttemptCount")
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        } else {
            Log.d(TAG, "NotificationWindowRunner started - window: $windowId, attempt: $runAttemptCount")
        }

        suspend fun recordRun(result: String, reason: String? = null) {
            runCatching { diagnosticsRepository.recordWindowRun(windowId, result, reason) }
        }

        return try {
            val settings = settingsRepository.settingsFlow.first()
            maybeShowDebugStartNotification(windowId, settings)
            val devMode = isDevMode(settings)
            Log.d(TAG, "📋 Settings loaded:")
            Log.d(TAG, "   Frequency: ${settings.frequency}")
            Log.d(TAG, "   NotificationTypes: ${settings.notificationTypes}")
            Log.d(TAG, "   MinScoreForNotify: ${settings.signalSensitivity.minScoreForNotify}")
            
            val watchlistEnabled = settings.notificationTypes.contains(NotificationType.WATCHLIST)
            val moversEnabled = settings.notificationTypes.contains(NotificationType.MARKET_MOVERS)
            if (!watchlistEnabled && !moversEnabled) {
                Log.w(TAG, "⚠️ Skipping window $windowId - no notification sources enabled")
                recordRun("skipped", "no notification sources enabled")
                return RunOutcome.SUCCESS
            }
            if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
                Log.d(TAG, "⏭️ Skipping window $windowId - frequency is only when open")
                recordRun("skipped", "frequency only when open")
                return RunOutcome.SUCCESS
            }

            Log.i(TAG, "▶️ Processing notification window $windowId")
            Log.i(TAG, "   Watchlist: $watchlistEnabled, MarketMovers: $moversEnabled")
            val candidates = mutableListOf<NotificationEvent>()
            val now = LocalDateTime.now()
            val runDeadlineMillis = System.currentTimeMillis() + MAX_WINDOW_RUNTIME_MINUTES * 60_000L
            val timingTracker = FetchTimingTracker()
            val requestPacer = RequestPacer()
            fun hasTimedOut(): Boolean = System.currentTimeMillis() >= runDeadlineMillis
            val premarketWindow = PremarketWindowUtils.resolvePremarketWindow(
                settings,
                windowId,
                ZonedDateTime.now()
            )
            val usePremarketData = premarketWindow?.let { window ->
                val nowInZone = ZonedDateTime.now(PremarketWindowUtils.marketZone(window))
                val isWeekend = nowInZone.dayOfWeek == java.time.DayOfWeek.SATURDAY ||
                    nowInZone.dayOfWeek == java.time.DayOfWeek.SUNDAY
                !isWeekend && !PremarketWindowUtils.isDuringMarketHours(nowInZone)
            } ?: false
            var watchlistCandidates = 0
            var moverCandidates = 0
            val diagnostics = RunDiagnostics()

            val watchlistRange = settings.selectedChartRange
            val moversRange = MarketMoverRange.ONE_DAY
            val moversChartRange = chartRangeForMarketRange(moversRange)
            val skipAiGeneration = !allowAiGeneration

            val watchlist = watchlistRepository.getAll()
            diagnostics.watchlistTotal = watchlist.size
            Log.i(TAG, "📊 Watchlist contains ${watchlist.size} items")

            if (watchlistEnabled) {
                Log.i(TAG, "🔍 Processing watchlist items...")
                if (watchlist.isEmpty()) {
                    Log.w(TAG, "⚠️ No watchlist items to evaluate for window $windowId")
                }
                for ((index, item) in watchlist.withIndex()) {
                    if (hasTimedOut()) {
                        diagnostics.watchlistTimeoutRemaining.addAll(
                            watchlist.drop(index).map { it.symbol }
                        )
                        Log.w(TAG, "Window $windowId timed out; skipping remaining watchlist items")
                        break
                    }
                    Log.d(TAG, "   → Processing: ${item.symbol} (${item.companyName})")
                    try {
                        if (!item.alertEnabled) {
                            Log.d(TAG, "   ⏭️ ${item.symbol} - alerts disabled")
                            diagnostics.watchlistDisabled += 1
                            continue
                        }
                        if (item.snoozedUntil != null && item.snoozedUntil.isAfter(now)) {
                            Log.d(TAG, "   ⏭️ ${item.symbol} - snoozed until ${item.snoozedUntil}")
                            diagnostics.watchlistSnoozed += 1
                            continue
                        }
                        val minScore = item.minScoreForNotify ?: settings.signalSensitivity.minScoreForNotify
                        Log.d(TAG, "   📈 ${item.symbol}: minScore=$minScore, range=$watchlistRange")
                        
                        // NOTE: This call to stockRepository.getSeries() automatically triggers
                        // passive accumulation of intraday data via StockRepository.accumulateIntradayData()
                        // Data is stored in IntradayDataCache for up to 1 year
                        val seriesOutcome = fetchSeriesWithFallback(
                            symbol = item.symbol,
                            range = watchlistRange,
                            usePremarketData = usePremarketData,
                            timingTracker = timingTracker,
                            requestPacer = requestPacer,
                            liveLog = diagnostics.watchlistLiveFetchTimes,
                            cacheLog = diagnostics.watchlistCacheFetchTimes
                        )
                        if (seriesOutcome.series != null) {
                            if (seriesOutcome.usedFallback) {
                                diagnostics.watchlistFallbacks += 1
                                recordTicker(diagnostics.watchlistCacheUsedTickers, item.symbol)
                            }
                            if (seriesOutcome.errorMessage != null) {
                                diagnostics.watchlistFetchFailures += 1
                                diagnostics.watchlistLastFetchError = diagnostics.watchlistLastFetchError
                                    ?: truncateError(seriesOutcome.errorMessage)
                                recordTicker(
                                    diagnostics.watchlistFailedTickers,
                                    "${item.symbol}:${truncateError(seriesOutcome.errorMessage, TICKER_ERROR_MAX)}"
                                )
                            }
                            val series = seriesOutcome.series
                            Log.d(TAG, "   ✓ ${item.symbol}: Got ${series.size} candles")
                            if (series.isEmpty()) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: No intraday candles returned")
                                diagnostics.watchlistEmpty += 1
                                recordTicker(diagnostics.watchlistEmptyTickers, item.symbol)
                                recordTicker(
                                    diagnostics.watchlistLastCandleTickers,
                                    "${item.symbol}:empty"
                                )
                                continue
                            }
                            recordTicker(
                                diagnostics.watchlistLastCandleTickers,
                                "${item.symbol}:${formatCandleTime(series.last().time)}"
                            )
                            if (!isFresh(series, now)) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: Data stale (last candle too old)")
                                diagnostics.watchlistStale += 1
                                recordTicker(diagnostics.watchlistStaleTickers, item.symbol)
                                continue
                            }
                            
                            // If intraday data doesn't have enough candles for signal computation (need 20+),
                            // fall back to daily data which will have more history
                            val signalSeries = if (series.size < MIN_CANDLES_FOR_SIGNAL) {
                                Log.i(TAG, "   🔄 ${item.symbol}: Insufficient candles (${series.size}), fetching daily fallback")
                                when (
                                    val fallback = fetchDailyFallbackWithDiagnostics(
                                        symbol = item.symbol,
                                        range = ChartRange.SIX_MONTH,
                                        timingTracker = timingTracker,
                                        requestPacer = requestPacer,
                                        logList = diagnostics.watchlistDailyFetchTimes
                                    )
                                ) {
                                    is StooqResult.Success -> {
                                        Log.i(TAG, "   ✓ ${item.symbol}: Daily fallback got ${fallback.data.size} candles")
                                        fallback.data
                                    }
                                    is StooqResult.Error -> {
                                        Log.w(TAG, "   ❌ ${item.symbol}: Daily fallback failed, using limited intraday")
                                        series
                                    }
                                }
                            } else {
                                Log.d(TAG, "   ✓ ${item.symbol}: Sufficient candles for signal computation")
                                series
                            }
                            
                            Log.d(TAG, "   🧮 ${item.symbol}: Computing signal with ${signalSeries.size} candles...")
                            val overview = loadOverviewCached(
                                symbol = item.symbol,
                                timingTracker = timingTracker,
                                requestPacer = requestPacer,
                                logList = diagnostics.watchlistOverviewFetchTimes
                            )
                            if (skipAiGeneration) {
                                diagnostics.aiSkipped += 1
                                recordTicker(diagnostics.aiSkippedTickers, item.symbol)
                            }
                            val signal = if (skipAiGeneration) {
                                signalsRepository.computeSignal(
                                    item.symbol,
                                    signalSeries,
                                    watchlistRange,
                                    overview,
                                    skipAiGeneration = true
                                )
                            } else {
                                try {
                                    signalsRepository.computeSignal(
                                        item.symbol,
                                        signalSeries,
                                        watchlistRange,
                                        overview,
                                        skipAiGeneration = false
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "   ❌ ${item.symbol}: AI scoring failed; retrying without AI", e)
                                    diagnostics.aiErrors += 1
                                    recordTicker(
                                        diagnostics.aiErrorTickers,
                                        "${item.symbol}:${truncateError(e.message, TICKER_ERROR_MAX) ?: "unknown"}"
                                    )
                                    signalsRepository.computeSignal(
                                        item.symbol,
                                        signalSeries,
                                        watchlistRange,
                                        overview,
                                        skipAiGeneration = true
                                    )
                                }
                            }
                            if (signal == null) {
                                Log.d(TAG, "   ⚠️ ${item.symbol}: No signal generated")
                                diagnostics.watchlistSignalNull += 1
                                continue
                            }
                            val displayScore = signal.displayScore
                            Log.i(TAG, "   📈 ${item.symbol}: Signal computed - score=$displayScore, tier=${signal.tier.label}")
                            if (displayScore < minScore && displayScore > -minScore) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: Score $displayScore below threshold $minScore")
                                diagnostics.watchlistBelowThreshold += 1
                                continue
                            }
                            if (signalsRepository.isInCooldown(item.symbol, signal.tier.label, signal.generatedAt)) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: Signal ${signal.tier.label} in cooldown")
                                diagnostics.watchlistCooldown += 1
                                continue
                            }
                            val event = buildEvent(
                                signal = signal,
                                ticker = item.symbol,
                                company = item.companyName,
                                price = series.lastOrNull()?.close,
                                percentChange = percentChange(series),
                                type = NotificationEventType.WATCHLIST_SIGNAL
                            )
                            signalsRepository.recordEvent(event)
                            candidates.add(event)
                            watchlistCandidates += 1
                            Log.i(TAG, "   ✅ ${item.symbol}: CANDIDATE ADDED - ${signal.tier.label} score=$displayScore")
                        } else {
                            diagnostics.watchlistFetchFailures += 1
                            diagnostics.watchlistLastFetchError = diagnostics.watchlistLastFetchError
                                ?: truncateError(seriesOutcome.errorMessage) ?: "unknown fetch failure"
                            recordTicker(
                                diagnostics.watchlistFailedTickers,
                                "${item.symbol}:${truncateError(seriesOutcome.errorMessage, TICKER_ERROR_MAX) ?: "unknown"}"
                            )
                            Log.w(TAG, "   ❌ ${item.symbol}: Failed to fetch series - ${seriesOutcome.errorMessage}")
                        }
                        evaluateIndicatorAlerts(
                            item = item,
                            holdingPeriod = settings.holdingPeriod,
                            now = now,
                            candidates = candidates,
                            timingTracker = timingTracker,
                            requestPacer = requestPacer,
                            fetchLog = diagnostics.watchlistLiveFetchTimes,
                            overviewLog = diagnostics.watchlistOverviewFetchTimes
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ ${item.symbol}: Exception during processing", e)
                        diagnostics.watchlistExceptions += 1
                    }
                }
            }

            if (moversEnabled) {
                if (hasTimedOut()) {
                    diagnostics.moversTimeoutRemaining.add("list_fetch")
                    Log.w(TAG, "Window $windowId timed out; skipping market movers list")
                } else {
                    val watchlistSymbols = watchlist.map { it.symbol }.toSet()
                    val increasersOutcome = fetchMarketMoversSnapshot(
                        range = moversRange,
                        direction = MarketMoverDirection.INCREASERS,
                        timingTracker = timingTracker,
                        requestPacer = requestPacer,
                        logList = diagnostics.moversListFetchTimes,
                        labelPrefix = "increasers"
                    )
                    val decreasersOutcome = fetchMarketMoversSnapshot(
                        range = moversRange,
                        direction = MarketMoverDirection.DECREASERS,
                        timingTracker = timingTracker,
                        requestPacer = requestPacer,
                        logList = diagnostics.moversListFetchTimes,
                        labelPrefix = "decreasers"
                    )

                    if (increasersOutcome.liveErrorMessage != null) {
                        diagnostics.moversListFetchFailures += 1
                        diagnostics.moversListLastError = diagnostics.moversListLastError
                            ?: truncateError(increasersOutcome.liveErrorMessage)
                        recordTicker(
                            diagnostics.moversListErrors,
                            "increasers:${truncateError(increasersOutcome.liveErrorMessage, TICKER_ERROR_MAX)}"
                        )
                    }
                    if (decreasersOutcome.liveErrorMessage != null) {
                        diagnostics.moversListFetchFailures += 1
                        diagnostics.moversListLastError = diagnostics.moversListLastError
                            ?: truncateError(decreasersOutcome.liveErrorMessage)
                        recordTicker(
                            diagnostics.moversListErrors,
                            "decreasers:${truncateError(decreasersOutcome.liveErrorMessage, TICKER_ERROR_MAX)}"
                        )
                    }

                    if (increasersOutcome.usedFallback) diagnostics.moversListFallbacks += 1
                    if (decreasersOutcome.usedFallback) diagnostics.moversListFallbacks += 1

                    val increasersSnapshot = increasersOutcome.snapshot
                    val decreasersSnapshot = decreasersOutcome.snapshot
                    if (increasersSnapshot?.isStale == true) {
                        Log.d(TAG, "Market movers increasers snapshot stale; using cached list")
                        diagnostics.moversListStale += 1
                    }
                    if (decreasersSnapshot?.isStale == true) {
                        Log.d(TAG, "Market movers decreasers snapshot stale; using cached list")
                        diagnostics.moversListStale += 1
                    }
                    val increasers = increasersSnapshot?.items.orEmpty()
                    val decreasers = decreasersSnapshot?.items.orEmpty()
                    val movers = (increasers.take(MAX_MOVERS) + decreasers.take(MAX_MOVERS))
                        .distinctBy { it.ticker }
                        .filterNot { watchlistSymbols.contains(it.ticker) }
                    diagnostics.moversTotal = movers.size

                    if (movers.isEmpty()) {
                        Log.d(TAG, "No market movers candidates to evaluate for window $windowId")
                    }

                    movers.forEachIndexed { index, mover ->
                        if (hasTimedOut()) {
                            diagnostics.moversTimeoutRemaining.addAll(
                                movers.drop(index).map { it.ticker }
                            )
                            Log.w(TAG, "Window $windowId timed out; skipping remaining movers")
                            return@forEachIndexed
                        }
                        try {
                            val seriesOutcome = fetchSeriesWithFallback(
                                symbol = mover.ticker,
                                range = moversChartRange,
                                usePremarketData = false,
                                timingTracker = timingTracker,
                                requestPacer = requestPacer,
                                liveLog = diagnostics.moversLiveFetchTimes,
                                cacheLog = diagnostics.moversCacheFetchTimes
                            )
                            if (seriesOutcome.series != null) {
                                if (seriesOutcome.usedFallback) {
                                    diagnostics.moversFallbacks += 1
                                    recordTicker(diagnostics.moversCacheUsedTickers, mover.ticker)
                                }
                                if (seriesOutcome.errorMessage != null) {
                                    diagnostics.moversFetchFailures += 1
                                    diagnostics.moversLastFetchError = diagnostics.moversLastFetchError
                                        ?: truncateError(seriesOutcome.errorMessage)
                                    recordTicker(
                                        diagnostics.moversFailedTickers,
                                        "${mover.ticker}:${truncateError(seriesOutcome.errorMessage, TICKER_ERROR_MAX)}"
                                    )
                                }
                                val series = seriesOutcome.series
                                if (series.isEmpty()) {
                                    Log.d(TAG, "Market mover ${mover.ticker} no intraday candles returned; skipping")
                                    diagnostics.moversEmpty += 1
                                    recordTicker(diagnostics.moversEmptyTickers, mover.ticker)
                                    recordTicker(
                                        diagnostics.moversLastCandleTickers,
                                        "${mover.ticker}:empty"
                                    )
                                    return@forEachIndexed
                                }
                                recordTicker(
                                    diagnostics.moversLastCandleTickers,
                                    "${mover.ticker}:${formatCandleTime(series.last().time)}"
                                )
                                if (!isFresh(series, now)) {
                                    Log.d(TAG, "Market mover ${mover.ticker} data stale; skipping")
                                    diagnostics.moversStale += 1
                                    recordTicker(diagnostics.moversStaleTickers, mover.ticker)
                                    return@forEachIndexed
                                }

                                // If intraday data doesn't have enough candles for signal computation (need 20+),
                                // fall back to daily data which will have more history
                                val signalSeries = if (series.size < MIN_CANDLES_FOR_SIGNAL) {
                                    Log.d(TAG, "Market mover ${mover.ticker}: insufficient candles (${series.size}), fetching daily fallback")
                                    when (
                                        val fallback = fetchDailyFallbackWithDiagnostics(
                                            symbol = mover.ticker,
                                            range = ChartRange.SIX_MONTH,
                                            timingTracker = timingTracker,
                                            requestPacer = requestPacer,
                                            logList = diagnostics.moversDailyFetchTimes
                                        )
                                    ) {
                                        is StooqResult.Success -> fallback.data
                                        is StooqResult.Error -> {
                                            Log.w(TAG, "Daily fallback failed for ${mover.ticker}, using limited intraday")
                                            series
                                        }
                                    }
                                } else {
                                    series
                                }

                                val overview = loadOverviewCached(
                                    symbol = mover.ticker,
                                    timingTracker = timingTracker,
                                    requestPacer = requestPacer,
                                    logList = diagnostics.moversOverviewFetchTimes
                                )
                                if (skipAiGeneration) {
                                    diagnostics.aiSkipped += 1
                                    recordTicker(diagnostics.aiSkippedTickers, mover.ticker)
                                }
                                val signal = if (skipAiGeneration) {
                                    signalsRepository.computeSignal(
                                        mover.ticker,
                                        signalSeries,
                                        moversChartRange,
                                        overview,
                                        skipAiGeneration = true
                                    )
                                } else {
                                    try {
                                        signalsRepository.computeSignal(
                                            mover.ticker,
                                            signalSeries,
                                            moversChartRange,
                                            overview,
                                            skipAiGeneration = false
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Market mover ${mover.ticker} AI scoring failed; retrying without AI", e)
                                        diagnostics.aiErrors += 1
                                        recordTicker(
                                            diagnostics.aiErrorTickers,
                                            "${mover.ticker}:${truncateError(e.message, TICKER_ERROR_MAX) ?: "unknown"}"
                                        )
                                        signalsRepository.computeSignal(
                                            mover.ticker,
                                            signalSeries,
                                            moversChartRange,
                                            overview,
                                            skipAiGeneration = true
                                        )
                                    }
                                }
                                if (signal == null) {
                                    Log.d(TAG, "Market mover ${mover.ticker} no signal generated")
                                    diagnostics.moversSignalNull += 1
                                    return@forEachIndexed
                                }
                                val displayScore = signal.displayScore
                                val strongBuy = settings.signalSensitivity.strongBuyThreshold
                                val strongSell = settings.signalSensitivity.strongSellThreshold
                                if (displayScore < strongBuy && displayScore > strongSell) {
                                    Log.d(TAG, "Market mover ${mover.ticker} score $displayScore below thresholds")
                                    diagnostics.moversBelowThreshold += 1
                                    return@forEachIndexed
                                }
                                if (signalsRepository.isInCooldown(mover.ticker, signal.tier.label, signal.generatedAt)) {
                                    Log.d(TAG, "Market mover ${mover.ticker} signal ${signal.tier.label} in cooldown")
                                    diagnostics.moversCooldown += 1
                                    return@forEachIndexed
                                }
                                val event = buildEvent(
                                    signal = signal,
                                    ticker = mover.ticker,
                                    company = mover.companyName,
                                    price = mover.price,
                                    percentChange = mover.percentChange,
                                    type = NotificationEventType.MARKET_MOVER
                                )
                                signalsRepository.recordEvent(event)
                                candidates.add(event)
                                moverCandidates += 1
                                Log.d(TAG, "Market mover candidate ${mover.ticker} ${signal.tier.label} score=$displayScore")
                            } else {
                                diagnostics.moversFetchFailures += 1
                                diagnostics.moversLastFetchError = diagnostics.moversLastFetchError
                                    ?: truncateError(seriesOutcome.errorMessage) ?: "unknown fetch failure"
                                recordTicker(
                                    diagnostics.moversFailedTickers,
                                    "${mover.ticker}:${truncateError(seriesOutcome.errorMessage, TICKER_ERROR_MAX) ?: "unknown"}"
                                )
                                Log.w(TAG, "Market mover ${mover.ticker} failed to fetch series; skipping (${seriesOutcome.errorMessage})")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error processing market mover ${mover.ticker}, skipping", e)
                            diagnostics.moversExceptions += 1
                        }
                    }
                }
            }

            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "📊 PROCESSING SUMMARY for window $windowId")
            Log.i(TAG, "   Watchlist candidates: $watchlistCandidates")
            Log.i(TAG, "   Market mover candidates: $moverCandidates")
            Log.i(TAG, "   Total candidates: ${candidates.size}")
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            
            if (candidates.isEmpty()) {
                Log.i(TAG, "📭 No candidates generated, processing queued events...")
                notificationQueueProcessor.processQueued(settings)
                recordRun(
                    "success",
                    buildRunReason(
                        diagnostics,
                        candidates.size,
                        watchlistCandidates,
                        moverCandidates
                    )
                )
            } else {
                Log.i(TAG, "📬 Processing ${candidates.size} candidate(s)...")
                candidates.forEachIndexed { idx, event ->
                    Log.i(TAG, "   ${idx + 1}. ${event.ticker}: ${event.tier.label} (score=${event.score})")
                }
                notificationQueueProcessor.processCandidates(candidates, settings)
                recordRun(
                    "success",
                    buildRunReason(
                        diagnostics,
                        candidates.size,
                        watchlistCandidates,
                        moverCandidates
                    )
                )
            }
            
            if (DebugConfig.ENABLE_DEV_MODE) {
                Log.i(TAG, "═══════════════════════════════════════════════════════════════")
                Log.i(TAG, "✅ NotificationWindowRunner COMPLETED SUCCESSFULLY")
                Log.i(TAG, "   Window: $windowId")
                Log.i(TAG, "   Time: ${LocalDateTime.now()}")
                Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            } else {
                Log.d(TAG, "NotificationWindowRunner completed successfully - window: $windowId")
            }
            RunOutcome.SUCCESS
        } catch (e: java.io.IOException) {
            Log.e(TAG, "❌ Network error in window $windowId, will retry", e)
            recordRun("retry", "network error: ${e.message}")
            RunOutcome.RETRY
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "❌ Database error in window $windowId, will retry", e)
            recordRun("retry", "database error: ${e.message}")
            RunOutcome.RETRY
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "⚠️ Runner cancelled for window $windowId", e)
            recordRun("cancelled", "cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error in window $windowId, will not retry", e)
            recordRun("failure", "unexpected error: ${e.message}")
            RunOutcome.FAILURE
        }
    }

    private fun buildEvent(
        signal: SignalResult,
        ticker: String,
        company: String?,
        price: Double?,
        percentChange: Double?,
        type: NotificationEventType
    ): NotificationEvent {
        val eventId = signalEventId(ticker, signal.generatedAt)
        return NotificationEvent(
            id = eventId,
            type = type,
            ticker = ticker,
            companyName = company,
            score = signal.score,
            averageScore = signal.averageScore,
            modeScore = signal.modeScore,
            confidence = signal.confidence,
            aiScore = signal.aiScore,
            aiConfidence = signal.aiConfidence,
            aiSummary = signal.aiSummary,
            aiReasons = signal.aiReasons,
            price = price,
            percentChange = percentChange,
            generatedAt = signal.generatedAt,
            notifiedAt = null,
            deepLink = "stocksignal://stock/$ticker",
            source = "local",
            delivered = false,
            reasons = signal.reasons
        )
    }

    private suspend fun fetchSeriesWithFallback(
        symbol: String,
        range: ChartRange,
        usePremarketData: Boolean,
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        liveLog: MutableList<String>,
        cacheLog: MutableList<String>
    ): SeriesFetchOutcome {
        val liveResult = fetchLiveSeriesWithRetries(
            symbol = symbol,
            range = range,
            usePremarketData = usePremarketData,
            timingTracker = timingTracker,
            requestPacer = requestPacer,
            logList = liveLog
        )
        if (liveResult is StooqResult.Success) {
            return SeriesFetchOutcome(liveResult.data, usedFallback = false, errorMessage = null)
        }
        val liveError = (liveResult as? StooqResult.Error)?.message

        val cacheResult = recordFetch(
            timingTracker = timingTracker,
            logList = cacheLog,
            label = "${symbol}#cache"
        ) {
            stockRepository.getFreshCachedSeries(symbol, range)
        }
        if (cacheResult is StooqResult.Success) {
            return SeriesFetchOutcome(cacheResult.data, usedFallback = true, errorMessage = liveError)
        }
        val cacheError = (cacheResult as? StooqResult.Error)?.message
        val combinedError = listOfNotNull(
            liveError?.let { "live=$it" },
            cacheError?.let { "cache=$it" }
        ).joinToString("; ")
        val errorMessage = when {
            combinedError.isNotBlank() -> combinedError
            liveError != null -> liveError
            cacheError != null -> cacheError
            else -> null
        }
        return SeriesFetchOutcome(
            series = null,
            usedFallback = true,
            errorMessage = errorMessage
        )
    }

    private suspend fun fetchMarketMoversSnapshot(
        range: MarketMoverRange,
        direction: MarketMoverDirection,
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        logList: MutableList<String>,
        labelPrefix: String
    ): MoversSnapshotOutcome {
        val liveResult = recordNetworkFetch(
            timingTracker = timingTracker,
            requestPacer = requestPacer,
            logList = logList,
            label = "${labelPrefix}#live"
        ) {
            marketMoversRepository.getMarketMovers(
                range = range,
                direction = direction,
                forceRefresh = true
            )
        }
        if (liveResult is StooqResult.Success && !liveResult.data.isStale) {
            return MoversSnapshotOutcome(liveResult.data, usedFallback = false, liveErrorMessage = null)
        }
        val liveError = when (liveResult) {
            is StooqResult.Error -> liveResult.message
            is StooqResult.Success -> "live list stale"
        }

        val cachedResult = recordFetch(
            timingTracker = timingTracker,
            logList = logList,
            label = "${labelPrefix}#cache"
        ) {
            marketMoversRepository.getFreshCachedMovers(range, direction)
        }
        if (cachedResult is StooqResult.Success) {
            return MoversSnapshotOutcome(cachedResult.data, usedFallback = true, liveErrorMessage = liveError)
        }

        val staleSnapshot = (liveResult as? StooqResult.Success)?.data
        val cacheError = (cachedResult as? StooqResult.Error)?.message
        val combinedError = listOfNotNull(
            liveError?.let { "live=$it" },
            cacheError?.let { "cache=$it" }
        ).joinToString("; ")
        val errorMessage = if (combinedError.isNotBlank()) combinedError else liveError ?: cacheError
        return MoversSnapshotOutcome(staleSnapshot, usedFallback = true, liveErrorMessage = errorMessage)
    }

    private fun buildRunReason(
        diagnostics: RunDiagnostics,
        totalCandidates: Int,
        watchlistCandidates: Int,
        moverCandidates: Int
    ): String {
        val watchlistEnabled = (diagnostics.watchlistTotal -
            diagnostics.watchlistDisabled -
            diagnostics.watchlistSnoozed).coerceAtLeast(0)
        val watchlistError = truncateError(diagnostics.watchlistLastFetchError)
        val moversListError = truncateError(diagnostics.moversListLastError)
        val moversError = truncateError(diagnostics.moversLastFetchError)
        val watchlistLiveFailed = diagnostics.watchlistFetchFailures > 0
        val moversLiveFailed = diagnostics.moversFetchFailures > 0
        val moversListLiveFailed = diagnostics.moversListFetchFailures > 0
        val watchlistFailed = formatTickerList("watchlist_failed", diagnostics.watchlistFailedTickers)
        val watchlistStale = formatTickerList("watchlist_stale", diagnostics.watchlistStaleTickers)
        val watchlistCache = formatTickerList("watchlist_cache", diagnostics.watchlistCacheUsedTickers)
        val watchlistEmpty = formatTickerList("watchlist_empty", diagnostics.watchlistEmptyTickers)
        val watchlistLast = formatTickerList("watchlist_last", diagnostics.watchlistLastCandleTickers)
        val watchlistLiveFetch = formatTickerList("watchlist_live_fetch", diagnostics.watchlistLiveFetchTimes)
        val watchlistCacheFetch = formatTickerList("watchlist_cache_fetch", diagnostics.watchlistCacheFetchTimes)
        val watchlistDailyFetch = formatTickerList("watchlist_daily_fetch", diagnostics.watchlistDailyFetchTimes)
        val watchlistOverviewFetch = formatTickerList("watchlist_overview_fetch", diagnostics.watchlistOverviewFetchTimes)
        val watchlistTimeout = formatTickerList("watchlist_timeout_remaining", diagnostics.watchlistTimeoutRemaining)
        val moversFailed = formatTickerList("movers_failed", diagnostics.moversFailedTickers)
        val moversStale = formatTickerList("movers_stale", diagnostics.moversStaleTickers)
        val moversCache = formatTickerList("movers_cache", diagnostics.moversCacheUsedTickers)
        val moversEmpty = formatTickerList("movers_empty", diagnostics.moversEmptyTickers)
        val moversLast = formatTickerList("movers_last", diagnostics.moversLastCandleTickers)
        val aiSkipped = formatTickerList("ai_skipped", diagnostics.aiSkippedTickers)
        val aiErrors = formatTickerList("ai_errors", diagnostics.aiErrorTickers)
        val moversListErrors = formatTickerList("movers_list_errors", diagnostics.moversListErrors)
        val moversLiveFetch = formatTickerList("movers_live_fetch", diagnostics.moversLiveFetchTimes)
        val moversCacheFetch = formatTickerList("movers_cache_fetch", diagnostics.moversCacheFetchTimes)
        val moversDailyFetch = formatTickerList("movers_daily_fetch", diagnostics.moversDailyFetchTimes)
        val moversOverviewFetch = formatTickerList("movers_overview_fetch", diagnostics.moversOverviewFetchTimes)
        val moversListFetch = formatTickerList("movers_list_fetch", diagnostics.moversListFetchTimes)
        val moversTimeout = formatTickerList("movers_timeout_remaining", diagnostics.moversTimeoutRemaining)
        val timedOut = diagnostics.watchlistTimeoutRemaining.isNotEmpty() || diagnostics.moversTimeoutRemaining.isNotEmpty()

        return "candidates=$totalCandidates (watchlist=$watchlistCandidates movers=$moverCandidates) | " +
            "ai_skipped=${diagnostics.aiSkipped} ai_errors=${diagnostics.aiErrors} " +
            "watchlist: total=${diagnostics.watchlistTotal} enabled=$watchlistEnabled " +
            "disabled=${diagnostics.watchlistDisabled} snoozed=${diagnostics.watchlistSnoozed} " +
            "fetch_fail=${diagnostics.watchlistFetchFailures} cache_used=${diagnostics.watchlistFallbacks} " +
            "live_failed=$watchlistLiveFailed " +
            "empty=${diagnostics.watchlistEmpty} stale=${diagnostics.watchlistStale} " +
            "signal_null=${diagnostics.watchlistSignalNull} " +
            "cooldown=${diagnostics.watchlistCooldown} below=${diagnostics.watchlistBelowThreshold} " +
            "exceptions=${diagnostics.watchlistExceptions} timeout=$timedOut " +
            (watchlistError?.let { "last_error=$it " } ?: "") +
            (watchlistFailed?.let { "$it " } ?: "") +
            (watchlistStale?.let { "$it " } ?: "") +
            (watchlistCache?.let { "$it " } ?: "") +
            (watchlistEmpty?.let { "$it " } ?: "") +
            (watchlistLast?.let { "$it " } ?: "") +
            (watchlistLiveFetch?.let { "$it " } ?: "") +
            (watchlistCacheFetch?.let { "$it " } ?: "") +
            (watchlistDailyFetch?.let { "$it " } ?: "") +
            (watchlistOverviewFetch?.let { "$it " } ?: "") +
            (watchlistTimeout?.let { "$it " } ?: "") +
            (aiSkipped?.let { "$it " } ?: "") +
            (aiErrors?.let { "$it " } ?: "") +
            "| movers: list_fail=${diagnostics.moversListFetchFailures} " +
            "list_cache_used=${diagnostics.moversListFallbacks} list_stale=${diagnostics.moversListStale} " +
            "list_live_failed=$moversListLiveFailed " +
            "total=${diagnostics.moversTotal} fetch_fail=${diagnostics.moversFetchFailures} " +
            "cache_used=${diagnostics.moversFallbacks} empty=${diagnostics.moversEmpty} " +
            "stale=${diagnostics.moversStale} " +
            "live_failed=$moversLiveFailed " +
            "signal_null=${diagnostics.moversSignalNull} cooldown=${diagnostics.moversCooldown} " +
            "below=${diagnostics.moversBelowThreshold} exceptions=${diagnostics.moversExceptions} " +
            (moversListError?.let { "list_error=$it " } ?: "") +
            (moversError?.let { "last_error=$it " } ?: "") +
            (moversListErrors?.let { "$it " } ?: "") +
            (moversFailed?.let { "$it " } ?: "") +
            (moversStale?.let { "$it " } ?: "") +
            (moversCache?.let { "$it " } ?: "") +
            (moversEmpty?.let { "$it " } ?: "") +
            (moversLast?.let { "$it " } ?: "") +
            (moversLiveFetch?.let { "$it " } ?: "") +
            (moversCacheFetch?.let { "$it " } ?: "") +
            (moversDailyFetch?.let { "$it " } ?: "") +
            (moversOverviewFetch?.let { "$it " } ?: "") +
            (moversListFetch?.let { "$it " } ?: "") +
            (moversTimeout?.let { "$it" } ?: "")
    }

    private fun truncateError(message: String?, maxLength: Int = 120): String? {
        if (message.isNullOrBlank()) return null
        return message
    }

    private fun formatTickerList(label: String, items: List<String>): String? {
        if (items.isEmpty()) return null
        return "$label=[${items.joinToString(",")}]"
    }

    private fun recordTicker(list: MutableList<String>, entry: String) {
        list.add(entry)
    }

    private suspend fun <T> recordFetch(
        timingTracker: FetchTimingTracker,
        logList: MutableList<String>,
        label: String,
        waitOverrideMs: Long? = null,
        block: suspend () -> T
    ): T {
        val startMillis = System.currentTimeMillis()
        val waitMillis = waitOverrideMs ?: timingTracker.waitMillis(startMillis)
        return try {
            block()
        } finally {
            val endMillis = System.currentTimeMillis()
            timingTracker.markEnd(endMillis)
            val entry = buildString {
                append(label)
                append(":")
                append(formatFetchTime(startMillis))
                append("-")
                append(formatFetchTime(endMillis))
                append(" wait=")
                append(waitMillis)
                append("ms")
            }
            logList.add(entry)
        }
    }

    private suspend fun <T> recordNetworkFetch(
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        logList: MutableList<String>,
        label: String,
        block: suspend () -> T
    ): T {
        val pacerWait = requestPacer.awaitGap()
        return recordFetch(
            timingTracker = timingTracker,
            logList = logList,
            label = label,
            waitOverrideMs = pacerWait,
            block = block
        )
    }

    private fun formatFetchTime(epochMillis: Long): String {
        val localTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return localTime.format(FETCH_TIME_FORMATTER)
    }

    private fun formatCandleTime(time: LocalDateTime): String {
        return try {
            time.format(CANDLE_TIME_FORMATTER)
        } catch (_: Exception) {
            time.toString()
        }
    }

    private suspend fun fetchLiveSeriesWithRetries(
        symbol: String,
        range: ChartRange,
        usePremarketData: Boolean,
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        logList: MutableList<String>
    ): StooqResult<List<PriceCandle>> {
        var lastError: String? = null
        repeat(LIVE_RETRY_COUNT + 1) { attempt ->
            val result = recordNetworkFetch(
                timingTracker = timingTracker,
                requestPacer = requestPacer,
                logList = logList,
                label = "${symbol}#${attempt + 1}"
            ) {
                if (usePremarketData) {
                    stockRepository.getSeriesForPremarket(symbol, range, eventType = null)
                } else {
                    stockRepository.getSeries(symbol, range, forceRefresh = true, eventType = null)
                }
            }
            if (result is StooqResult.Success) return result
            lastError = (result as? StooqResult.Error)?.message ?: "live fetch failed"
            if (attempt < LIVE_RETRY_COUNT) {
                delay(LIVE_RETRY_DELAY_MS)
            }
        }
        return StooqResult.Error(Exception(lastError ?: "live fetch failed"), lastError ?: "live fetch failed")
    }

    private suspend fun fetchDailyFallbackWithDiagnostics(
        symbol: String,
        range: ChartRange,
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        logList: MutableList<String>
    ): StooqResult<List<PriceCandle>> {
        return recordNetworkFetch(
            timingTracker = timingTracker,
            requestPacer = requestPacer,
            logList = logList,
            label = "${symbol}#daily"
        ) {
            stockRepository.getDailySeriesFallback(symbol, range)
        }
    }

    private suspend fun evaluateIndicatorAlerts(
        item: WatchlistItemEntity,
        holdingPeriod: com.example.stocksignal.data.settings.HoldingPeriod,
        now: LocalDateTime,
        candidates: MutableList<NotificationEvent>,
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        fetchLog: MutableList<String>,
        overviewLog: MutableList<String>
    ) {
        val alerts = IndicatorAlertJson.fromJson(item.indicatorAlertsJson).filter { it.enabled }
        if (alerts.isEmpty()) return

        val alertsByRange = alerts.groupBy { it.metric.defaultRange }
        for ((range, rangeAlerts) in alertsByRange) {
            val result = recordNetworkFetch(
                timingTracker = timingTracker,
                requestPacer = requestPacer,
                logList = fetchLog,
                label = "${item.symbol}#alert_${range.name}"
            ) {
                stockRepository.getSeries(
                    item.symbol,
                    range,
                    forceRefresh = true,
                    eventType = null
                )
            }
            if (result is StooqResult.Success) {
                val series = result.data
                if (series.isEmpty()) {
                    Log.d(TAG, "Indicator alerts: ${item.symbol} no data for range $range")
                    continue
                }
                if (!isFresh(series, now)) {
                    Log.d(TAG, "Indicator alerts: ${item.symbol} data stale for range $range")
                    continue
                }
                val overview = loadOverviewCached(
                    symbol = item.symbol,
                    timingTracker = timingTracker,
                    requestPacer = requestPacer,
                    logList = overviewLog
                )
                val signal = signalsRepository.computeSignal(item.symbol, series, range, overview)
                for (alert in rangeAlerts) {
                    val evaluation = IndicatorAlertEvaluator.evaluate(alert, series, holdingPeriod) ?: continue
                    if (!evaluation.crossed) continue
                    val label = indicatorLabel(alert)
                    if (signalsRepository.isInCooldown(item.symbol, label, now)) {
                        Log.d(TAG, "Indicator alert ${item.symbol} ${alert.metric.name} in cooldown")
                        continue
                    }
                    val event = buildIndicatorEvent(
                        ticker = item.symbol,
                        company = item.companyName,
                        series = series,
                        alert = alert,
                        evaluation = evaluation,
                        signal = signal,
                        generatedAt = now
                    )
                    signalsRepository.recordIndicatorEvent(event, label)
                    candidates.add(event)
                    Log.d(TAG, "Indicator alert candidate ${item.symbol} ${alert.metric.label} ${alert.direction.name}")
                }
            }
        }
    }

    private fun buildIndicatorEvent(
        ticker: String,
        company: String?,
        series: List<PriceCandle>,
        alert: IndicatorAlertSetting,
        evaluation: IndicatorAlertEvaluator.Evaluation,
        signal: SignalResult?,
        generatedAt: LocalDateTime
    ): NotificationEvent {
        val reason = indicatorReason(alert, evaluation)
        val reasons = listOf(reason)
        return NotificationEvent(
            id = indicatorEventId(ticker, alert, generatedAt),
            type = NotificationEventType.WATCHLIST_SIGNAL,
            ticker = ticker,
            companyName = company,
            score = signal?.score ?: 0,
            averageScore = signal?.averageScore,
            modeScore = signal?.modeScore,
            confidence = signal?.confidence ?: 0,
            aiScore = signal?.aiScore,
            aiConfidence = signal?.aiConfidence,
            aiSummary = signal?.aiSummary,
            aiReasons = signal?.aiReasons ?: emptyList(),
            price = series.lastOrNull()?.close,
            percentChange = percentChange(series),
            generatedAt = generatedAt,
            notifiedAt = null,
            deepLink = "stocksignal://stock/$ticker",
            source = "local",
            delivered = false,
            reasons = reasons
        )
    }

    private fun indicatorReason(
        alert: IndicatorAlertSetting,
        evaluation: IndicatorAlertEvaluator.Evaluation
    ): SignalReason {
        val direction = if (alert.direction == com.example.stocksignal.domain.model.AlertDirection.ABOVE) {
            "above"
        } else {
            "below"
        }
        val threshold = IndicatorAlertDefaults.formatValue(alert.threshold)
        val current = IndicatorAlertDefaults.formatValue(evaluation.current)
        val title = "${alert.metric.label} crossed $direction $threshold (now $current)"
        val explanation = "Alert when ${alert.metric.label} is $direction $threshold. Current value $current."
        return SignalReason(
            id = "indicator_${alert.metric.name}",
            title = title,
            explanation = explanation,
            impactScore = 0,
            model = "indicator"
        )
    }

    private fun indicatorLabel(alert: IndicatorAlertSetting): String {
        val threshold = IndicatorAlertDefaults.formatValue(alert.threshold).replace('.', '_')
        return "indicator_${alert.metric.name}_${alert.direction.name}_$threshold"
    }

    private fun indicatorEventId(
        ticker: String,
        alert: IndicatorAlertSetting,
        generatedAt: LocalDateTime
    ): String {
        return "ind_${ticker}_${alert.metric.name}_${generatedAt.toString().replace(':', '_')}"
    }

    private fun signalEventId(ticker: String, generatedAt: LocalDateTime): String {
        return "sig_${ticker}_${generatedAt.toString().replace(':', '_')}"
    }

    private fun chartRangeForMarketRange(range: MarketMoverRange): ChartRange {
        return when (range) {
            MarketMoverRange.ONE_DAY -> ChartRange.ONE_DAY
            MarketMoverRange.FIVE_DAY -> ChartRange.FIVE_DAY
            MarketMoverRange.ONE_MONTH -> ChartRange.ONE_MONTH
            MarketMoverRange.SIX_MONTH -> ChartRange.SIX_MONTH
            MarketMoverRange.ONE_YEAR -> ChartRange.ONE_YEAR
            MarketMoverRange.FIVE_YEAR -> ChartRange.FIVE_YEAR
        }
    }

    private suspend fun loadOverviewCached(
        symbol: String,
        timingTracker: FetchTimingTracker,
        requestPacer: RequestPacer,
        logList: MutableList<String>
    ): StockOverview? {
        val cached = overviewCache[symbol]
        if (cached != null || overviewCache.containsKey(symbol)) {
            return cached
        }
        val overview = recordNetworkFetch(
            timingTracker = timingTracker,
            requestPacer = requestPacer,
            logList = logList,
            label = "${symbol}#overview"
        ) {
            when (val result = stockRepository.getStockOverview(symbol)) {
                is StooqResult.Success -> result.data
                is StooqResult.Error -> null
            }
        }
        overviewCache[symbol] = overview
        return overview
    }

    private fun percentChange(candles: List<PriceCandle>): Double? {
        if (candles.isEmpty()) return null
        val first = candles.first().open
        val last = candles.last().close
        if (first == 0.0) return null
        return ((last - first) / first) * 100.0
    }

    private fun isFresh(candles: List<PriceCandle>, now: LocalDateTime): Boolean {
        val last = candles.lastOrNull()?.time ?: return false
        val age = Duration.between(last, now)
        return !age.isNegative && age <= STALE_THRESHOLD
    }

    private fun isDevMode(settings: AppSettings): Boolean {
        return DebugConfig.ENABLE_DEV_MODE &&
            settings.frequency == NotificationFrequency.DEV_FIVE_MINUTES
    }

    private fun maybeShowDebugStartNotification(windowId: String, settings: AppSettings) {
        if (!isDevMode(settings)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            "Debug worker",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Debug: Notification window")
            .setContentText("Run started (window=$windowId)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(debugNotificationId(windowId), notification)
    }

    private fun debugNotificationId(windowId: String): Int {
        return DEBUG_NOTIFICATION_ID_BASE + (windowId.hashCode() and 0x0FFF)
    }

    companion object {
        private const val TAG = "NotificationWindowRunner"
        private const val MAX_MOVERS = 3
        private const val MIN_CANDLES_FOR_SIGNAL = 20
        private val STALE_THRESHOLD = Duration.ofDays(7)
        private const val MAX_WINDOW_RUNTIME_MINUTES = 10L
        private const val DEBUG_CHANNEL_ID = "debug_worker"
        private const val DEBUG_NOTIFICATION_ID_BASE = 9100
        private const val LIVE_RETRY_COUNT = 2
        private const val LIVE_RETRY_DELAY_MS = 250L
        private const val TICKER_ERROR_MAX = 40
        private const val REQUEST_GAP_BASE_MS = 3000L
        private const val REQUEST_GAP_JITTER_MS = 2000L
        private val CANDLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val FETCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
