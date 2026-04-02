package com.example.stocksignal.data.stooq.repository

import android.util.Log
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.network.StooqApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException
import java.io.EOFException
import java.io.IOException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit tests for StooqRepository.
 * Tests data fetching, parsing, and error handling.
 */
class StooqRepositoryTest {

    private lateinit var api: StooqApi
    private lateinit var repository: StooqRepository

    @Before
    fun setup() {
        // Mock Android Log class
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        
        api = mockk()
        repository = StooqRepository(api)
    }

    @Test
    fun `getData returns success with valid CSV data`() = runTest {
        // Given
        val ticker = "PKO"
        val csvData = """
            Date,Open,High,Low,Close,Volume
            2020-04-01,20.8531,20.9742,20.4063,20.6669,3176696
            2020-04-02,20.7600,20.8904,19.7546,20.3225,4157717
        """.trimIndent()

        coEvery {
            api.getStockData(ticker, "20200401", "20200402", "d")
        } returns csvData

        // When
        val result = repository.getData(
            tickers = listOf(ticker),
            startDate = LocalDate.of(2020, 4, 1),
            endDate = LocalDate.of(2020, 4, 2)
        )

        // Then
        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertTrue(data.containsKey(ticker))
        assertEquals(2, data[ticker]?.size)

        val firstDay = data[ticker]?.get(LocalDate.of(2020, 4, 1))
        assertNotNull(firstDay)
        assertEquals(20.8531, firstDay?.open ?: 0.0, 0.0001)
        assertEquals(20.9742, firstDay?.high ?: 0.0, 0.0001)
        assertEquals(20.4063, firstDay?.low ?: 0.0, 0.0001)
        assertEquals(20.6669, firstDay?.close ?: 0.0, 0.0001)
        assertEquals(3176696L, firstDay?.volume ?: 0L)
    }

    @Test
    fun `getData fetches multiple tickers in parallel`() = runTest {
        // Given
        val tickers = listOf("PKO", "TPE")
        val csvDataPKO = """
            Date,Open,High,Low,Close,Volume
            2020-04-01,20.8531,20.9742,20.4063,20.6669,3176696
        """.trimIndent()

        val csvDataTPE = """
            Date,Open,High,Low,Close,Volume
            2020-04-01,1.100,1.113,1.082,1.091,4377953
        """.trimIndent()

        coEvery {
            api.getStockData("PKO", "20200401", "20200401", "d")
        } returns csvDataPKO

        coEvery {
            api.getStockData("TPE", "20200401", "20200401", "d")
        } returns csvDataTPE

        // When
        val result = repository.getData(
            tickers = tickers,
            startDate = LocalDate.of(2020, 4, 1),
            endDate = LocalDate.of(2020, 4, 1)
        )

        // Then
        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertEquals(2, data.size)
        assertTrue(data.containsKey("PKO"))
        assertTrue(data.containsKey("TPE"))

        coVerify(exactly = 1) { api.getStockData("PKO", any(), any(), any()) }
        coVerify(exactly = 1) { api.getStockData("TPE", any(), any(), any()) }
    }

    @Test
    fun `getData handles partial failures gracefully`() = runTest {
        // Given
        val tickers = listOf("PKO", "INVALID")
        val csvDataPKO = """
            Date,Open,High,Low,Close,Volume
            2020-04-01,20.8531,20.9742,20.4063,20.6669,3176696
        """.trimIndent()

        coEvery {
            api.getStockData("PKO", "20200401", "20200401", "d")
        } returns csvDataPKO

        coEvery {
            api.getStockData("INVALID", "20200401", "20200401", "d")
        } throws Exception("Ticker not found")

        // When
        val result = repository.getData(
            tickers = tickers,
            startDate = LocalDate.of(2020, 4, 1),
            endDate = LocalDate.of(2020, 4, 1)
        )

        // Then
        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertTrue(data.containsKey("PKO"))
        assertFalse(data.containsKey("INVALID"))
    }

    @Test
    fun `getData returns error when all tickers fail`() = runTest {
        // Given
        val tickers = listOf("INVALID1", "INVALID2")

        coEvery {
            api.getStockData(any(), any(), any(), any())
        } throws Exception("Network error")

        // When
        val result = repository.getData(
            tickers = tickers,
            startDate = LocalDate.of(2020, 4, 1),
            endDate = LocalDate.of(2020, 4, 1)
        )

        // Then
        assertTrue(result.isError)
        val error = result as Result.Error
        assertTrue(error.message.contains("Failed to fetch data for all"))
    }

