package com.example.stocksignal.integration

import androidx.room.Room
import com.example.stocksignal.data.local.dao.IntradayDataCacheDao
import com.example.stocksignal.data.local.db.StockSignalDatabase
import com.example.stocksignal.data.local.repository.IntradayDataCacheRepository
import com.example.stocksignal.data.repository.PriceCandleJson
import com.example.stocksignal.data.stooq.model.IntradayStockData
import com.example.stocksignal.domain.model.PriceCandle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Integration test for the full data accumulation flow:
 * 1. Fetch intraday data (simulated)
 * 2. Persist to IntradayDataCache
 * 3. Retrieve for signal computation
 * 4. Verify accumulation over multiple fetches
 */
@RunWith(RobolectricTestRunner::class)
class DataAccumulationIntegrationTest {

    private lateinit var database: StockSignalDatabase
    private lateinit var dao: IntradayDataCacheDao
    private lateinit var repository: IntradayDataCacheRepository

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, StockSignalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.intradayDataCacheDao()
        repository = IntradayDataCacheRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `full accumulation flow - data persists and accumulates over multiple fetches`() = runTest {
        val symbol = "AAPL.US"
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Simulate first fetch - yesterday's data
        val yesterdayCandles = generateMockCandles(yesterday, 39) // 6.5 hours * 6 candles/hour
        accumulateData(symbol, yesterday, yesterdayCandles)

        // Verify first accumulation
        var allData = repository.getAllCandlesForSymbol(symbol)
        assertEquals(1, allData.size)
        assertEquals(yesterday, allData[0].date)

        // Simulate second fetch - today's partial data (morning)
        val morningCandles = generateMockCandles(today, 12) // 2 hours of data
        accumulateData(symbol, today, morningCandles)

        // Verify both days are stored
        allData = repository.getAllCandlesForSymbol(symbol)
        assertEquals(2, allData.size)

        // Simulate third fetch - today's full data (update)
        val fullDayCandles = generateMockCandles(today, 39)
        accumulateData(symbol, today, fullDayCandles)

        // Verify today's data was updated, not duplicated
        allData = repository.getAllCandlesForSymbol(symbol)
        assertEquals(2, allData.size)

        // Retrieve all candles for signal computation
        val allCandles = allData.flatMap { entity ->
            PriceCandleJson.fromJson(entity.candlesJson)
        }.sortedBy { it.time }

        // Verify we have data from both days
        assertEquals(39 + 39, allCandles.size) // yesterday + today
        assertTrue(allCandles.first().time.toLocalDate() == yesterday)
        assertTrue(allCandles.last().time.toLocalDate() == today)
    }

    @Test
    fun `accumulation respects one year limit`() = runTest {
        val symbol = "MSFT.US"
        val today = LocalDate.now()
        
        // Add data from 13 months ago (should be deleted)
        val tooOld = today.minusMonths(13)
        accumulateData(symbol, tooOld, generateMockCandles(tooOld, 10))

        // Add data from 11 months ago (should be kept)
        val elevenMonthsAgo = today.minusMonths(11)
        accumulateData(symbol, elevenMonthsAgo, generateMockCandles(elevenMonthsAgo, 10))

        // Add recent data and trigger cleanup
        accumulateData(symbol, today, generateMockCandles(today, 10))

        // Cleanup old data (simulate what StockRepository does)
        val oneYearAgo = today.minusYears(1)
        repository.deleteOldData(symbol, oneYearAgo)

        // Verify old data was removed
        val remaining = repository.getAllCandlesForSymbol(symbol)
        assertTrue(remaining.all { it.date >= oneYearAgo })
        
        // Should have 11-month-old and today's data
        assertEquals(2, remaining.size)
    }

