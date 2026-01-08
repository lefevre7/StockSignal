package com.example.stocksignal.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
        val windowId = inputData.getString(KEY_WINDOW_ID) ?: return Result.failure()
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.notificationTypes.contains(NotificationType.DIGESTS)) {
            Log.d(TAG, "Skipping window $windowId because digests are disabled")
            return Result.success()
        }
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            Log.d(TAG, "Skipping window $windowId because frequency is only when open")
            return Result.success()
        }

        Log.d(TAG, "Running notification window worker for $windowId")
        val candidates = mutableListOf<NotificationEvent>()
        val now = LocalDateTime.now()

        val watchlistRange = settings.selectedChartRange
        val moversRange = settings.selectedMarketMoverRange
        val moversChartRange = chartRangeForMarketRange(moversRange)

        val watchlist = watchlistRepository.getAll()

        if (settings.notificationTypes.contains(NotificationType.WATCHLIST)) {
            for (item in watchlist) {
                if (!item.alertEnabled) continue
                if (item.snoozedUntil != null && item.snoozedUntil.isAfter(now)) continue
                val minScore = item.minScoreForNotify ?: settings.signalSensitivity.minScoreForNotify
                val result = stockRepository.getSeries(
                    item.symbol,
                    watchlistRange,
                    forceRefresh = true,
                    eventType = null
                )
                if (result is StooqResult.Success) {
                    val series = result.data
                    if (!isFresh(series, now)) continue
                    val signal = signalsRepository.computeSignal(series, watchlistRange) ?: continue
                    if (signal.score < minScore && signal.score > -minScore) continue
                    if (signalsRepository.isInCooldown(item.symbol, signal.tier.label, signal.generatedAt)) continue
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
                }
            }
        }

        if (settings.notificationTypes.contains(NotificationType.MARKET_MOVERS)) {
            val watchlistSymbols = watchlist.map { it.symbol }.toSet()
            val increasersSnapshot = when (val result = marketMoversRepository.getMarketMovers(
                range = moversRange,
                direction = MarketMoverDirection.INCREASERS,
                forceRefresh = true
            )) {
                is StooqResult.Success -> result.data
                is StooqResult.Error -> null
            }
            val decreasersSnapshot = when (val result = marketMoversRepository.getMarketMovers(
                range = moversRange,
                direction = MarketMoverDirection.DECREASERS,
                forceRefresh = true
            )) {
                is StooqResult.Success -> result.data
                is StooqResult.Error -> null
            }
            val increasers = increasersSnapshot?.takeUnless { it.isStale }?.items.orEmpty()
            val decreasers = decreasersSnapshot?.takeUnless { it.isStale }?.items.orEmpty()
            val movers = (increasers.take(MAX_MOVERS) + decreasers.take(MAX_MOVERS))
                .distinctBy { it.ticker }
                .filterNot { watchlistSymbols.contains(it.ticker) }

            movers.forEach { mover ->
                val result = stockRepository.getSeries(
                    mover.ticker,
                    moversChartRange,
                    forceRefresh = true,
                    eventType = null
                )
                if (result is StooqResult.Success) {
                    val series = result.data
                    if (!isFresh(series, now)) return@forEach
                    val signal = signalsRepository.computeSignal(series, moversChartRange) ?: return@forEach
                    val strongBuy = settings.signalSensitivity.strongBuyThreshold
                    val strongSell = settings.signalSensitivity.strongSellThreshold
                    if (signal.score < strongBuy && signal.score > strongSell) return@forEach
                    if (signalsRepository.isInCooldown(mover.ticker, signal.tier.label, signal.generatedAt)) return@forEach
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
                }
            }
        }

        notificationQueueProcessor.processCandidates(candidates, settings)
        return Result.success()
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
        if (!isMarketHours(now)) return true
        val age = Duration.between(last, now)
        return !age.isNegative && age <= STALE_THRESHOLD
    }

    private fun isMarketHours(now: LocalDateTime): Boolean {
        val eastern = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(MARKET_ZONE)
        val day = eastern.dayOfWeek
        if (day.value >= 6) return false
        val time = eastern.toLocalTime()
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE)
    }

    companion object {
        const val KEY_WINDOW_ID = "window_id"
        private const val TAG = "NotificationWindowWorker"
        private const val MAX_MOVERS = 3
        private val STALE_THRESHOLD = Duration.ofMinutes(10)
        private val MARKET_ZONE = ZoneId.of("America/New_York")
        private val MARKET_OPEN = LocalTime.of(9, 30)
        private val MARKET_CLOSE = LocalTime.of(16, 0)
    }
}
