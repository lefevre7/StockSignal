package com.example.stocksignal.data.stooq.repository

import android.util.Log
import com.example.stocksignal.data.stooq.TestRateLimiter
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.network.StooqApi
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Live integration tests for StooqRepository.
 * These tests make actual API calls to stooq.com to verify the implementation works with the real API.
 * 
 * Note: These tests require internet connection and may be slower.
 */
@Ignore("Live tests disabled - stooq.com rate limits/blocks automated requests")
class StooqRepositoryLiveTest {

    private lateinit var api: StooqApi
    private lateinit var repository: StooqRepository

    @Before
    fun setup() {
        // Mock Android Log class
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0

        // Create real Retrofit instance
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                .header("Accept", StooqApi.DEFAULT_ACCEPT)
                .header("Accept-Language", StooqApi.DEFAULT_ACCEPT_LANGUAGE)
                .header("Connection", "keep-alive")
                .header("Sec-CH-UA", StooqApi.DEFAULT_SEC_CH_UA)
                .header("Sec-CH-UA-Mobile", StooqApi.DEFAULT_SEC_CH_UA_MOBILE)
                .header("Sec-CH-UA-Platform", StooqApi.DEFAULT_SEC_CH_UA_PLATFORM)
                .header("Sec-Fetch-Dest", StooqApi.DEFAULT_SEC_FETCH_DEST)
                .header("Sec-Fetch-Mode", StooqApi.DEFAULT_SEC_FETCH_MODE)
                .header("Sec-Fetch-Site", StooqApi.DEFAULT_SEC_FETCH_SITE)
                .header("Sec-Fetch-User", StooqApi.DEFAULT_SEC_FETCH_USER)
                .header("Upgrade-Insecure-Requests", "1")
                .build()
            chain.proceed(requestWithHeaders)
        }