    @Test
    fun `parseCsvData handles empty volume correctly`() = runTest {
        // Given
        val ticker = "TEST"
        val csvData = """
            Date,Open,High,Low,Close,Volume
            2020-04-01,10.5,11.0,10.0,10.8,0
        """.trimIndent()

        coEvery {
            api.getStockData(ticker, "20200401", "20200401", "d")
        } returns csvData

        // When
        val result = repository.getData(
            tickers = listOf(ticker),
            startDate = LocalDate.of(2020, 4, 1),
            endDate = LocalDate.of(2020, 4, 1)
        )

        // Then
        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data[ticker]
        val stockData = data?.get(LocalDate.of(2020, 4, 1))
        assertEquals(0L, stockData?.volume)
    }

    @Test
    fun `getData formats dates correctly`() = runTest {
        // Given
        val ticker = "PKO"
        val csvData = """
            Date,Open,High,Low,Close,Volume
            2020-04-01,20.0,21.0,19.0,20.5,1000000
        """.trimIndent()

        coEvery {
            api.getStockData(ticker, "20200401", "20221031", "d")
        } returns csvData

        // When
        repository.getData(
            tickers = listOf(ticker),
            startDate = LocalDate.of(2020, 4, 1),
            endDate = LocalDate.of(2022, 10, 31)
        )

        // Then
        coVerify {
            api.getStockData(
                ticker = ticker,
                startDate = "20200401",
                endDate = "20221031",
                interval = "d"
            )
        }
    }

    @Test
    fun `getIntradayData returns success with valid intraday response`() = runTest {
        val ticker = "TSLA.US"
        // Real stooq intraday format: no header row, data starts after ~TICKER_NAME~
        // Format: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume
        val rawResponse = """
            TESLA INC (NASDAQ: TSLA.US) | Ticker Rank: 65 (-4) | ★ 6158~TESLA INC~20251125,185000,420.2022,420.35,418.6,418.95,1395217
            20251125,190000,418.98,419.26,417.2101,417.51,972087
        """.trimIndent()

        coEvery { api.getIntradayData(ticker.lowercase(), 10) } returns rawResponse

        val result = repository.getIntradayData(tickers = listOf(ticker))

        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertTrue(data.containsKey(ticker))

        val tickerData = data.getValue(ticker)
        assertEquals(2, tickerData.size)

        val keys = tickerData.keys.toList()
        assertEquals(keys.sorted(), keys)

        val first = tickerData.getValue(LocalDateTime.of(2025, 11, 25, 18, 50, 0))
        assertEquals(420.2022, first.open, 0.0001)
        assertEquals(420.35, first.high, 0.0001)
        assertEquals(418.6, first.low, 0.0001)
        assertEquals(418.95, first.close, 0.0001)
        assertEquals(1395217L, first.volume)
        assertNull(first.openInterest)
        assertNull(first.annotation)
    }

    @Test
    fun `getIntradayData pads time and applies range filters`() = runTest {
        val ticker = "TEST"
        // Real stooq format: no header row
        val rawResponse = """
            X~TEST~20251125,93000,10.0,11.0,9.5,10.5,100
            20251125,093500,10.5,10.9,10.2,10.8,200
        """.trimIndent()

        coEvery { api.getIntradayData(ticker.lowercase(), 10) } returns rawResponse

        val filtered = repository.getIntradayData(
            tickers = listOf(ticker),
            intervalMinutes = 10,
            start = LocalDateTime.of(2025, 11, 25, 9, 35, 0),
            end = LocalDateTime.of(2025, 11, 25, 9, 35, 0)
        )

        assertTrue(filtered.isSuccess)
        val data = (filtered as Result.Success).data.getValue(ticker)
        assertEquals(1, data.size)
        assertNotNull(data[LocalDateTime.of(2025, 11, 25, 9, 35, 0)])
    }

    @Test
    fun `getIntradayData handles partial failures gracefully`() = runTest {
        // Real stooq format: no header row
        val rawResponse = """
            X~OK~20251125,185000,10.0,11.0,9.5,10.5,100
        """.trimIndent()

        coEvery { api.getIntradayData("ok", 10) } returns rawResponse
        coEvery { api.getIntradayData("fail", 10) } throws Exception("Network error")

        val result = repository.getIntradayData(tickers = listOf("OK", "FAIL"))

        assertTrue(result.isSuccess)
        val data = (result as Result.Success).data
        assertEquals(1, data.size)
        assertTrue(data.containsKey("OK"))
        assertFalse(data.containsKey("FAIL"))
    }

