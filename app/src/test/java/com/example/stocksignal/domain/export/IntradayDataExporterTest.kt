package com.example.stocksignal.domain.export

import androidx.room.Room
import com.example.stocksignal.data.local.dao.IntradayDataCacheDao
import com.example.stocksignal.data.local.db.StockSignalDatabase
import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
import com.example.stocksignal.data.local.repository.IntradayDataCacheRepository
import com.example.stocksignal.data.repository.PriceCandleJson
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
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class IntradayDataExporterTest {

    private lateinit var database: StockSignalDatabase
    private lateinit var repository: IntradayDataCacheRepository
    private lateinit var exporter: IntradayDataExporter
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, StockSignalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.intradayDataCacheDao()
        repository = IntradayDataCacheRepository(dao)
        exporter = IntradayDataExporter(repository)
        
        tempDir = context.cacheDir.resolve("test_exports").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        database.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `export to CSV with valid data`() = runTest {
        val symbol = "AAPL.US"
        val date = LocalDate.of(2026, 1, 10)
        val candles = generateTestCandles(date, 10)

        // Store test data
        repository.upsert(
            IntradayDataCacheEntity(
                symbol = symbol,
                date = date,
                candlesJson = PriceCandleJson.toJson(candles),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        // Export to CSV
        val outputFile = File(tempDir, "test_export.csv")
        val result = exporter.exportToCSV(symbol, outputFile)

        // Verify success
        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(outputFile.absolutePath, success.filePath)
        assertEquals(10, success.rowCount)
        assertEquals(date, success.dateRange.first)
        assertEquals(date, success.dateRange.second)

        // Verify file contents
        assertTrue(outputFile.exists())
        val lines = outputFile.readLines()
        assertEquals(11, lines.size) // Header + 10 data rows
        assertEquals("Timestamp,Open,High,Low,Close,Volume", lines[0])
        assertTrue(lines[1].startsWith("2026-01-10T09:30:00"))
    }

    @Test
    fun `export multiple days of data`() = runTest {
        val symbol = "MSFT.US"
        val today = LocalDate.of(2026, 1, 13)

        // Store 3 days of data
        repeat(3) { i ->
            val date = today.minusDays(i.toLong())
            val candles = generateTestCandles(date, 5)
            repository.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = PriceCandleJson.toJson(candles),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        // Export
        val outputFile = File(tempDir, "multi_day_export.csv")
        val result = exporter.exportToCSV(symbol, outputFile)

        // Verify
        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(15, success.rowCount) // 3 days * 5 candles
        
        val lines = outputFile.readLines()
        assertEquals(16, lines.size) // Header + 15 rows
        
        // Verify data is sorted chronologically
        assertTrue(lines[1].contains("2026-01-11")) // Earliest
        assertTrue(lines[15].contains("2026-01-13")) // Latest
    }

    @Test
    fun `export returns NoData when no data available`() = runTest {
        val symbol = "UNKNOWN.US"
        val outputFile = File(tempDir, "no_data.csv")
        
        val result = exporter.exportToCSV(symbol, outputFile)
        
        assertTrue(result is ExportResult.NoData)
    }

    @Test
    fun `export statistics calculation`() = runTest {
        val symbol = "GOOGL.US"
        val today = LocalDate.of(2026, 1, 13)

        // Store varying amounts of data over 5 days
        repeat(5) { i ->
            val date = today.minusDays(i.toLong())
            val candleCount = 10 + (i * 2)
            val candles = generateTestCandles(date, candleCount)
            repository.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = PriceCandleJson.toJson(candles),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        // Get stats
        val stats = exporter.getExportStats(symbol)

        assertEquals(symbol, stats.symbol)
        assertEquals(5, stats.daysAvailable)
        assertEquals(10 + 12 + 14 + 16 + 18, stats.totalCandles)
        assertEquals(today.minusDays(4), stats.earliestDate)
        assertEquals(today, stats.latestDate)
    }

    @Test
    fun `export stats returns empty for unknown symbol`() = runTest {
        val stats = exporter.getExportStats("UNKNOWN.US")
        
        assertEquals("UNKNOWN.US", stats.symbol)
        assertEquals(0, stats.daysAvailable)
        assertEquals(0, stats.totalCandles)
        assertEquals(null, stats.earliestDate)
        assertEquals(null, stats.latestDate)
    }

    @Test
    fun `CSV format is correct`() = runTest {
        val symbol = "TSLA.US"
        val date = LocalDate.of(2026, 1, 10)
        val candle = PriceCandle(
            time = date.atTime(9, 30),
            open = 200.0,
            high = 205.5,
            low = 199.0,
            close = 203.25,
            volume = 150000
        )

        repository.upsert(
            IntradayDataCacheEntity(
                symbol = symbol,
                date = date,
                candlesJson = PriceCandleJson.toJson(listOf(candle)),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        val outputFile = File(tempDir, "format_test.csv")
        exporter.exportToCSV(symbol, outputFile)

        val lines = outputFile.readLines()
        val dataRow = lines[1]
        assertEquals("2026-01-10T09:30:00,200.0,205.5,199.0,203.25,150000", dataRow)
    }

    @Test
    fun `export handles large dataset`() = runTest {
        val symbol = "NVDA.US"
        val today = LocalDate.of(2026, 1, 13)

        // Simulate 30 days of full data (~39 candles per day)
        repeat(30) { i ->
            val date = today.minusDays(i.toLong())
            val candles = generateTestCandles(date, 39)
            repository.upsert(
                IntradayDataCacheEntity(
                    symbol = symbol,
                    date = date,
                    candlesJson = PriceCandleJson.toJson(candles),
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        }

        val outputFile = File(tempDir, "large_export.csv")
        val result = exporter.exportToCSV(symbol, outputFile)

        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals(30 * 39, success.rowCount)
        assertTrue(outputFile.length() > 0)
    }

    private fun generateTestCandles(date: LocalDate, count: Int): List<PriceCandle> {
        return (0 until count).map { i ->
            PriceCandle(
                time = date.atTime(9, 30).plusMinutes(i * 10L),
                open = 150.0 + i,
                high = 151.0 + i,
                low = 149.0 + i,
                close = 150.5 + i,
                volume = 1000L + (i * 100L)
            )
        }
    }
}