        // Rate limiting interceptor - uses global lock to serialize all requests across all test classes
        val rateLimitInterceptor = Interceptor { chain ->
            TestRateLimiter.withRateLimit {
                chain.proceed(chain.request())
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        api = retrofit.create(StooqApi::class.java)
        repository = StooqRepository(api)
    }

    @Test
    fun `live test - fetch single ticker data`() = runTest {
        // Given - Using a well-known stock ticker
        val ticker = "AAPL.US"
        val startDate = LocalDate.of(2024, 1, 2)
        val endDate = LocalDate.of(2024, 1, 5)

        // When
        val result = repository.getData(
            tickers = listOf(ticker),
            startDate = startDate,
            endDate = endDate
        )

        // Then
        println("Result: $result")
        assertTrue("Expected success result", result.isSuccess)
        
        val data = (result as Result.Success).data
        assertTrue("Expected data for ticker $ticker", data.containsKey(ticker))
        
        val tickerData = data[ticker]!!
        assertTrue("Expected at least one data point", tickerData.isNotEmpty())
        
        // Verify data structure
        tickerData.forEach { (date, stockData) ->
            println("Date: $date, Open: ${stockData.open}, High: ${stockData.high}, Low: ${stockData.low}, Close: ${stockData.close}, Volume: ${stockData.volume}")
            
            assertTrue("Open should be positive", stockData.open > 0)
            assertTrue("High should be positive", stockData.high > 0)
            assertTrue("Low should be positive", stockData.low > 0)
            assertTrue("Close should be positive", stockData.close > 0)
            assertTrue("High should be >= Low", stockData.high >= stockData.low)
            assertTrue("Date should match", stockData.date == date)
        }
    }

    @Test
    fun `live test - fetch multiple tickers in parallel`() = runTest {
        // Given - Multiple well-known stock tickers
        val tickers = listOf("AAPL.US", "MSFT.US")
        val startDate = LocalDate.of(2024, 1, 2)
        val endDate = LocalDate.of(2024, 1, 3)

        // When
        val result = repository.getData(
            tickers = tickers,
            startDate = startDate,
            endDate = endDate
        )

        // Then
        println("Result: $result")
        
        when (result) {
            is Result.Success -> {
                val data = result.data
                println("Successfully fetched data for ${data.size} tickers: ${data.keys}")
                
                // At least one ticker should have data
                assertTrue("Expected at least one ticker with data", data.isNotEmpty())
                
                data.forEach { (ticker, dateMap) ->
                    println("Ticker: $ticker has ${dateMap.size} data points")
                    assertTrue("Expected at least one data point for $ticker", dateMap.isNotEmpty())
                }
            }
            is Result.Error -> {
                fail("Expected success but got error: ${result.message}")
            }
        }
    }

    @Test
    fun `live test - fetch Polish stock`() = runTest {
        // Given - Polish stock ticker (as in original Python example)
        val ticker = "PKO"
        val startDate = LocalDate.of(2024, 1, 2)
        val endDate = LocalDate.of(2024, 1, 5)

        // When
        val result = repository.getData(
            tickers = listOf(ticker),
            startDate = startDate,
            endDate = endDate
        )

        // Then
        println("Result for PKO: $result")
        
        when (result) {
            is Result.Success -> {
                val data = result.data
                println("Successfully fetched data for PKO: ${data.keys}")
                assertTrue("Expected data for PKO", data.containsKey(ticker))
            }
            is Result.Error -> {
                // PKO might not be available or might have no data for these dates
                println("PKO fetch failed (may be expected): ${result.message}")
                // Just log, don't fail the test - ticker might not be available
            }
        }
    }

    @Test
    fun `live test - handle invalid ticker gracefully`() = runTest {
        // Given - One valid and one invalid ticker
        val tickers = listOf("AAPL.US", "INVALIDTICKER12345")
        val startDate = LocalDate.of(2024, 1, 2)
        val endDate = LocalDate.of(2024, 1, 3)

        // When
        val result = repository.getData(
            tickers = tickers,
            startDate = startDate,
            endDate = endDate
        )

        // Then - Should succeed with at least the valid ticker
        println("Result with invalid ticker: $result")
        when (result) {
            is Result.Success -> {
                val data = result.data
                // At least AAPL.US should be present
                assertTrue("Expected at least one valid ticker", data.isNotEmpty())
            }
            is Result.Error -> {
                // If all failed, that's also acceptable
                println("All tickers failed: ${result.message}")
            }
        }
    }

    @Test
    fun `live test - raw API call to inspect CSV format`() = runTest {
        // Given
        val ticker = "AAPL.US"
        val startDate = "20240102"
        val endDate = "20240105"

        // When
        val csvData = api.getStockData(ticker, startDate, endDate)

        // Then
        println("Raw CSV data from API:")
        println(csvData)
        println("---")
        
        assertNotNull("CSV data should not be null", csvData)
        assertTrue("CSV should contain header", csvData.contains("Date") || csvData.contains("date"))
        
        // Print first few lines to understand format
        val lines = csvData.lines().take(10)
        lines.forEach { println("Line: $it") }
    }

    @Test
    fun `live test - fetch intraday data`() = runTest {
        val ticker = "TSLA.US"

        val result = repository.getIntradayData(
            tickers = listOf(ticker),
            intervalMinutes = 10
        )

        println("Intraday result: $result")
        assertTrue("Expected success result", result.isSuccess)

        val data = (result as Result.Success).data
        assertTrue("Expected data for ticker $ticker", data.containsKey(ticker))

        val tickerData = data.getValue(ticker)
        assertTrue("Expected at least one intraday data point", tickerData.isNotEmpty())

        val keys = tickerData.keys.toList()
        assertEquals("Expected keys to be sorted ascending", keys.sorted(), keys)

        tickerData.forEach { (dateTime, stockData) ->
            println(
                "DateTime: $dateTime, Open: ${stockData.open}, High: ${stockData.high}, " +
                    "Low: ${stockData.low}, Close: ${stockData.close}, Vol: ${stockData.volume}, " +
                    "OI: ${stockData.openInterest}, Annotation: ${stockData.annotation}"
            )

            assertTrue("DateTime should match", stockData.dateTime == dateTime)
            assertTrue("Open should be positive", stockData.open > 0)
            assertTrue("High should be positive", stockData.high > 0)
            assertTrue("Low should be positive", stockData.low > 0)
            assertTrue("Close should be positive", stockData.close > 0)
            assertTrue("High should be >= Low", stockData.high >= stockData.low)
        }

        // Sanity: ensure at least one timestamp is recent-ish (within the last few years).
        val latest: LocalDateTime = tickerData.keys.maxOrNull()!!
        assertTrue("Latest intraday timestamp should be after 2020", latest.isAfter(LocalDateTime.of(2020, 1, 1, 0, 0)))
    }
}