    @Test
    fun `getIntradayData stops batch after timeout failure`() = runTest {
        coEvery { api.getIntradayData("a", 10) } throws SocketTimeoutException("timeout")

        val result = repository.getIntradayData(tickers = listOf("A", "B", "C"))

        assertTrue(result.isError)
        val error = result as Result.Error
        assertTrue(error.exception is SocketTimeoutException)
        coVerify(exactly = 1) { api.getIntradayData("a", 10) }
        coVerify(exactly = 0) { api.getIntradayData("b", 10) }
        coVerify(exactly = 0) { api.getIntradayData("c", 10) }
    }

    @Test
    fun `getIntradayData returns error when no tickers yield data`() = runTest {
        coEvery { api.getIntradayData(any(), any()) } returns "no header here"

        val result = repository.getIntradayData(tickers = listOf("A"))

        assertTrue(result.isError)
        val error = result as Result.Error
        assertTrue(error.message.contains("Failed to fetch intraday data for all"))
    }

    @Test
    fun `getPremarketQuotes stops batch after timeout failure`() = runTest {
        coEvery { api.getQuotePage("a") } throws SocketTimeoutException("timeout")

        val result = repository.getPremarketQuotes(listOf("A", "B"))

        assertTrue(result.isError)
        val error = result as Result.Error
        assertTrue(error.exception is SocketTimeoutException)
        coVerify(exactly = 1) { api.getQuotePage("a") }
        coVerify(exactly = 0) { api.getQuotePage("b") }
    }

    @Test
    fun `getData stops batch after timeout failure and preserves exception`() = runTest {
        coEvery {
            api.getStockData("A", "20240101", "20240105", "d")
        } throws SocketTimeoutException("timeout")

        val result = repository.getData(
            tickers = listOf("A", "B"),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 1, 5)
        )

