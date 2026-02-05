package com.example.stocksignal.data.stooq.network

import android.util.Log
import com.example.stocksignal.data.stooq.TestRateLimiter
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.parser.MarketMoversHtmlParser
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Live integration tests for StooqApi.
 * These tests make actual API calls to stooq.com to verify the API interface works.
 *
 * Note: These tests require internet connection.
 */
@Ignore("Live tests disabled - stooq.com rate limits/blocks automated requests")
class StooqApiLiveTest {

    companion object {
        private const val FALLBACK_CAMPAIGN_ID = "1767286282"
    }

    private lateinit var api: StooqApi

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
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
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
    }

    private fun extractCampaignIdFromHomePage(homePageHtml: String): String? {
        val marker = "//stooq.com/cmp/?"
        for (line in homePageHtml.lineSequence()) {
            val markerIndex = line.indexOf(marker)
            if (markerIndex < 0) continue

            val idStart = markerIndex + marker.length
            val idEnd = line.indexOf("&q=", startIndex = idStart)
            if (idEnd <= idStart) continue

            val candidate = line.substring(idStart, idEnd)
            if (candidate.isNotEmpty() && candidate.all(Char::isDigit)) return candidate
        }
        return null
    }

    private suspend fun resolveCampaignId(): String {
        val homePage1 = api.getHomePage()
        val extracted1 = extractCampaignIdFromHomePage(homePage1)
        if (extracted1 != null) return extracted1

        val homePage2 = api.getHomePage()
        val extracted2 = extractCampaignIdFromHomePage(homePage2)
        if (extracted2 != null) return extracted2

        return FALLBACK_CAMPAIGN_ID
    }

    /*@Test
    fun `live test - getHomePage contains cmp campaign id marker`() = runTest {
        val homePage = api.getHomePage()

        assertNotNull(homePage)
        assertFalse(homePage.isEmpty())
        assertTrue(homePage.contains("//stooq.com/cmp/?"))

        val campaignId = extractCampaignIdFromHomePage(homePage)
        assertNotNull("Expected campaign id to be present in home page HTML", campaignId)
        assertTrue("Campaign id should be numeric", campaignId!!.all(Char::isDigit))

        println("✓ Extracted campaign id: $campaignId")
    }

    @Test
    fun `live test - getCmp returns cmp response`() = runTest {
        val campaignId = resolveCampaignId()
        val query = "tesla"

        val response = api.getCmp(campaignId = campaignId, query = query)

        assertNotNull(response)
        assertFalse(response.isEmpty())
        assertTrue(
            "Expected cmp response to start with window.cmp_r(",
            response.trim().startsWith("window.cmp_r(")
        )

        println("✓ cmp campaign id: $campaignId")
        println("✓ cmp response (first 120 chars): ${response.take(120)}")
    }

    @Test
    fun `live test - getRobotsTxt returns valid content`() = runTest {
        // When
        val result = api.getRobotsTxt()

        // Then
        println("Robots.txt content:")
        println(result)
        println("---")
        
        assertNotNull("Robots.txt should not be null", result)
        assertFalse("Robots.txt should not be empty", result.isEmpty())
        
        // Typical robots.txt contains User-agent directives
        val lowerResult = result.lowercase()
        assertTrue(
            "Robots.txt should contain user-agent or disallow directives",
            lowerResult.contains("user-agent") || 
            lowerResult.contains("disallow") ||
            lowerResult.contains("allow") ||
            lowerResult.contains("sitemap")
        )
        
        println("✓ Robots.txt length: ${result.length} characters")
        println("✓ Contains user-agent: ${lowerResult.contains("user-agent")}")
        println("✓ Contains disallow: ${lowerResult.contains("disallow")}")
    }

    @Test
    fun `live test - getRobotsTxt is idempotent`() = runTest {
        // When - Call multiple times
        val result1 = api.getRobotsTxt()
        val result2 = api.getRobotsTxt()

        // Then - Results should be identical
        assertEquals("Multiple calls should return same content", result1, result2)
        println("✓ Idempotency verified: ${result1.length} chars")
    }

    @Test
    fun `live test - getRobotsTxt returns plain text`() = runTest {
        // When
        val result = api.getRobotsTxt()

        // Then
        assertNotNull(result)
        
        // Should be plain text, not HTML
        assertFalse(
            "Should not be HTML response",
            result.trim().startsWith("<html>") || result.trim().startsWith("<!DOCTYPE")
        )
        
        // Should not be JSON
        assertFalse(
            "Should not be JSON response",
            result.trim().startsWith("{") || result.trim().startsWith("[")
        )
        
        println("✓ Response is plain text format")
    }

    @Test
    fun `live test - parse robots txt structure`() = runTest {
        // When
        val result = api.getRobotsTxt()

        // Then
        val lines = result.lines().filter { it.isNotBlank() }
        
        println("Robots.txt structure:")
        println("Total lines: ${lines.size}")
        
        // Count different directive types
        val userAgentCount = lines.count { it.trim().lowercase().startsWith("user-agent:") }
        val disallowCount = lines.count { it.trim().lowercase().startsWith("disallow:") }
        val allowCount = lines.count { it.trim().lowercase().startsWith("allow:") }
        val crawlDelayCount = lines.count { it.trim().lowercase().startsWith("crawl-delay:") }
        val sitemapCount = lines.count { it.trim().lowercase().startsWith("sitemap:") }
        
        println("User-agent directives: $userAgentCount")
        println("Disallow directives: $disallowCount")
        println("Allow directives: $allowCount")
        println("Crawl-delay directives: $crawlDelayCount")
        println("Sitemap directives: $sitemapCount")
        
        // Print first 10 lines for inspection
        println("\nFirst 10 lines:")
        lines.take(10).forEach { println("  $it") }
        
        assertTrue("Should have at least some content", lines.isNotEmpty())
    }

    @Test
    fun `live test - compare with getStockData endpoint`() = runTest {
        // When - Test both endpoints work
        val homePage = api.getHomePage()
        val robotsTxt = api.getRobotsTxt()
        val stockData = api.getStockData("AAPL.US", "20240102", "20240105")

        // Then - Both should return valid data
        assertNotNull("Home page should not be null", homePage)
        assertNotNull("Robots.txt should not be null", robotsTxt)
        assertNotNull("Stock data should not be null", stockData)
        
        assertFalse("Home page should not be empty", homePage.isEmpty())
        assertFalse("Robots.txt should not be empty", robotsTxt.isEmpty())
        assertFalse("Stock data should not be empty", stockData.isEmpty())
        
        // Different content types
        assertNotEquals("Should return different content", robotsTxt, stockData)
        
        println("✓ Home page length: ${homePage.length}")
        println("✓ Robots.txt length: ${robotsTxt.length}")
        println("✓ Stock data length: ${stockData.length}")
        println("✓ Both endpoints functional")
    }

    @Test
    fun `live test - getIntradayData returns response with tilde marker and data rows`() = runTest {
        val response = api.getIntradayData(ticker = "TSLA.US", intervalMinutes = 10)

        assertNotNull(response)
        assertFalse("Intraday response should not be empty", response.isEmpty())
        
        // Real stooq format: data starts after ~ marker (e.g., "~TESLA INC~20260106,154000,...")
        // No "Date,Time" header row in the actual response
        assertTrue(
            "Expected intraday response to contain ~ marker before data",
            response.contains('~')
        )

        // At least one data row should exist: YYYYMMDD,HHMMSS,Open,High,Low,Close,Volume
        val hasDataRow = response.lineSequence().any { line ->
            line.trim().matches(Regex("""\d{8},\d{1,6},[\d.]+,[\d.]+,[\d.]+,[\d.]+,\d+"""))
        }
        assertTrue("Expected intraday response to contain at least one data row", hasDataRow)
        
        println("✓ Response contains ~ marker: ${response.contains('~')}")
        println("✓ Response has data rows in format YYYYMMDD,HHMMSS,O,H,L,C,V")
    }*/

    @Test
    fun `live test - parse market movers from home page`() = runTest {
        val homePage = api.getHomePage()
        assertTrue("Home page should not be empty; skipping", homePage.isNotBlank())

        val hasMarker = homePage.contains("najbardziej aktywne", ignoreCase = true) ||
            homePage.contains("most active", ignoreCase = true)
        assertTrue("Home page missing market movers table marker; skipping", hasMarker)

        val sections = MarketMoversHtmlParser.parse(homePage)
        assertTrue("Parser returned no market movers sections; skipping", sections.isNotEmpty())

        val directions = sections.mapNotNull { it.direction }.toSet()
        assertTrue(
            "Expected at least one known market movers direction; skipping",
            directions.contains(MarketMoverDirection.MOST_ACTIVE) ||
                directions.contains(MarketMoverDirection.INCREASERS) ||
                directions.contains(MarketMoverDirection.DECREASERS)
        )

        val firstItem = sections.first().items.firstOrNull()
        assertTrue("Expected at least one mover row; skipping", firstItem != null)
        assertTrue("Expected non-blank ticker; skipping", firstItem!!.ticker.isNotBlank())
        assertTrue("Expected non-blank company name; skipping", firstItem.companyName.isNotBlank())
        assertTrue("Expected percent change to be parsed; skipping", firstItem.percentChange != null)

        println("✓ Market movers sections: ${sections.size}")
        println("✓ Directions: ${directions.joinToString()}")
        println("✓ First item: ${firstItem.ticker} ${firstItem.companyName}")
    }
}
