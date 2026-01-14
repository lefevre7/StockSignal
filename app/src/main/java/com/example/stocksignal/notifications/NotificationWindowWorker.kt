package com.example.stocksignal.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.SettingsRepository
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
import com.example.stocksignal.domain.signal.IndicatorAlertEvaluator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@HiltWorker
class NotificationWindowWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketMoversRepository: MarketMoversRepository,
    private val stockRepository: StockRepository,
    private val signalsRepository: SignalsRepository,
    private val notificationQueueProcessor: NotificationQueueProcessor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val windowId = inputData.getString(KEY_WINDOW_ID)
        if (windowId == null) {
            Log.e(TAG, "Missing window ID in worker input")
            return Result.failure()
        }

        return try {
            val settings = settingsRepository.settingsFlow.first()
            val watchlistEnabled = settings.notificationTypes.contains(NotificationType.WATCHLIST)
            val moversEnabled = settings.notificationTypes.contains(NotificationType.MARKET_MOVERS)
            if (!watchlistEnabled && !moversEnabled) {
                Log.d(TAG, "Skipping window $windowId because no notification sources are enabled")
                return Result.success()
            }
            if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
                Log.d(TAG, "Skipping window $windowId because frequency is only when open")
                return Result.success()
            }

            Log.d(TAG, "Running notification window worker for $windowId (watchlist=$watchlistEnabled movers=$moversEnabled)")
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

            if (watchlistEnabled) {
                if (watchlist.isEmpty()) {
                    Log.d(TAG, "No watchlist items to evaluate for window $windowId")
                }
                for (item in watchlist) {
                    try {
                        if (!item.alertEnabled) {
                            Log.d(TAG, "Watchlist ${item.symbol} alerts disabled")
                            continue
                        }
                        if (item.snoozedUntil != null && item.snoozedUntil.isAfter(now)) {
                            Log.d(TAG, "Watchlist ${item.symbol} snoozed until ${item.snoozedUntil}")
                            continue
                        }
                        val minScore = item.minScoreForNotify ?: settings.signalSensitivity.minScoreForNotify
                        
                        // NOTE: This call to stockRepository.getSeries() automatically triggers
                        // passive accumulation of intraday data via StockRepository.accumulateIntradayData()
                        // Data is stored in IntradayDataCache for up to 1 year
                        val result = if (usePremarketData) {
                            stockRepository.getSeriesForPremarket(
                                item.symbol,
                                watchlistRange,
                                eventType = null
                            )
                        } else {
                            stockRepository.getSeries(
                                item.symbol,
                                watchlistRange,
                                forceRefresh = true,
                                eventType = null
                            )
                        }
                        if (result is StooqResult.Success) {
                            val series = result.data
                            if (!isFresh(series, now)) {
                                Log.d(TAG, "Watchlist ${item.symbol} data stale; skipping")
                                continue
                            }
                            val signal = signalsRepository.computeSignal(series, watchlistRange)
                            if (signal == null) {
                                Log.d(TAG, "Watchlist ${item.symbol} no signal generated")
                                continue
                            }
                            if (signal.score < minScore && signal.score > -minScore) {
                                Log.d(TAG, "Watchlist ${item.symbol} signal score ${signal.score} below threshold $minScore")
                                continue
                            }
                            if (signalsRepository.isInCooldown(item.symbol, signal.tier.label, signal.generatedAt)) {
                                Log.d(TAG, "Watchlist ${item.symbol} signal ${signal.tier.label} in cooldown")
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
                            Log.d(TAG, "Watchlist candidate ${item.symbol} ${signal.tier.label} score=${signal.score}")
                        } else {
                            Log.w(TAG, "Watchlist ${item.symbol} failed to fetch series; skipping")
                        }
                        evaluateIndicatorAlerts(
                            item = item,
                            now = now,
                            candidates = candidates
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing watchlist item ${item.symbol}, skipping", e)
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
                            val signal = signalsRepository.computeSignal(series, moversChartRange)
                            if (signal == null) {
                                Log.d(TAG, "Market mover ${mover.ticker} no signal generated")
                                return@forEach
                            }
                            val strongBuy = settings.signalSensitivity.strongBuyThreshold
                            val strongSell = settings.signalSensitivity.strongSellThreshold
                            if (signal.score < strongBuy && signal.score > strongSell) {
                                Log.d(TAG, "Market mover ${mover.ticker} score ${signal.score} below thresholds")
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
                            Log.d(TAG, "Market mover candidate ${mover.ticker} ${signal.tier.label} score=${signal.score}")
                        } else {
                            Log.w(TAG, "Market mover ${mover.ticker} failed to fetch series; skipping")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing market mover ${mover.ticker}, skipping", e)
                    }
                }
            }

            if (candidates.isEmpty()) {
                Log.d(TAG, "No candidates generated for window $windowId, processing queued events")
                notificationQueueProcessor.processQueued(settings)
            } else {
                notificationQueueProcessor.processCandidates(candidates, settings)
            }
            Log.d(
                TAG,
                "Successfully processed window $windowId with watchlist=$watchlistCandidates movers=$moverCandidates total=${candidates.size}"
            )
            Result.success()
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Network error in window $windowId, will retry", e)
            Result.retry()
        } catch (e: android.database.sqlite.SQLiteException) {
            Log.e(TAG, "Database error in window $windowId, will retry", e)
            Result.retry()
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "Worker cancelled for window $windowId", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in window $windowId, will not retry", e)
            Result.failure()
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
                val signal = signalsRepository.computeSignal(series, range)
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

    companion object {
        const val KEY_WINDOW_ID = "window_id"
        private const val TAG = "NotificationWindowWorker"
        private const val MAX_MOVERS = 3
        private val STALE_THRESHOLD = Duration.ofDays(7)
    }
}