        assertTrue(result.isError)
        val error = result as Result.Error
        assertTrue(error.exception is SocketTimeoutException)
        coVerify(exactly = 1) { api.getStockData("A", "20240101", "20240105", "d") }
        coVerify(exactly = 0) { api.getStockData("B", any(), any(), any()) }
    }

    // ============== Premarket retry for transient errors ==============

    @Test
    fun `getPremarketQuotes retries on gzip error and succeeds`() = runTest {
        val gzipError = IOException("gzip finished without exhausting source")
        val validHtml = "<html><table><tr><td>Bid <span>10.50</span></td></tr><tr><td>Ask <span>10.60</span></td></tr></table></html>"
        var callCount = 0
        coEvery { api.getQuotePage("a") } coAnswers {
            callCount++
            if (callCount == 1) throw gzipError
            validHtml
        }

        val result = repository.getPremarketQuotes(listOf("A"))

        assertTrue("Expected success after gzip retry, got: $result", result.isSuccess)
        coVerify(exactly = 2) { api.getQuotePage("a") }
    }

    @Test
    fun `getPremarketQuotes treats UnknownHostException as terminal - no retry`() = runTest {
        coEvery { api.getQuotePage("a") } throws UnknownHostException("Unable to resolve host")

        val result = repository.getPremarketQuotes(listOf("A"))

        assertTrue(result.isError)
        // UnknownHostException is terminal (DNS failure), not transient — no retry
        coVerify(exactly = 1) { api.getQuotePage("a") }
    }

    @Test
    fun `getPremarketQuotes retries on EOFException once then fails`() = runTest {
        coEvery { api.getQuotePage("a") } throws EOFException("unexpected end of stream")

        val result = repository.getPremarketQuotes(listOf("A"))

        assertTrue(result.isError)
        // Retry count is 2 (1 retry), not 3
        coVerify(exactly = 2) { api.getQuotePage("a") }
    }

    @Test
    fun `getPremarketQuotes does not retry SocketTimeoutException`() = runTest {
        coEvery { api.getQuotePage("a") } throws SocketTimeoutException("timeout")

        val result = repository.getPremarketQuotes(listOf("A"))

        assertTrue(result.isError)
        // SocketTimeoutException is terminal, not transient — no retry
        coVerify(exactly = 1) { api.getQuotePage("a") }
    }

    // ============== Batch-level consecutive transient error tracking ==============

    @Test
    fun `getPremarketQuotes stops batch after consecutive transient errors`() = runTest {
        // Both tickers fail with gzip error → batch should stop at 2nd ticker
        coEvery { api.getQuotePage("a") } throws IOException("gzip finished without exhausting source")
        coEvery { api.getQuotePage("b") } throws EOFException("unexpected end of stream")

        val result = repository.getPremarketQuotes(listOf("A", "B", "C"))

        assertTrue(result.isError)
        // A retries once (2 calls), B retries once (2 calls), C never called
        coVerify(exactly = 2) { api.getQuotePage("a") }
        coVerify(exactly = 2) { api.getQuotePage("b") }
        coVerify(exactly = 0) { api.getQuotePage("c") }
    }

    @Test
    fun `getPremarketQuotes consecutive transient counter resets on success`() = runTest {
        val validHtml = "<html><table><tr><td>Bid <span>10.50</span></td></tr><tr><td>Ask <span>10.60</span></td></tr></table></html>"
        // A fails with gzip (transient count=1), B succeeds (reset to 0), C fails with EOF (count=1) → no batch stop
        coEvery { api.getQuotePage("a") } throws IOException("gzip finished without exhausting source")
        coEvery { api.getQuotePage("b") } returns validHtml
        coEvery { api.getQuotePage("c") } throws EOFException("unexpected end of stream")

        val result = repository.getPremarketQuotes(listOf("A", "B", "C"))

        // B succeeds, so result is success (partial data)
        assertTrue(result.isSuccess)
        // A called 2x (1 retry), B called 1x, C called 2x (1 retry)
        coVerify(exactly = 2) { api.getQuotePage("a") }
        coVerify(exactly = 1) { api.getQuotePage("b") }
        coVerify(exactly = 2) { api.getQuotePage("c") }
    }

    @Test
    fun `getPremarketQuotes stops batch after UnknownHostException - terminal`() = runTest {
        coEvery { api.getQuotePage("a") } throws UnknownHostException("Unable to resolve host")

        val result = repository.getPremarketQuotes(listOf("A", "B", "C"))

        assertTrue(result.isError)
        coVerify(exactly = 1) { api.getQuotePage("a") }
        coVerify(exactly = 0) { api.getQuotePage("b") }
        coVerify(exactly = 0) { api.getQuotePage("c") }
    }

    @Test
    fun `getIntradayData stops batch after consecutive transient errors`() = runTest {
        coEvery { api.getIntradayData("a", 10) } throws EOFException("unexpected end of stream")
        coEvery { api.getIntradayData("b", 10) } throws IOException("gzip finished without exhausting source")

        val result = repository.getIntradayData(tickers = listOf("A", "B", "C"))

        assertTrue(result.isError)
        coVerify(exactly = 1) { api.getIntradayData("a", 10) }
        coVerify(exactly = 1) { api.getIntradayData("b", 10) }
        coVerify(exactly = 0) { api.getIntradayData("c", 10) }
    }

    @Test
    fun `getIntradayData stops batch on UnknownHostException`() = runTest {
        coEvery { api.getIntradayData("a", 10) } throws UnknownHostException("Unable to resolve host")

        val result = repository.getIntradayData(tickers = listOf("A", "B"))

        assertTrue(result.isError)
        coVerify(exactly = 1) { api.getIntradayData("a", 10) }
        coVerify(exactly = 0) { api.getIntradayData("b", 10) }
    }

    @Test
    fun `getData stops batch after consecutive transient errors`() = runTest {
        coEvery { api.getStockData("A", any(), any(), any()) } throws EOFException("unexpected end")
        coEvery { api.getStockData("B", any(), any(), any()) } throws IOException("gzip finished without exhausting source")

        val result = repository.getData(
            tickers = listOf("A", "B", "C"),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 1, 5)
        )

        assertTrue(result.isError)
        coVerify(exactly = 1) { api.getStockData("A", any(), any(), any()) }
        coVerify(exactly = 1) { api.getStockData("B", any(), any(), any()) }
        coVerify(exactly = 0) { api.getStockData("C", any(), any(), any()) }
    }

    @Test
    fun `getData consecutive transient counter resets on success`() = runTest {
        val csvData = "Date,Open,High,Low,Close,Volume\n2024-01-01,10.0,11.0,9.0,10.5,1000"
        coEvery { api.getStockData("A", any(), any(), any()) } throws EOFException("eof")
        coEvery { api.getStockData("B", any(), any(), any()) } returns csvData
        coEvery { api.getStockData("C", any(), any(), any()) } throws IOException("gzip finished without exhausting source")
        coEvery { api.getStockData("D", any(), any(), any()) } returns csvData

        val result = repository.getData(
            tickers = listOf("A", "B", "C", "D"),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 1, 1)
        )

        // B and D succeed, so result is success
        assertTrue(result.isSuccess)
        // All tickers processed — counter reset by successes between failures
        coVerify(exactly = 1) { api.getStockData("A", any(), any(), any()) }
        coVerify(exactly = 1) { api.getStockData("B", any(), any(), any()) }
        coVerify(exactly = 1) { api.getStockData("C", any(), any(), any()) }
        coVerify(exactly = 1) { api.getStockData("D", any(), any(), any()) }
    }
}
