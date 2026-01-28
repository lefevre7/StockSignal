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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PremarketQuoteRunner @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val stooqRepository: StooqRepository,
    private val marketMoversRepository: MarketMoversRepository,
    private val stockRepository: StockRepository
) {

    enum class RunOutcome {
        SUCCESS,
        FAILURE
    }

    suspend fun run(windowId: String, sampleIndex: Int): RunOutcome {
        if (windowId.isBlank() || sampleIndex < 0) return RunOutcome.FAILURE

        val settings = settingsRepository.settingsFlow.first()
        if (!settings.notificationTypes.contains(NotificationType.WATCHLIST)) {
            Log.d(TAG, "Premarket sample skipped: watchlist notifications disabled.")
            return RunOutcome.SUCCESS
        }
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            Log.d(TAG, "Premarket sample skipped: frequency only when open.")
            return RunOutcome.SUCCESS
        }

        val now = ZonedDateTime.now()
        val window = PremarketWindowUtils.resolvePremarketWindow(settings, windowId, now) ?: return RunOutcome.SUCCESS
        val marketZone = PremarketWindowUtils.marketZone(window)
        val nowInZone = ZonedDateTime.now(marketZone)
        if (nowInZone.dayOfWeek == DayOfWeek.SATURDAY || nowInZone.dayOfWeek == DayOfWeek.SUNDAY) {
            Log.d(TAG, "Premarket sample skipped on weekend.")
            return RunOutcome.SUCCESS
        }
        if (PremarketWindowUtils.isDuringMarketHours(nowInZone)) {
            Log.d(TAG, "Premarket sample skipped during market hours.")
            return RunOutcome.SUCCESS
        }

        val watchlist = watchlistRepository.getAll()
        val eligible = watchlist.filter { it.alertEnabled }
            .filterNot { it.snoozedUntil?.isAfter(LocalDateTime.now()) == true }

        if (eligible.isEmpty()) {
            Log.d(TAG, "Premarket sample skipped: no eligible watchlist items.")
            return RunOutcome.SUCCESS
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

        return RunOutcome.SUCCESS
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
        private const val TAG = "PremarketQuoteRunner"
    }
}
