package com.example.stocksignal.notifications

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.stocksignal.R
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

@HiltWorker
class NotificationWindowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketMoversRepository: MarketMoversRepository,
    private val stockRepository: StockRepository,
    private val signalsRepository: SignalsRepository,
    private val notificationQueueProcessor: NotificationQueueProcessor,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) : CoroutineWorker(context, params) {
    private val overviewCache = mutableMapOf<String, StockOverview?>()

    override suspend fun doWork(): Result {
        val windowId = inputData.getString(KEY_WINDOW_ID)
        if (DebugConfig.ENABLE_DEV_MODE) {
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            Log.i(TAG, "🔔 NotificationWindowWorker STARTED")
            Log.i(TAG, "   Window ID: $windowId")
            Log.i(TAG, "   Time: ${LocalDateTime.now()}")
            Log.i(TAG, "   Run Attempt: $runAttemptCount")
            Log.i(TAG, "═══════════════════════════════════════════════════════════════")
        } else {
            Log.d(TAG, "NotificationWindowWorker started - window: $windowId, attempt: $runAttemptCount")
        }
        
        if (windowId == null) {
            Log.e(TAG, "❌ Missing window ID in worker input")
            return Result.failure()
        }

        var scheduleNextDev = false
        suspend fun recordRun(result: String, reason: String? = null) {
            runCatching { diagnosticsRepository.recordWindowRun(windowId, result, reason) }
        }

        val result = try {
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
                scheduleNextDev = devMode
                return Result.success()
            }
            if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
                Log.d(TAG, "⏭️ Skipping window $windowId - frequency is only when open")
                recordRun("skipped", "frequency only when open")
                scheduleNextDev = devMode
                return Result.success()
            }

            Log.i(TAG, "▶️ Processing notification window $windowId")
            Log.i(TAG, "   Watchlist: $watchlistEnabled, MarketMovers: $moversEnabled")
            val candidates = mutableListOf<NotificationEvent>()
            val now = LocalDateTime.now()
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

            val watchlistRange = settings.selectedChartRange
            val moversRange = MarketMoverRange.ONE_DAY
            val moversChartRange = chartRangeForMarketRange(moversRange)

            val watchlist = watchlistRepository.getAll()
            Log.i(TAG, "📊 Watchlist contains ${watchlist.size} items")

            if (watchlistEnabled) {
                Log.i(TAG, "🔍 Processing watchlist items...")
                if (watchlist.isEmpty()) {
                    Log.w(TAG, "⚠️ No watchlist items to evaluate for window $windowId")
                }
                for (item in watchlist) {
                    Log.d(TAG, "   → Processing: ${item.symbol} (${item.companyName})")
                    try {
                        if (!item.alertEnabled) {
                            Log.d(TAG, "   ⏭️ ${item.symbol} - alerts disabled")
                            continue
                        }
                        if (item.snoozedUntil != null && item.snoozedUntil.isAfter(now)) {
                            Log.d(TAG, "   ⏭️ ${item.symbol} - snoozed until ${item.snoozedUntil}")
                            continue
                        }
                        val minScore = item.minScoreForNotify ?: settings.signalSensitivity.minScoreForNotify
                        Log.d(TAG, "   📈 ${item.symbol}: minScore=$minScore, range=$watchlistRange")
                        
                        // NOTE: This call to stockRepository.getSeries() automatically triggers
                        // passive accumulation of intraday data via StockRepository.accumulateIntradayData()
                        // Data is stored in IntradayDataCache for up to 1 year
                        val result = if (usePremarketData) {
                            Log.d(TAG, "   🌅 ${item.symbol}: Using premarket data")
                            stockRepository.getSeriesForPremarket(
                                item.symbol,
                                watchlistRange,
                                eventType = null
                            )
                        } else {
                            Log.d(TAG, "   📊 ${item.symbol}: Fetching regular series")
                            stockRepository.getSeries(
                                item.symbol,
                                watchlistRange,
                                forceRefresh = true,
                                eventType = null
                            )
                        }
                        if (result is StooqResult.Success) {
                            val series = result.data
                            Log.d(TAG, "   ✓ ${item.symbol}: Got ${series.size} candles")
                            if (!isFresh(series, now)) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: Data stale (last candle too old)")
                                continue
                            }
                            
                            // If intraday data doesn't have enough candles for signal computation (need 20+),
                            // fall back to daily data which will have more history
                            val signalSeries = if (series.size < MIN_CANDLES_FOR_SIGNAL) {
                                Log.i(TAG, "   🔄 ${item.symbol}: Insufficient candles (${series.size}), fetching daily fallback")
                                when (val fallback = stockRepository.getDailySeriesFallback(item.symbol, ChartRange.SIX_MONTH)) {
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
                            val overview = loadOverviewCached(item.symbol)
                            val signal = signalsRepository.computeSignal(item.symbol, signalSeries, watchlistRange, overview)
                            if (signal == null) {
                                Log.d(TAG, "   ⚠️ ${item.symbol}: No signal generated")
                                continue
                            }
                            val displayScore = signal.displayScore
                            Log.i(TAG, "   📈 ${item.symbol}: Signal computed - score=$displayScore, tier=${signal.tier.label}")
                            if (displayScore < minScore && displayScore > -minScore) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: Score $displayScore below threshold $minScore")
                                continue
                            }
                            if (signalsRepository.isInCooldown(item.symbol, signal.tier.label, signal.generatedAt)) {
                                Log.d(TAG, "   ⏭️ ${item.symbol}: Signal ${signal.tier.label} in cooldown")
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
                            val errorResult = result as? StooqResult.Error
                            Log.w(TAG, "   ❌ ${item.symbol}: Failed to fetch series - ${errorResult?.message}")
                        }
                        evaluateIndicatorAlerts(
                            item = item,
                            now = now,
                            candidates = candidates
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ ${item.symbol}: Exception during processing", e)
                    }
                }
            }

            if (moversEnabled) {
                val watchlistSymbols = watchlist.map { it.symbol }.toSet()
                val increasersSnapshot = when (val result = marketMoversRepository.getMarketMovers(
                    range = moversRange,
                    direction = MarketMoverDirection.INCREASERS,
                    forceRefresh = true
                )) {
                    is StooqResult.Success -> result.data
                    is StooqResult.Error -> {
                        Log.w(TAG, "Market movers increasers fetch failed: ${result.message}")
                        null
                    }
                }
                val decreasersSnapshot = when (val result = marketMoversRepository.getMarketMovers(
                    range = moversRange,
                    direction = MarketMoverDirection.DECREASERS,
                    forceRefresh = true
                )) {
                    is StooqResult.Success -> result.data
                    is StooqResult.Error -> {
                        Log.w(TAG, "Market movers decreasers fetch failed: ${result.message}")
                        null
                    }
                }
                if (increasersSnapshot?.isStale == true) {
                    Log.d(TAG, "Market movers increasers snapshot stale; skipping")
                }
                if (decreasersSnapshot?.isStale == true) {
                    Log.d(TAG, "Market movers decreasers snapshot stale; skipping")
                }
                val increasers = increasersSnapshot?.takeUnless { it.isStale }?.items.orEmpty()
                val decreasers = decreasersSnapshot?.takeUnless { it.isStale }?.items.orEmpty()
                val movers = (increasers.take(MAX_MOVERS) + decreasers.take(MAX_MOVERS))
                    .distinctBy { it.ticker }
                    .filterNot { watchlistSymbols.contains(it.ticker) }

                if (movers.isEmpty()) {
                    Log.d(TAG, "No market movers candidates to evaluate for window $windowId")
                }

                movers.forEach { mover ->
                    try {
                        val result = stockRepository.getSeries(
                            mover.ticker,
                            moversChartRange,
                            forceRefresh = true,
                            eventType = null
                        )
                        if (result is StooqResult.Success) {
                            val series = result.data
                            if (!isFresh(series, now)) {
                                Log.d(TAG, "Market mover ${mover.ticker} data stale; skipping")
                                return@forEach
                            }
                            
                            // If intraday data doesn't have enough candles for signal computation (need 20+),
                            // fall back to daily data which will have more history
                            val signalSeries = if (series.size < MIN_CANDLES_FOR_SIGNAL) {
                                Log.d(TAG, "Market mover ${mover.ticker}: insufficient candles (${series.size}), fetching daily fallback")
                                when (val fallback = stockRepository.getDailySeriesFallback(mover.ticker, ChartRange.SIX_MONTH)) {
                                    is StooqResult.Success -> fallback.data
                                    is StooqResult.Error -> {
                                        Log.w(TAG, "Daily fallback failed for ${mover.ticker}, using limited intraday")
                                        series
                                    }
                                }
                            } else {
                                series
                            }
                            
                            val overview = loadOverviewCached(mover.ticker)
                            val signal = signalsRepository.computeSignal(mover.ticker, signalSeries, moversChartRange, overview)
                            if (signal == null) {
                                Log.d(TAG, "Market mover ${mover.ticker} no signal generated")
                                return@forEach
                            }
                            val displayScore = signal.displayScore
                            val strongBuy = settings.signalSensitivity.strongBuyThreshold
                            val strongSell = settings.signalSensitivity.strongSellThreshold
                            if (displayScore < strongBuy && displayScore > strongSell) {
                                Log.d(TAG, "Market mover ${mover.ticker} score $displayScore below thresholds")
                                return@forEach
                            }
                            if (signalsRepository.isInCooldown(mover.ticker, signal.tier.label, signal.generatedAt)) {
                                Log.d(TAG, "Market mover ${mover.ticker} signal ${signal.tier.label} in cooldown")
                                return@forEach
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
                            Log.w(TAG, "Market mover ${mover.ticker} failed to fetch series; skipping")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing market mover ${mover.ticker}, skipping", e)
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
                    "candidates=0 (watchlist=$watchlistCandidates movers=$moverCandidates)"
                )
            } else {
                Log.i(TAG, "📬 Processing ${candidates.size} candidate(s)...")
                candidates.forEachIndexed { idx, event ->
                    Log.i(TAG, "   ${idx + 1}. ${event.ticker}: ${event.tier.label} (score=${event.score})")
                }
                notificationQueueProcessor.processCandidates(candidates, settings)
                recordRun(
                    "success",
                    "candidates=${candidates.size} (watchlist=$watchlistCandidates movers=$moverCandidates)"
                )
            }
            
            if (DebugConfig.ENABLE_DEV_MODE) {
                Log.i(TAG, "═══════════════════════════════════════════════════════════════")
                Log.i(TAG, "✅ NotificationWindowWorker COMPLETED SUCCESSFULLY")
                Log.i(TAG, "   Window: $windowId")
                Log.i(TAG, "   Time: ${LocalDateTime.now()}")
                Log.i(TAG, "═══════════════════════════════════════════════════════════════")
            } else {
                Log.d(TAG, "NotificationWindowWorker completed successfully - window: $windowId")
            }
            scheduleNextDev = devMode
            Result.success()
        } catch (e: java.io.IOException) {
            Log.e(TAG, "❌ Network error in window $windowId, will retry", e)
            recordRun("retry", "network error: ${e.message}")
            Result.retry()
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "❌ Database error in window $windowId, will retry", e)
            recordRun("retry", "database error: ${e.message}")
            Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "⚠️ Worker cancelled for window $windowId", e)
            recordRun("cancelled", "cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected error in window $windowId, will not retry", e)
            recordRun("failure", "unexpected error: ${e.message}")
            Result.failure()
        }

        if (scheduleNextDev) {
            scheduleDevRepeat(windowId)
        }

        return result
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

    private suspend fun evaluateIndicatorAlerts(
        item: WatchlistItemEntity,
        now: LocalDateTime,
        candidates: MutableList<NotificationEvent>
    ) {
        val alerts = IndicatorAlertJson.fromJson(item.indicatorAlertsJson).filter { it.enabled }
        if (alerts.isEmpty()) return

        val alertsByRange = alerts.groupBy { it.metric.defaultRange }
        for ((range, rangeAlerts) in alertsByRange) {
            val result = stockRepository.getSeries(
                item.symbol,
                range,
                forceRefresh = true,
                eventType = null
            )
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
                val overview = loadOverviewCached(item.symbol)
                val signal = signalsRepository.computeSignal(item.symbol, series, range, overview)
                for (alert in rangeAlerts) {
                    val evaluation = IndicatorAlertEvaluator.evaluate(alert, series) ?: continue
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

    private suspend fun loadOverviewCached(symbol: String): StockOverview? {
        val cached = overviewCache[symbol]
        if (cached != null || overviewCache.containsKey(symbol)) {
            return cached
        }
        val overview = when (val result = stockRepository.getStockOverview(symbol)) {
            is StooqResult.Success -> result.data
            is StooqResult.Error -> null
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
            settings.frequency == NotificationFrequency.DEV_ONE_MINUTE
    }

    private fun maybeShowDebugStartNotification(windowId: String, settings: AppSettings) {
        if (!isDevMode(settings)) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            "Debug worker",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, DEBUG_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Debug: Notification window")
            .setContentText("Worker started (window=$windowId)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(debugNotificationId(windowId), notification)
    }

    private fun scheduleDevRepeat(windowId: String) {
        val request = OneTimeWorkRequestBuilder<NotificationWindowWorker>()
            .setConstraints(DEV_CONSTRAINTS)
            .setInitialDelay(DEV_REPEAT_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(NotificationScheduler.WORK_TAG)
            .addTag(NotificationScheduler.DEV_REPEAT_TAG)
            .addTag("${NotificationScheduler.WINDOW_TAG_PREFIX}$windowId")
            .setInputData(workDataOf(KEY_WINDOW_ID to windowId))
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "notification_dev_repeat_$windowId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun debugNotificationId(windowId: String): Int {
        return DEBUG_NOTIFICATION_ID_BASE + (windowId.hashCode() and 0x0FFF)
    }

    companion object {
        const val KEY_WINDOW_ID = "window_id"
        private const val TAG = "NotificationWindowWorker"
        private const val MAX_MOVERS = 3
        private const val MIN_CANDLES_FOR_SIGNAL = 20
        private val STALE_THRESHOLD = Duration.ofDays(7)
        private const val DEBUG_CHANNEL_ID = "debug_worker"
        private const val DEBUG_NOTIFICATION_ID_BASE = 9100
        private const val DEV_REPEAT_DELAY_MINUTES = 2L
        private val DEV_CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
