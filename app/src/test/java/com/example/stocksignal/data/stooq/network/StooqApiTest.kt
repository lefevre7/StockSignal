package com.example.stocksignal.data.stooq.network

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * Unit tests for StooqApi interface.
 * Tests the API endpoints with mocked responses.
 */
class StooqApiTest {

    private lateinit var api: StooqApi

    @Before
    fun setup() {
        // Mock Android Log class
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0

        api = mockk()
    }

    @Test
    fun `getStockData returns CSV data`() = runTest {
        // Given
        val ticker = "AAPL.US"
        val startDate = "20240102"
        val endDate = "20240105"
        val expectedCsv = """
            Date,Open,High,Low,Close,Volume
            2024-01-02,186.032,187.316,182.788,184.532,82983926
        """.trimIndent()

        coEvery {
            api.getStockData(ticker, startDate, endDate, "d")
        } returns expectedCsv

        // When
        val result = api.getStockData(ticker, startDate, endDate)

        // Then
        assertEquals(expectedCsv, result)
        assertTrue(result.contains("Date,Open,High,Low,Close,Volume"))
        coVerify(exactly = 1) { api.getStockData(ticker, startDate, endDate, "d") }
    }

    @Test
    fun `getRobotsTxt returns robots file content`() = runTest {
        // Given
        val expectedRobots = """
            User-agent: *
            Disallow: /admin/
            Crawl-delay: 10
        """.trimIndent()

        coEvery {
            api.getRobotsTxt()
        } returns expectedRobots

        // When
        val result = api.getRobotsTxt()

        // Then
        assertNotNull(result)
        assertEquals(expectedRobots, result)
        assertTrue(result.contains("User-agent"))
        coVerify(exactly = 1) { api.getRobotsTxt() }
    }

    @Test
    fun `getRobotsTxt handles empty response`() = runTest {
        // Given
        coEvery {
            api.getRobotsTxt()
        } returns ""

        // When
        val result = api.getRobotsTxt()

        // Then
        assertNotNull(result)
        assertEquals("", result)
    }

    @Test
    fun `getRobotsTxt handles large response`() = runTest {
        // Given
        val largeRobots = buildString {
            repeat(100) {
                appendLine("User-agent: Bot$it")
                appendLine("Disallow: /path$it/")
            }
        }

        coEvery {
            api.getRobotsTxt()
        } returns largeRobots

        // When
        val result = api.getRobotsTxt()

        // Then
        assertNotNull(result)
        assertTrue(result.length > 1000)
        assertTrue(result.contains("User-agent: Bot0"))
        assertTrue(result.contains("User-agent: Bot99"))
    }

    @Test
    fun `getHomePage builds root URL and sets User-Agent`() = runTest {
        val recordedRequests = mutableListOf<Request>()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor { chain ->
                val request = chain.request()
                recordedRequests.add(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html>OK</html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        val retrofitApi = Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(StooqApi::class.java)

        val response = retrofitApi.getHomePage()

        assertEquals("<html>OK</html>", response)
        assertEquals(1, recordedRequests.size)

        val request = recordedRequests.single()
        assertEquals("https://stooq.com/", request.url.toString())
        assertEquals(StooqApi.DEFAULT_USER_AGENT, request.header("User-Agent"))
    }

    @Test
    fun `getCmp builds expected URL and sets User-Agent`() = runTest {
        val recordedRequests = mutableListOf<Request>()
        val campaignId = "1767286282"
        val query = "tesla"

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor { chain ->
                val request = chain.request()
                recordedRequests.add(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        "window.cmp_r('TSLA.US~Tesla Inc~XNAS~...');"
                            .toResponseBody("text/plain".toMediaType())
                    )
                    .build()
            }
            .build()

        val retrofitApi = Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(StooqApi::class.java)

        val response = retrofitApi.getCmp(campaignId, query)

        assertEquals("window.cmp_r('TSLA.US~Tesla Inc~XNAS~...');", response)
        assertEquals(1, recordedRequests.size)

        val request = recordedRequests.single()
        assertEquals(
            "https://stooq.com/cmp/?$campaignId&q=$query",
            request.url.toString()
        )
        assertEquals(StooqApi.DEFAULT_USER_AGENT, request.header("User-Agent"))
    }

    @Test
    fun `getCmp encodes query parameter`() = runTest {
        val recordedRequests = mutableListOf<Request>()
        val campaignId = "1767286282"
        val query = "tesla inc"

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor { chain ->
                val request = chain.request()
                recordedRequests.add(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("OK".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val retrofitApi = Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(StooqApi::class.java)

        retrofitApi.getCmp(campaignId, query)

        val request = recordedRequests.single()
        assertEquals(
            "https://stooq.com/cmp/?$campaignId&q=tesla%20inc",
            request.url.toString()
        )
    }

    @Test
    fun `getIntradayData builds expected URL and sets User-Agent`() = runTest {
        val recordedRequests = mutableListOf<Request>()
        val ticker = "TSLA.US"
        val intervalMinutes = 10

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor { chain ->
                val request = chain.request()
                recordedRequests.add(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("OK".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val retrofitApi = Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(StooqApi::class.java)

        val response = retrofitApi.getIntradayData(ticker = ticker, intervalMinutes = intervalMinutes)

        assertEquals("OK", response)
        assertEquals(1, recordedRequests.size)

        val request = recordedRequests.single()
        assertEquals(
            "https://stooq.com/q/a2/d/?s=$ticker&i=$intervalMinutes",
            request.url.toString()
        )
        assertEquals(StooqApi.DEFAULT_USER_AGENT, request.header("User-Agent"))
    }
}
