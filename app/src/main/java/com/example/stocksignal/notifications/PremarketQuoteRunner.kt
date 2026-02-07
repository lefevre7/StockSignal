package com.example.stocksignal.notifications

import android.util.Log
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result as StooqResult
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.data.stooq.repository.StooqRepository
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PremarketQuoteRunner @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val stooqRepository: StooqRepository,
    private val marketMoversRepository: MarketMoversRepository,
    private val stockRepository: StockRepository,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) {

    enum class RunOutcome {
        SUCCESS,
        FAILURE
    }

    suspend fun run(windowId: String, sampleIndex: Int): RunOutcome {
        if (windowId.isBlank() || sampleIndex < 0) return RunOutcome.FAILURE

        val key = NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex)
        diagnosticsRepository.recordPremarketRunStarted(key)
        val errors = mutableListOf<String>()
        var errorCount = 0
        var quoteCount = 0
        var upsertedCount = 0
        var candleLabel: String? = null

        fun recordError(message: String) {
            errorCount += 1
            if (errors.size < MAX_ERROR_MESSAGES) {
                errors.add(message)
            }
        }

        suspend fun recordResult(result: String, reason: String? = null) {
            diagnosticsRepository.recordPremarketRunResult(
                key = key,
                result = result,
                reason = reason,
                candleLabel = candleLabel,
                upsertedCount = upsertedCount,
                quoteCount = quoteCount,
                errorCount = if (errorCount > 0) errorCount else null
            )
        }

        return try {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.notificationTypes.contains(NotificationType.WATCHLIST)) {
                Log.d(TAG, "Premarket sample skipped: watchlist notifications disabled.")
                recordResult("skipped", "watchlist notifications disabled")
                return RunOutcome.SUCCESS
            }
            if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
                Log.d(TAG, "Premarket sample skipped: frequency only when open.")
                recordResult("skipped", "frequency only when open")
                return RunOutcome.SUCCESS
            }

            val now = ZonedDateTime.now()
            val window = PremarketWindowUtils.resolvePremarketWindow(settings, windowId, now)
                ?: run {
                    recordResult("skipped", "window not in premarket")
                    return RunOutcome.SUCCESS
                }
            val marketZone = PremarketWindowUtils.marketZone(window)
            val nowInZone = ZonedDateTime.now(marketZone)
            if (nowInZone.dayOfWeek == DayOfWeek.SATURDAY || nowInZone.dayOfWeek == DayOfWeek.SUNDAY) {
                Log.d(TAG, "Premarket sample skipped on weekend.")
                recordResult("skipped", "weekend")
                return RunOutcome.SUCCESS
            }
            if (PremarketWindowUtils.isDuringMarketHours(nowInZone)) {
                Log.d(TAG, "Premarket sample skipped during market hours.")
                recordResult("skipped", "during market hours")
                return RunOutcome.SUCCESS
            }

            val watchlist = watchlistRepository.getAll()
            val eligible = watchlist.filter { it.alertEnabled }
                .filterNot { it.snoozedUntil?.isAfter(LocalDateTime.now()) == true }

            if (eligible.isEmpty()) {
                Log.d(TAG, "Premarket sample skipped: no eligible watchlist items.")
                recordResult("skipped", "no eligible watchlist items")
                return RunOutcome.SUCCESS
            }

            val tickers = eligible.map { it.symbol }.distinct()
            if (sampleIndex == 0) {
                val prefetch = prefetchIntraday(tickers)
                prefetch.errorMessage?.let { recordError("prefetch: $it") }
            }

            val quotes = when (val result = stooqRepository.getPremarketQuotes(tickers)) {
                is StooqResult.Success -> result.data
                is StooqResult.Error -> {
                    Log.w(TAG, "Premarket quote fetch failed: ${result.message}")
                    recordError("quotes: ${result.message ?: "error"}")
                    emptyMap()
                }
            }
            quoteCount = quotes.size

            val bucketedTime = floorToTenMinutes(nowInZone.toLocalDateTime())
            candleLabel = bucketedTime.format(CANDLE_TIME_FORMATTER)
            val date = bucketedTime.toLocalDate()

            quotes.values.forEach { quote ->
                val fallback = stockRepository.getLatestCachedCandleForDate(quote.ticker, date)
                val bid = quote.bid ?: fallback?.open
                val ask = quote.ask ?: fallback?.close
                val volume = quote.volume ?: fallback?.volume
                if (bid == null || ask == null) {
                    Log.d(TAG, "Premarket quote missing bid/ask for ${quote.ticker}; skipping.")
                    recordError("bid/ask missing: ${quote.ticker}")
                    return@forEach
                }
                val candle = PriceCandle(
                    time = bucketedTime,
                    open = bid,
                    high = maxOf(bid, ask),
                    low = minOf(bid, ask),
                    close = ask,
                    volume = volume ?: 0L
                )
                try {
                    stockRepository.upsertPremarketCandle(quote.ticker, candle)
                    upsertedCount += 1
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upsert premarket candle for ${quote.ticker}", e)
                    recordError("upsert failed: ${quote.ticker}")
                }
            }

            val reason = buildReason(errors)
            val result = when {
                errorCount > 0 && upsertedCount == 0 -> "failure"
                errorCount > 0 -> "partial"
                upsertedCount > 0 -> "success"
                else -> "no_data"
            }
            recordResult(result, reason)
            RunOutcome.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Premarket sample failed", e)
            recordError("exception: ${e.message ?: "error"}")
            recordResult("failure", buildReason(errors))
            RunOutcome.FAILURE
        }
    }

    private suspend fun prefetchIntraday(watchlistTickers: List<String>): PrefetchOutcome {
        val movers = fetchMoverTickers()
        val symbols = (watchlistTickers + movers).distinct()
        if (symbols.isEmpty()) return PrefetchOutcome(0, null)

        return when (val result = stooqRepository.getIntradayData(symbols)) {
            is StooqResult.Error -> {
                Log.w(TAG, "Premarket intraday prefetch failed: ${result.message}")
                PrefetchOutcome(0, result.message ?: "prefetch error")
            }
            is StooqResult.Success -> {
                var stored = 0
                result.data.forEach { (ticker, data) ->
                    if (!data.isNullOrEmpty()) {
                        stockRepository.storeIntradaySnapshot(ticker, data, ChartRange.ONE_DAY)
                        stored += 1
                    }
                }
                PrefetchOutcome(stored, null)
            }
        }
    }

    private suspend fun fetchMoverTickers(): List<String> {
        val increasers = when (val result = marketMoversRepository.getMarketMovers(
            range = MarketMoverRange.ONE_DAY,
            direction = MarketMoverDirection.INCREASERS,
            forceRefresh = true
        )) {
            is StooqResult.Success -> result.data.items
            is StooqResult.Error -> emptyList()
        }
        val decreasers = when (val result = marketMoversRepository.getMarketMovers(
            range = MarketMoverRange.ONE_DAY,
            direction = MarketMoverDirection.DECREASERS,
            forceRefresh = true
        )) {
            is StooqResult.Success -> result.data.items
            is StooqResult.Error -> emptyList()
        }
        return (increasers + decreasers).map { it.ticker }.distinct()
    }

    private fun floorToTenMinutes(time: LocalDateTime): LocalDateTime {
        val minute = (time.minute / 10) * 10
        return time.withMinute(minute).withSecond(0).withNano(0)
    }

    private fun buildReason(errors: List<String>): String? {
        if (errors.isEmpty()) return null
        val reason = errors.joinToString("; ")
        return if (reason.length > MAX_REASON_CHARS) {
            reason.take(MAX_REASON_CHARS - 3) + "..."
        } else {
            reason
        }
    }

    private data class PrefetchOutcome(
        val storedCount: Int,
        val errorMessage: String?
    )

    companion object {
        private const val TAG = "PremarketQuoteRunner"
        private const val MAX_ERROR_MESSAGES = 5
        private const val MAX_REASON_CHARS = 180
        private val CANDLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
