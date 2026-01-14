package com.example.stocksignal.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZonedDateTime

@HiltWorker
class PremarketQuoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val stooqRepository: StooqRepository,
    private val marketMoversRepository: MarketMoversRepository,
    private val stockRepository: StockRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val windowId = inputData.getString(KEY_WINDOW_ID)
        val sampleIndex = inputData.getInt(KEY_SAMPLE_INDEX, -1)
        if (windowId.isNullOrBlank() || sampleIndex < 0) return Result.failure()

        val settings = settingsRepository.settingsFlow.first()
        if (!settings.notificationTypes.contains(NotificationType.WATCHLIST)) {
            Log.d(TAG, "Premarket sample skipped: watchlist notifications disabled.")
            return Result.success()
        }
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            Log.d(TAG, "Premarket sample skipped: frequency only when open.")
            return Result.success()
        }

        val now = ZonedDateTime.now()
        val window = PremarketWindowUtils.resolvePremarketWindow(settings, windowId, now) ?: return Result.success()
        val marketZone = PremarketWindowUtils.marketZone(window)
        val nowInZone = ZonedDateTime.now(marketZone)
        if (nowInZone.dayOfWeek == DayOfWeek.SATURDAY || nowInZone.dayOfWeek == DayOfWeek.SUNDAY) {
            Log.d(TAG, "Premarket sample skipped on weekend.")
            return Result.success()
        }
        if (PremarketWindowUtils.isDuringMarketHours(nowInZone)) {
            Log.d(TAG, "Premarket sample skipped during market hours.")
            return Result.success()
        }

        val watchlist = watchlistRepository.getAll()
        val eligible = watchlist.filter { it.alertEnabled }
            .filterNot { it.snoozedUntil?.isAfter(LocalDateTime.now()) == true }

        if (eligible.isEmpty()) {
            Log.d(TAG, "Premarket sample skipped: no eligible watchlist items.")
            return Result.success()
        }

        val tickers = eligible.map { it.symbol }.distinct()
        if (sampleIndex == 0) {
            prefetchIntraday(tickers)
        }

        val quotes = when (val result = stooqRepository.getPremarketQuotes(tickers)) {
            is StooqResult.Success -> result.data
            is StooqResult.Error -> {
                Log.w(TAG, "Premarket quote fetch failed: ${result.message}")
                emptyMap()
            }
        }

        val bucketedTime = floorToTenMinutes(nowInZone.toLocalDateTime())
        val date = bucketedTime.toLocalDate()

        quotes.values.forEach { quote ->
            val fallback = stockRepository.getLatestCachedCandleForDate(quote.ticker, date)
            val bid = quote.bid ?: fallback?.open
            val ask = quote.ask ?: fallback?.close
            val volume = quote.volume ?: fallback?.volume
            if (bid == null || ask == null) {
                Log.d(TAG, "Premarket quote missing bid/ask for ${quote.ticker}; skipping.")
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
            stockRepository.upsertPremarketCandle(quote.ticker, candle)
        }

        return Result.success()
    }

    private suspend fun prefetchIntraday(watchlistTickers: List<String>) {
        val movers = fetchMoverTickers()
        val symbols = (watchlistTickers + movers).distinct()
        if (symbols.isEmpty()) return

        when (val result = stooqRepository.getIntradayData(symbols)) {
            is StooqResult.Error -> {
                Log.w(TAG, "Premarket intraday prefetch failed: ${result.message}")
            }
            is StooqResult.Success -> {
                result.data.forEach { (ticker, data) ->
                    if (!data.isNullOrEmpty()) {
                        stockRepository.storeIntradaySnapshot(ticker, data, ChartRange.ONE_DAY)
                    }
                }
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

    companion object {
        const val KEY_WINDOW_ID = "premarket_window_id"
        const val KEY_SAMPLE_INDEX = "premarket_sample_index"
        const val WORK_TAG = "premarket_quote"
        private const val TAG = "PremarketQuoteWorker"
    }
}
