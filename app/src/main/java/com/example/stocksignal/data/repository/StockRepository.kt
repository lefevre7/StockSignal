package com.example.stocksignal.data.repository

import android.util.Log
import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
import com.example.stocksignal.data.local.entity.StockOverviewCacheEntity
import com.example.stocksignal.data.local.repository.IntradayDataCacheRepository
import com.example.stocksignal.data.local.repository.StockDetailCacheRepository
import com.example.stocksignal.data.local.repository.StockOverviewCacheRepository
import com.example.stocksignal.data.stooq.model.IntradayStockData
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.StockData
import com.example.stocksignal.data.stooq.repository.StooqRepository
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.StockOverview
import com.example.stocksignal.domain.model.StockNewsItem
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepository @Inject constructor(
    private val stooqRepository: StooqRepository,
    private val cacheRepository: StockDetailCacheRepository,
    private val intradayCacheRepository: IntradayDataCacheRepository,
    private val signalsRepository: SignalsRepository,
    private val overviewCacheRepository: StockOverviewCacheRepository
) {

    suspend fun getSeries(
        symbol: String,
        range: ChartRange,
        forceRefresh: Boolean = false,
        eventType: NotificationEventType? = NotificationEventType.WATCHLIST_SIGNAL
    ): Result<List<PriceCandle>> {
        try {
            val cached = cacheRepository.getCache(symbol, range.label)
            if (!forceRefresh && cached != null && !isStale(cached, range)) {
                Log.d(TAG, "Using cached data for $symbol/${range.label}")
                return Result.Success(PriceCandleJson.fromJson(cached.seriesJson))
            }

            Log.d(TAG, "Fetching fresh data for $symbol/${range.label}")
            
            // Use chart range to determine data source (intraday for short ranges, daily for long ranges)
            // Note: Holding period is used for indicator parameters, not data source selection
            return when (range) {
                ChartRange.ONE_DAY,
                ChartRange.FIVE_DAY,
                ChartRange.ONE_MONTH -> fetchIntradaySeries(symbol, range, eventType)
                ChartRange.SIX_MONTH,
                ChartRange.ONE_YEAR,
                ChartRange.FIVE_YEAR -> fetchDailySeries(symbol, range, eventType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting series for $symbol/${range.label}", e)
            return Result.Error(e, "Failed to get stock data: ${e.message}")
        }
    }

    suspend fun getSeriesForDetail(
        symbol: String,
        range: ChartRange,
        forceRefresh: Boolean = false
    ): Result<List<PriceCandle>> {
        return when (range) {
            ChartRange.ONE_DAY -> getLiveIntradayForDetail(symbol, range, forceRefresh)
            ChartRange.FIVE_DAY,
            ChartRange.ONE_MONTH,
            ChartRange.SIX_MONTH -> {
                val intraday = getIntradayHistoryIfComplete(symbol, range)
                if (!intraday.isNullOrEmpty()) {
                    Result.Success(intraday)
                } else {
                    getDailySeriesFallback(symbol, range, forceRefresh)
                }
            }
            ChartRange.ONE_YEAR,
            ChartRange.FIVE_YEAR -> getSeries(symbol, range, forceRefresh, eventType = null)
        }
    }

    suspend fun getFreshCachedSeries(
        symbol: String,
        range: ChartRange
    ): Result<List<PriceCandle>> {
        return try {
            val cached = cacheRepository.getCache(symbol, range.label)
            if (cached == null) {
                Result.Error(Exception("No cached data for $symbol/${range.label}"), "No cached data")
            } else if (isStale(cached, range)) {
                Result.Error(Exception("Cached data stale for $symbol/${range.label}"), "Cached data stale")
            } else {
                Result.Success(PriceCandleJson.fromJson(cached.seriesJson))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cached series for $symbol/${range.label}", e)
            Result.Error(e, "Failed to read cached data: ${e.message}")
        }
    }

    suspend fun getDailySeriesFallback(
        symbol: String,
        range: ChartRange,
        forceRefresh: Boolean = false
    ): Result<List<PriceCandle>> {
        val cacheKey = "${range.label}_daily_fallback"
        val cached = cacheRepository.getCache(symbol, cacheKey)
        if (!forceRefresh && cached != null && !isFallbackStale(cached)) {
            return Result.Success(PriceCandleJson.fromJson(cached.seriesJson))
        }
        return fetchDailySeries(symbol, range, eventType = null, cacheKey = cacheKey)
    }

    suspend fun refreshIntradayHistory(
        symbol: String,
        range: ChartRange
    ) {
        try {
            val end = LocalDateTime.now()
            val start = intradayRefreshStartForRange(range, end)
            when (val result = stooqRepository.getIntradayData(listOf(symbol), start = start, end = end)) {
                is Result.Error -> {
                    Log.w(TAG, "Intraday refresh failed for $symbol/${range.label}: ${result.message}")
                }
                is Result.Success -> {
                    val map = result.data[symbol]
                    if (map.isNullOrEmpty()) {
                        Log.w(TAG, "Intraday refresh returned empty data for $symbol/${range.label}")
                        return
                    }
                    val candles = mapIntradayToCandles(map)
                    accumulateIntradayData(symbol, map)
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
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh intraday history for $symbol/${range.label}", e)
        }
    }

    suspend fun getSeriesForPremarket(
        symbol: String,
        range: ChartRange,
        eventType: NotificationEventType? = NotificationEventType.WATCHLIST_SIGNAL
    ): Result<List<PriceCandle>> {
        return when (range) {
            ChartRange.ONE_DAY,
            ChartRange.FIVE_DAY,
            ChartRange.ONE_MONTH -> fetchIntradaySeriesForPremarket(symbol, range, eventType)
            ChartRange.SIX_MONTH,
            ChartRange.ONE_YEAR,
            ChartRange.FIVE_YEAR -> getSeries(symbol, range, forceRefresh = true, eventType = eventType)
        }
    }

    private suspend fun fetchIntradaySeries(
        symbol: String,
        range: ChartRange,
        eventType: NotificationEventType?
    ): Result<List<PriceCandle>> {
        // TODO: API UPGRADE - Stooq API currently supports 1 day to 6 weeks of intraday data.
        // Once extended historical intraday API is available, implement bulk backfill here.
        // Until then, we accumulate data passively as users check their watchlist.
        
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
                    
                    // Passive accumulation: Store 10-minute candles to IntradayDataCache
                    // for gradual build-up of historical data (up to 1 year)
                    accumulateIntradayData(symbol, map)
                    
                    if (eventType != null) {
                        val overview = loadOverviewOrNull(symbol)
                        signalsRepository.evaluateAndStoreSignal(symbol, candles, range, overview, eventType)
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

    private suspend fun fetchIntradaySeriesForPremarket(
        symbol: String,
        range: ChartRange,
        eventType: NotificationEventType?
    ): Result<List<PriceCandle>> {
        val end = LocalDateTime.now()
        val start = premarketIntradayStartForRange(range, end)
        return when (val result = stooqRepository.getIntradayData(listOf(symbol), start = start, end = end)) {
            is Result.Error -> result
            is Result.Success -> {
                val map = result.data[symbol]
                if (map.isNullOrEmpty()) {
                    Result.Error(Exception("No intraday data for $symbol"), "No intraday data for $symbol")
                } else {
                    val candles = mapIntradayToCandles(map)
                    val merged = mergePremarketCandles(symbol, candles, end)
                    accumulateIntradayData(symbol, map)
                    if (eventType != null) {
                        val overview = loadOverviewOrNull(symbol)
                        signalsRepository.evaluateAndStoreSignal(
                            symbol,
                            merged,
                            range,
                            overview,
                            eventType,
                            skipAiGeneration = true
                        )
                    }
                    cacheRepository.upsert(
                        StockDetailCacheEntity(
                            symbol = symbol,
                            range = range.label,
                            fetchedAt = LocalDateTime.now(),
                            seriesJson = PriceCandleJson.toJson(merged),
                            latestPrice = merged.lastOrNull()?.close,
                            indicatorsJson = null,
                            signalHistoryJson = null
                        )
                    )
                    Result.Success(merged)
                }
            }
        }
    }

    suspend fun storeIntradaySnapshot(
        symbol: String,
        data: Map<LocalDateTime, IntradayStockData>,
        range: ChartRange = ChartRange.ONE_DAY
    ) {
        if (data.isEmpty()) return
        val candles = mapIntradayToCandles(data)
        accumulateIntradayData(symbol, data)
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
    }
    
    /**
     * Accumulate intraday data into the long-term cache for historical analysis.
     * Data is chunked by date and stored for up to 1 year.
     * Historical data (not today) is immutable; only today's data gets updated with new candles.
     */
    private suspend fun accumulateIntradayData(
        symbol: String,
        data: Map<LocalDateTime, IntradayStockData>
    ) {
        try {
            val today = LocalDate.now()
            
            // Group candles by date
            val candlesByDate = data.entries.groupBy { it.key.toLocalDate() }
            
            for ((date, entries) in candlesByDate) {
                val candles = entries.map { (time, stock) ->
                    PriceCandle(
                        time = time,
                        open = stock.open,
                        high = stock.high,
                        low = stock.low,
                        close = stock.close,
                        volume = stock.volume
                    )
                }.sortedBy { it.time }
                
                // Convert candles to JSON using existing PriceCandleJson utility
                val candlesJson = PriceCandleJson.toJson(candles)
                
                val now = LocalDateTime.now()
                val entity = IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = candlesJson,
                    createdAt = now,
                    updatedAt = now
                )
                
                // Only upsert if it's today's data (allow updates) or if we don't have this date yet
                // Historical data (date < today) is immutable once stored
                if (date == today) {
                    val existing = intradayCacheRepository.getCandlesByDateRange(symbol, date, date)
                    val existingEntity = existing.firstOrNull()
                    val existingCandles = existingEntity?.let { PriceCandleJson.fromJson(it.candlesJson) }.orEmpty()
                    val merged = mergeCandles(existingCandles, candles)
                    val mergedEntity = entity.copy(
                        createdAt = existingEntity?.createdAt ?: now,
                        candlesJson = PriceCandleJson.toJson(merged)
                    )
                    intradayCacheRepository.upsert(mergedEntity)
                    Log.d(TAG, "Updated today's intraday cache for $symbol on $date with ${merged.size} candles")
                } else {
                    // Check if we already have this historical date
                    val existing = intradayCacheRepository.getCandlesByDateRange(symbol, date, date)
                    if (existing.isEmpty()) {
                        intradayCacheRepository.upsert(entity)
                        Log.d(TAG, "Stored historical intraday data for $symbol on $date with ${candles.size} candles")
                    } else {
                        Log.d(TAG, "Skipping already-stored historical data for $symbol on $date")
                    }
                }
            }
            
            // Enforce 1-year retention: delete data older than 1 year
            val oneYearAgo = today.minusYears(1)
            intradayCacheRepository.deleteOldData(symbol, oneYearAgo)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accumulate intraday data for $symbol", e)
            // Don't fail the main fetch operation if accumulation fails
        }
    }

    suspend fun upsertPremarketCandle(
        symbol: String,
        candle: PriceCandle
    ) {
        try {
            val date = candle.time.toLocalDate()
            val existingEntities = intradayCacheRepository.getCandlesByDateRange(symbol, date, date)
            val existingEntity = existingEntities.firstOrNull()
            val existingCandles = existingEntity?.let { PriceCandleJson.fromJson(it.candlesJson) }.orEmpty()
            if (existingCandles.any { it.time == candle.time }) return

            val merged = (existingCandles + candle).sortedBy { it.time }
            val now = LocalDateTime.now()
            val createdAt = existingEntity?.createdAt ?: now
            val entity = IntradayDataCacheEntity(
                symbol = symbol,
                date = date,
                createdAt = createdAt,
                updatedAt = now,
                candlesJson = PriceCandleJson.toJson(merged)
            )
            intradayCacheRepository.upsert(entity)
            upsertOneDayCacheWithCandle(symbol, candle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upsert premarket candle for $symbol", e)
        }
    }

    suspend fun getLatestCachedCandleForDate(
        symbol: String,
        date: LocalDate
    ): PriceCandle? {
        val entities = intradayCacheRepository.getCandlesByDateRange(symbol, date, date)
        if (entities.isEmpty()) return null
        val candles = entities.flatMap { PriceCandleJson.fromJson(it.candlesJson) }
        return candles.maxByOrNull { it.time }
    }

    private suspend fun upsertOneDayCacheWithCandle(
        symbol: String,
        candle: PriceCandle
    ) {
        val range = ChartRange.ONE_DAY.label
        val existing = cacheRepository.getCache(symbol, range)
        val existingCandles = existing?.let { PriceCandleJson.fromJson(it.seriesJson) }.orEmpty()
        if (existingCandles.any { it.time == candle.time }) return

        val merged = (existingCandles + candle).sortedBy { it.time }
        cacheRepository.upsert(
            StockDetailCacheEntity(
                symbol = symbol,
                range = range,
                fetchedAt = LocalDateTime.now(),
                seriesJson = PriceCandleJson.toJson(merged),
                latestPrice = merged.lastOrNull()?.close,
                indicatorsJson = existing?.indicatorsJson,
                signalHistoryJson = existing?.signalHistoryJson
            )
        )
    }

    private suspend fun fetchDailySeries(
        symbol: String,
        range: ChartRange,
        eventType: NotificationEventType?,
        cacheKey: String = range.label
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
                        val overview = loadOverviewOrNull(symbol)
                        signalsRepository.evaluateAndStoreSignal(symbol, candles, range, overview, eventType)
                    }
                    cacheRepository.upsert(
                        StockDetailCacheEntity(
                            symbol = symbol,
                            range = cacheKey,
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

    private suspend fun getLiveIntradayForDetail(
        symbol: String,
        range: ChartRange,
        forceRefresh: Boolean
    ): Result<List<PriceCandle>> {
        return when (val result = fetchIntradaySeries(symbol, range, eventType = null)) {
            is Result.Success -> result
            is Result.Error -> {
                val cached = cacheRepository.getCache(symbol, range.label)
                if (cached != null) {
                    Result.Success(PriceCandleJson.fromJson(cached.seriesJson))
                } else {
                    getDailySeriesFallback(symbol, range, forceRefresh)
                }
            }
        }
    }

    private suspend fun getIntradayHistoryIfComplete(
        symbol: String,
        range: ChartRange
    ): List<PriceCandle>? {
        val (rawStart, rawEnd) = resolveDateRange(range)
        val startDate = normalizeStartToTradingDay(rawStart)
        val endDate = rawEnd
        if (startDate.isAfter(endDate)) return null

        val entities = intradayCacheRepository.getCandlesByDateRange(symbol, startDate, endDate)
        if (entities.isEmpty()) return null

        val dates = entities.map { it.date }.toSet()
        if (!dates.contains(startDate) || !dates.contains(endDate)) return null

        val candles = entities.flatMap { PriceCandleJson.fromJson(it.candlesJson) }
            .sortedBy { it.time }
        return candles
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

    private fun premarketIntradayStartForRange(range: ChartRange, end: LocalDateTime): LocalDateTime {
        val base = intradayStartForRange(range, end)
        val marketOpen = LocalTime.of(9, 30)
        val zone = ZoneId.of("America/New_York")
        val endInZone = end.atZone(zone)
        if (endInZone.toLocalTime().isBefore(marketOpen)) {
            val previousTradingDay = normalizeToTradingDay(endInZone.toLocalDate().minusDays(1))
            val adjusted = previousTradingDay.atStartOfDay()
            return if (adjusted.isBefore(base)) adjusted else base
        }
        return base
    }

    private fun intradayRefreshStartForRange(range: ChartRange, end: LocalDateTime): LocalDateTime {
        return when (range) {
            ChartRange.ONE_DAY -> end.toLocalDate().atStartOfDay()
            ChartRange.FIVE_DAY -> {
                val endDate = end.toLocalDate()
                minusTradingDays(endDate, 4).atStartOfDay()
            }
            ChartRange.ONE_MONTH,
            ChartRange.SIX_MONTH -> end.minusMonths(1)
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

    private fun resolveDateRange(range: ChartRange, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
        val endDate = normalizeToTradingDay(today)
        val startDate = when (range) {
            ChartRange.ONE_DAY -> endDate
            ChartRange.FIVE_DAY -> minusTradingDays(endDate, 4)
            ChartRange.ONE_MONTH -> endDate.minusMonths(1)
            ChartRange.SIX_MONTH -> endDate.minusMonths(6)
            ChartRange.ONE_YEAR -> endDate.minusYears(1)
            ChartRange.FIVE_YEAR -> endDate.minusYears(5)
        }
        return startDate to endDate
    }

    private fun normalizeToTradingDay(date: LocalDate): LocalDate {
        var adjusted = date
        while (adjusted.dayOfWeek == DayOfWeek.SATURDAY || adjusted.dayOfWeek == DayOfWeek.SUNDAY) {
            adjusted = adjusted.minusDays(1)
        }
        return adjusted
    }

    private fun normalizeStartToTradingDay(date: LocalDate): LocalDate {
        var adjusted = date
        while (adjusted.dayOfWeek == DayOfWeek.SATURDAY || adjusted.dayOfWeek == DayOfWeek.SUNDAY) {
            adjusted = adjusted.plusDays(1)
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

    private fun isFallbackStale(cache: StockDetailCacheEntity): Boolean {
        return Duration.between(cache.fetchedAt, LocalDateTime.now()) > Duration.ofHours(24)
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

    private fun mergeCandles(
        existing: List<PriceCandle>,
        incoming: List<PriceCandle>
    ): List<PriceCandle> {
        if (existing.isEmpty()) return incoming
        val merged = LinkedHashMap<LocalDateTime, PriceCandle>()
        existing.forEach { merged[it.time] = it }
        incoming.forEach { merged[it.time] = it }
        return merged.values.sortedBy { it.time }
    }

    private suspend fun mergePremarketCandles(
        symbol: String,
        candles: List<PriceCandle>,
        end: LocalDateTime
    ): List<PriceCandle> {
        val zone = ZoneId.of("America/New_York")
        val date = end.atZone(zone).toLocalDate()
        val cachedEntities = intradayCacheRepository.getCandlesByDateRange(symbol, date, date)
        if (cachedEntities.isEmpty()) return candles

        val cached = cachedEntities.flatMap { PriceCandleJson.fromJson(it.candlesJson) }
        if (cached.isEmpty()) return candles

        val merged = LinkedHashMap<LocalDateTime, PriceCandle>()
        candles.forEach { merged[it.time] = it }
        cached.forEach { candle ->
            if (!merged.containsKey(candle.time)) {
                merged[candle.time] = candle
            }
        }
        return merged.values.sortedBy { it.time }
    }

    /**
     * Fetches stock overview/fundamental data with 10-minute caching.
     * 
     * @param symbol Stock ticker symbol (e.g., "TSLA.US")
     * @param forceRefresh If true, bypass cache and fetch fresh data
     * @return Result containing StockOverview or Error
     */
    suspend fun getStockOverview(
        symbol: String,
        forceRefresh: Boolean = false
    ): Result<StockOverview> {
        try {
            // Check cache first
            val cached = overviewCacheRepository.getCache(symbol)
            if (!forceRefresh && cached != null && !isOverviewStale(cached)) {
                Log.d(TAG, "Using cached overview for $symbol")
                return Result.Success(
                    StockOverview(
                        symbol = cached.symbol,
                        marketCap = cached.marketCap,
                        peRatio = cached.peRatio,
                        dividend = cached.dividend,
                        week52High = cached.week52High,
                        week52Low = cached.week52Low,
                        news = StockNewsJson.fromJson(cached.newsJson)
                    )
                )
            }

            // Fetch fresh data
            Log.d(TAG, "Fetching fresh overview for $symbol")
            return when (val result = stooqRepository.getStockOverview(symbol)) {
                is Result.Error -> result
                is Result.Success -> {
                    val overview = result.data
                    // Cache the result
                    overviewCacheRepository.upsert(
                        StockOverviewCacheEntity(
                            symbol = overview.symbol,
                            marketCap = overview.marketCap,
                            peRatio = overview.peRatio,
                            dividend = overview.dividend,
                            week52High = overview.week52High,
                            week52Low = overview.week52Low,
                            newsJson = StockNewsJson.toJson(overview.news),
                            fetchedAt = LocalDateTime.now().toString()
                        )
                    )
                    Result.Success(overview)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting overview for $symbol", e)
            return Result.Error(e, "Failed to get stock overview: ${e.message}")
        }
    }

    private fun isOverviewStale(cache: StockOverviewCacheEntity): Boolean {
        val fetchedAt = LocalDateTime.parse(cache.fetchedAt)
        val age = Duration.between(fetchedAt, LocalDateTime.now())
        val ttl = Duration.ofMinutes(10)
        return age > ttl
    }

    suspend fun updateOverviewNews(symbol: String, news: List<StockNewsItem>) {
        val cached = overviewCacheRepository.getCache(symbol) ?: return
        overviewCacheRepository.upsert(
            cached.copy(newsJson = StockNewsJson.toJson(news))
        )
    }

    private suspend fun loadOverviewOrNull(symbol: String): StockOverview? {
        return when (val result = getStockOverview(symbol)) {
            is Result.Success -> result.data
            is Result.Error -> null
        }
    }

    companion object {
        private const val TAG = "StockRepository"
    }
}
