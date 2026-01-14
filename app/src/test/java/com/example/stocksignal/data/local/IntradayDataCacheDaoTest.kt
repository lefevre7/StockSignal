package com.example.stocksignal.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stocksignal.data.local.dao.IntradayDataCacheDao
import com.example.stocksignal.data.local.db.StockSignalDatabase
import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class IntradayDataCacheDaoTest {

    private lateinit var database: StockSignalDatabase
    private lateinit var dao: IntradayDataCacheDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, StockSignalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.intradayDataCacheDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert and retrieve candles by date range`() = runTest {
        val symbol = "AAPL.US"
        val date1 = LocalDate.of(2026, 1, 10)
        val date2 = LocalDate.of(2026, 1, 11)
        val now = LocalDateTime.now()

        val entity1 = IntradayDataCacheEntity(
            symbol = symbol,
            date = date1,
            candlesJson = """[{"time":"2026-01-10T09:30:00","open":150.0,"high":151.0,"low":149.0,"close":150.5,"volume":1000}]""",
            createdAt = now,
            updatedAt = now
        )
        val entity2 = IntradayDataCacheEntity(
            symbol = symbol,
            date = date2,
            candlesJson = """[{"time":"2026-01-11T09:30:00","open":151.0,"high":152.0,"low":150.0,"close":151.5,"volume":1100}]""",
            createdAt = now,
            updatedAt = now
        )

        dao.upsert(entity1)
        dao.upsert(entity2)

        val result = dao.getCandlesByDateRange(symbol, date1, date2)
        assertEquals(2, result.size)
        assertEquals(date1, result[0].date)
        assertEquals(date2, result[1].date)
    }

    @Test
    fun `upsert updates existing entity`() = runTest {
        val symbol = "TSLA.US"
        val date = LocalDate.of(2026, 1, 10)
        val now = LocalDateTime.now()

        val original = IntradayDataCacheEntity(
            symbol = symbol,
            date = date,
            candlesJson = """[{"time":"2026-01-10T09:30:00","open":200.0,"high":201.0,"low":199.0,"close":200.5,"volume":500}]""",
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(original)

        val updated = original.copy(
            candlesJson = """[{"time":"2026-01-10T09:30:00","open":200.0,"high":202.0,"low":199.0,"close":201.0,"volume":600}]""",
            updatedAt = now.plusMinutes(10)
        )
        dao.upsert(updated)

        val result = dao.getCandlesByDateRange(symbol, date, date)
        assertEquals(1, result.size)
        assertEquals(updated.candlesJson, result[0].candlesJson)
    }

    @Test
    fun `getAllCandlesForSymbol returns all dates`() = runTest {
        val symbol = "MSFT.US"
        val now = LocalDateTime.now()

        repeat(5) { i ->
            val date = LocalDate.of(2026, 1, 10 + i)
            dao.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = """[{"time":"2026-01-${10 + i}T09:30:00","open":100.0,"high":101.0,"low":99.0,"close":100.5,"volume":1000}]""",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val result = dao.getAllCandlesForSymbol(symbol)
        assertEquals(5, result.size)
    }

    @Test
    fun `deleteOldData removes data before cutoff date`() = runTest {
        val symbol = "GOOGL.US"
        val now = LocalDateTime.now()
        val oldDate = LocalDate.of(2025, 1, 10)
        val recentDate = LocalDate.of(2026, 1, 10)

        dao.upsert(
            IntradayDataCacheEntity(
                symbol = symbol,
                date = oldDate,
                candlesJson = """[]""",
                createdAt = now,
                updatedAt = now
            )
        )
        dao.upsert(
            IntradayDataCacheEntity(
                symbol = symbol,
                date = recentDate,
                candlesJson = """[]""",
                createdAt = now,
                updatedAt = now
            )
        )

        val cutoff = LocalDate.of(2025, 12, 31)
        dao.deleteOldData(symbol, cutoff)

        val result = dao.getAllCandlesForSymbol(symbol)
        assertEquals(1, result.size)
        assertEquals(recentDate, result[0].date)
    }

    @Test
    fun `countDaysForSymbol returns correct count`() = runTest {
        val symbol = "NVDA.US"
        val now = LocalDateTime.now()

        repeat(10) { i ->
            dao.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = LocalDate.of(2026, 1, 1 + i),
                    candlesJson = """[]""",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val count = dao.countDaysForSymbol(symbol)
        assertEquals(10, count)
    }

    @Test
    fun `getEarliestDate returns oldest date`() = runTest {
        val symbol = "AMD.US"
        val now = LocalDateTime.now()
        val dates = listOf(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 1, 20)
        )

        dates.forEach { date ->
            dao.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = """[]""",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val earliest = dao.getEarliestDate(symbol)
        assertNotNull(earliest)
        assertEquals(LocalDate.of(2026, 1, 10), earliest)
    }

    @Test
    fun `getLatestDate returns newest date`() = runTest {
        val symbol = "NFLX.US"
        val now = LocalDateTime.now()
        val dates = listOf(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 1, 10),
            LocalDate.of(2026, 1, 20)
        )

        dates.forEach { date ->
            dao.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = """[]""",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        val latest = dao.getLatestDate(symbol)
        assertNotNull(latest)
        assertEquals(LocalDate.of(2026, 1, 20), latest)
    }

    @Test
    fun `deleteForSymbol removes all data for symbol`() = runTest {
        val symbol1 = "AAPL.US"
        val symbol2 = "MSFT.US"
        val now = LocalDateTime.now()
        val date = LocalDate.of(2026, 1, 10)

        dao.upsert(
            IntradayDataCacheEntity(symbol = symbol1, date = date, candlesJson = """[]""", createdAt = now, updatedAt = now)
        )
        dao.upsert(
            IntradayDataCacheEntity(symbol = symbol2, date = date, candlesJson = """[]""", createdAt = now, updatedAt = now)
        )

        dao.deleteForSymbol(symbol1)

        val result1 = dao.getAllCandlesForSymbol(symbol1)
        val result2 = dao.getAllCandlesForSymbol(symbol2)
        assertEquals(0, result1.size)
        assertEquals(1, result2.size)
    }

    @Test
    fun `one year boundary edge case`() = runTest {
        val symbol = "EDGE.US"
        val now = LocalDateTime.now()
        val today = LocalDate.of(2026, 1, 13)
        val oneYearAgo = today.minusYears(1)
        val justBeforeOneYear = oneYearAgo.minusDays(1)
        val justAfterOneYear = oneYearAgo.plusDays(1)

        dao.upsert(
            IntradayDataCacheEntity(symbol = symbol, date = justBeforeOneYear, candlesJson = """[]""", createdAt = now, updatedAt = now)
        )
        dao.upsert(
            IntradayDataCacheEntity(symbol = symbol, date = oneYearAgo, candlesJson = """[]""", createdAt = now, updatedAt = now)
        )
        dao.upsert(
            IntradayDataCacheEntity(symbol = symbol, date = justAfterOneYear, candlesJson = """[]""", createdAt = now, updatedAt = now)
        )

        dao.deleteOldData(symbol, oneYearAgo)

        val result = dao.getAllCandlesForSymbol(symbol)
        assertEquals(2, result.size) // oneYearAgo and justAfterOneYear should remain
    }

    @Test
    fun `getEarliestDate returns null for unknown symbol`() = runTest {
        val earliest = dao.getEarliestDate("UNKNOWN.US")
        assertNull(earliest)
    }

    @Test
    fun `getLatestDate returns null for unknown symbol`() = runTest {
        val latest = dao.getLatestDate("UNKNOWN.US")
        assertNull(latest)
    }
}