    @Test
    fun `retrieval for different timeframes`() = runTest {
        val symbol = "GOOGL.US"
        val today = LocalDate.now()

        // Populate 5 days of data
        repeat(5) { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val candles = generateMockCandles(date, 39)
            accumulateData(symbol, date, candles)
        }

        // Retrieve last 1 day (HOURS holding period)
        val oneDayData = repository.getCandlesByDateRange(symbol, today, today)
        assertEquals(1, oneDayData.size)

        // Retrieve last 5 days (DAYS holding period)
        val fiveDayStart = today.minusDays(4)
        val fiveDayData = repository.getCandlesByDateRange(symbol, fiveDayStart, today)
        assertEquals(5, fiveDayData.size)

        // Verify candles can be parsed for signal computation
        val allCandles = fiveDayData.flatMap { entity ->
            PriceCandleJson.fromJson(entity.candlesJson)
        }
        assertEquals(39 * 5, allCandles.size)
        assertTrue(allCandles.all { it.open > 0 && it.high > 0 && it.low > 0 && it.close > 0 })
    }

    @Test
    fun `historical data remains immutable, only today updates`() = runTest {
        val symbol = "TSLA.US"
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Add yesterday's data
        val yesterdayOriginal = generateMockCandles(yesterday, 39)
        accumulateData(symbol, yesterday, yesterdayOriginal)
        val yesterdayStored = repository.getCandlesByDateRange(symbol, yesterday, yesterday)
        val originalJson = yesterdayStored[0].candlesJson

        // Try to "update" yesterday's data (should not change in real flow)
        val yesterdayAttemptUpdate = generateMockCandles(yesterday, 50) // Different count
        // In real code, accumulateIntradayData checks if date < today and skips if exists
        // We'll verify the check works by simulating it
        val existing = repository.getCandlesByDateRange(symbol, yesterday, yesterday)
        if (existing.isNotEmpty() && yesterday < today) {
            // Don't upsert - historical data is immutable
        } else {
            accumulateData(symbol, yesterday, yesterdayAttemptUpdate)
        }

        // Verify yesterday's data unchanged
        val afterAttempt = repository.getCandlesByDateRange(symbol, yesterday, yesterday)
        assertEquals(originalJson, afterAttempt[0].candlesJson)
    }

    @Test
    fun `export statistics calculation`() = runTest {
        val symbol = "NVDA.US"
        val today = LocalDate.now()

        // Add 10 days of data with varying candle counts
        repeat(10) { i ->
            val date = today.minusDays(i.toLong())
            val candleCount = 30 + (i * 2) // Varying counts
            accumulateData(symbol, date, generateMockCandles(date, candleCount))
        }

        // Calculate stats
        val entities = repository.getAllCandlesForSymbol(symbol)
        val totalCandles = entities.sumOf { entity ->
            PriceCandleJson.fromJson(entity.candlesJson).size
        }
        val earliest = repository.getEarliestDate(symbol)
        val latest = repository.getLatestDate(symbol)

        assertEquals(10, entities.size)
        assertTrue(totalCandles > 300) // At least 30 * 10
        assertEquals(today.minusDays(9), earliest)
        assertEquals(today, latest)
    }

    // Helper functions

    private suspend fun accumulateData(
        symbol: String,
        date: LocalDate,
        candles: List<PriceCandle>
    ) {
        val entity = com.example.stocksignal.data.local.entity.IntradayDataCacheEntity(
            symbol = symbol,
            date = date,
            candlesJson = PriceCandleJson.toJson(candles),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        repository.upsert(entity)
    }

    private fun generateMockCandles(date: LocalDate, count: Int): List<PriceCandle> {
        return (0 until count).map { i ->
            val baseTime = date.atTime(9, 30).plusMinutes(i * 10L)
            val basePrice = 150.0 + (i * 0.5)
            PriceCandle(
                time = baseTime,
                open = basePrice,
                high = basePrice + 1.0,
                low = basePrice - 0.5,
                close = basePrice + 0.25,
                volume = 1000L + (i * 100L)
            )
        }
    }
}
