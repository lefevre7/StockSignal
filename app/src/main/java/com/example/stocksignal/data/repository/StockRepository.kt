package com.example.stocksignal.data.repository

import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
import com.example.stocksignal.data.local.repository.StockDetailCacheRepository
import com.example.stocksignal.data.stooq.model.IntradayStockData
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.StockData
import com.example.stocksignal.data.stooq.repository.StooqRepository
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.NotificationEventType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(
    private val stooqRepository: StooqRepository,
    private val cacheRepository: StockDetailCacheRepository,
    private val signalsRepository: SignalsRepository
) {

    suspend fun getSeries(
        symbol: String,
        range: ChartRange,
        forceRefresh: Boolean = false,
        eventType: NotificationEventType? = NotificationEventType.WATCHLIST_SIGNAL
    ): Result<List<PriceCandle>> {
        val cached = cacheRepository.getCache(symbol, range.label)
        if (!forceRefresh && cached != null && !isStale(cached, range)) {
            return Result.Success(PriceCandleJson.fromJson(cached.seriesJson))
        }

        return when (range) {
            ChartRange.ONE_DAY,
            ChartRange.FIVE_DAY,
            ChartRange.ONE_MONTH -> fetchIntradaySeries(symbol, range, eventType)
            ChartRange.SIX_MONTH,
            ChartRange.ONE_YEAR,
            ChartRange.FIVE_YEAR -> fetchDailySeries(symbol, range, eventType)
        }
    }

    private suspend fun fetchIntradaySeries(
        symbol: String,
        range: ChartRange,
        eventType: NotificationEventType?
    ): Result<List<PriceCandle>> {
        val end = LocalDateTime.now()
        val start = intradayStartForRange(range, end)

        return when (val result = stooqRepository.getIntradayData(listOf(symbol), start = start, end = end)) {
            is Result.Error -> {
                if (range == ChartRange.ONE_DAY) {
                    result
                } else {
                    fetchDailySeries(symbol, range, eventType)
                }
            }
            is Result.Success -> {
                val map = result.data[symbol]
                if (map.isNullOrEmpty()) {
                    if (range == ChartRange.ONE_DAY) {
                        Result.Error(Exception("No intraday data for $symbol"), "No intraday data for $symbol")
                    } else {
                        fetchDailySeries(symbol, range, eventType)
                    }
                } else if (shouldFallbackToDaily(range, map)) {
                    fetchDailySeries(symbol, range, eventType)
                } else {
                    val candles = mapIntradayToCandles(map)
                    if (eventType != null) {
                        signalsRepository.evaluateAndStoreSignal(symbol, candles, range, eventType)
                    }
                    cacheRepository.upsert(
                        StockDetailCacheEntity(
                            symbol = symbol,
                            range = range.label,
                            fetchedAt = LocalDateTime.now(),
                            seriesJson = PriceCandleJson.toJson(candles),
                            latestPrice = candles.lastOrNull()?.close,
                            indicatorsJson = null,
                            signalHistoryJson = null
                        )
                    )
                    Result.Success(candles)
                }
            }
        }
    }

    private suspend fun fetchDailySeries(
        symbol: String,
        range: ChartRange,
        eventType: NotificationEventType?
    ): Result<List<PriceCandle>> {
        val endDate = normalizeToTradingDay(LocalDate.now())
        val startDate = when (range) {
            ChartRange.ONE_DAY -> endDate
            ChartRange.FIVE_DAY -> minusTradingDays(endDate, 4)
            ChartRange.ONE_MONTH -> endDate.minusMonths(1)
            ChartRange.SIX_MONTH -> endDate.minusMonths(6)
            ChartRange.ONE_YEAR -> endDate.minusYears(1)
            ChartRange.FIVE_YEAR -> endDate.minusYears(5)
        }

        return when (val result = stooqRepository.getData(listOf(symbol), startDate, endDate)) {
            is Result.Error -> result
            is Result.Success -> {
                val map = result.data[symbol]
                if (map.isNullOrEmpty()) {
                    Result.Error(Exception("No daily data for $symbol"), "No daily data for $symbol")
                } else {
                    val candles = mapDailyToCandles(map)
                    if (eventType != null) {
                        signalsRepository.evaluateAndStoreSignal(symbol, candles, range, eventType)
                    }
                    cacheRepository.upsert(
                        StockDetailCacheEntity(
                            symbol = symbol,
                            range = range.label,
                            fetchedAt = LocalDateTime.now(),
                            seriesJson = PriceCandleJson.toJson(candles),
                            latestPrice = candles.lastOrNull()?.close,
                            indicatorsJson = null,
                            signalHistoryJson = null
                        )
                    )
                    Result.Success(candles)
                }
            }
        }
    }

    private fun intradayStartForRange(range: ChartRange, end: LocalDateTime): LocalDateTime {
        return when (range) {
            ChartRange.ONE_DAY -> end.toLocalDate().atStartOfDay()
            ChartRange.FIVE_DAY -> {
                val endDate = end.toLocalDate()
                minusTradingDays(endDate, 4).atStartOfDay()
            }
            ChartRange.ONE_MONTH -> end.minusMonths(1)
            else -> end.minusDays(1)
        }
    }

    private fun shouldFallbackToDaily(
        range: ChartRange,
        data: Map<LocalDateTime, IntradayStockData>
    ): Boolean {
        if (range != ChartRange.FIVE_DAY && range != ChartRange.ONE_MONTH) return false
        val distinctDays = data.keys.map { it.toLocalDate() }.toSet().size
        return distinctDays <= 1
    }

    private fun normalizeToTradingDay(date: LocalDate): LocalDate {
        var adjusted = date
        while (adjusted.dayOfWeek == DayOfWeek.SATURDAY || adjusted.dayOfWeek == DayOfWeek.SUNDAY) {
            adjusted = adjusted.minusDays(1)
        }
        return adjusted
    }

    private fun minusTradingDays(date: LocalDate, days: Int): LocalDate {
        var remaining = days
        var current = date
        while (remaining > 0) {
            current = current.minusDays(1)
            val day = current.dayOfWeek
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                remaining -= 1
            }
        }
        return current
    }

    private fun isStale(cache: StockDetailCacheEntity, range: ChartRange): Boolean {
        val ttl = when (range) {
            ChartRange.ONE_DAY,
            ChartRange.FIVE_DAY,
            ChartRange.ONE_MONTH -> Duration.ofMinutes(10)
            ChartRange.SIX_MONTH,
            ChartRange.ONE_YEAR,
            ChartRange.FIVE_YEAR -> Duration.ofHours(24)
        }
        return Duration.between(cache.fetchedAt, LocalDateTime.now()) > ttl
    }

    private fun mapDailyToCandles(data: Map<LocalDate, StockData>): List<PriceCandle> {
        return data.entries.sortedBy { it.key }.map { (date, stock) ->
            PriceCandle(
                time = date.atStartOfDay(),
                open = stock.open,
                high = stock.high,
                low = stock.low,
                close = stock.close,
                volume = stock.volume
            )
        }
    }

    private fun mapIntradayToCandles(data: Map<LocalDateTime, IntradayStockData>): List<PriceCandle> {
        return data.entries.sortedBy { it.key }.map { (time, stock) ->
            PriceCandle(
                time = time,
                open = stock.open,
                high = stock.high,
                low = stock.low,
                close = stock.close,
                volume = stock.volume
            )
        }
    }

}
