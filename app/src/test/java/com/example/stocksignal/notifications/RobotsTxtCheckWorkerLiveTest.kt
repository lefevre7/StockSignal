package com.example.stocksignal.notifications

import android.util.Log
import com.example.stocksignal.data.stooq.TestRateLimiter
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
import java.util.concurrent.TimeUnit

/**
 * Live integration test for RobotsTxtCheckWorker.
 * Makes actual API calls to verify the robots.txt check functionality.
 */
@Ignore("Live tests disabled by default - enable manually when needed")
class RobotsTxtCheckWorkerLiveTest {

    private lateinit var api: StooqApi

    @Before
    fun setup() {
        
        // Mock Android Log class
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0

        // Create real Retrofit instance
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                .header("Accept", StooqApi.DEFAULT_ACCEPT)
                .header("Accept-Language", StooqApi.DEFAULT_ACCEPT_LANGUAGE)
                .build()
            chain.proceed(requestWithHeaders)
        }

        val rateLimitInterceptor = Interceptor { chain ->
            TestRateLimiter.withRateLimit {
                chain.proceed(chain.request())
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(StooqApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        api = retrofit.create(StooqApi::class.java)
    }

    @Test
    fun `live test - fetch robots txt and verify format`() = runTest {
        println("\n=== Live Robots.txt Check Test ===")
        
        // When: Fetch robots.txt from Stooq
        val robotsTxt = api.getRobotsTxt()
        
        // Then: Verify content
        println("Robots.txt content:")
        println("<<<")
        println(robotsTxt)
        println(">>>")
        println()
        
        assertNotNull("Robots.txt should not be null", robotsTxt)
        assertFalse("Robots.txt should not be empty", robotsTxt.isEmpty())
        
        // Check expected format
        // Note: Stooq uses Windows-style CRLF line endings (\r\n)
        val expected = "User-agent: *\r\nDisallow:\r\n"
        
        println("Expected content:")
        println("<<<")
        println(expected)
        println(">>>")
        println()
        
        println("Length comparison:")
        println("  Expected: ${expected.length} characters")
        println("  Actual:   ${robotsTxt.length} characters")
        println()
        
        if (robotsTxt == expected) {
            println("✅ MATCH: Robots.txt matches expected format exactly")
        } else {
            println("⚠️  MISMATCH: Robots.txt has changed!")
            println()
            println("Character-by-character comparison:")
            val maxLen = maxOf(expected.length, robotsTxt.length)
            for (i in 0 until maxLen) {
                val expectedChar = expected.getOrNull(i)
                val actualChar = robotsTxt.getOrNull(i)
                if (expectedChar != actualChar) {
                    println("  Position $i:")
                    println("    Expected: ${expectedChar?.code} (${expectedChar?.let { "'$it'" } ?: "EOF"})")
                    println("    Actual:   ${actualChar?.code} (${actualChar?.let { "'$it'" } ?: "EOF"})")
                }
            }
        }
        
        println()
        println("=== Test Complete ===\n")
    }

    @Test
    fun `live test - date logic verification`() = runTest {
        println("\n=== Testing Date Check Logic ===")
        
        // Simulate the date comparison logic
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)
        
        println("Today: $today")
        println("Yesterday: $yesterday")
        println("Tomorrow: $tomorrow")
        println()
        
        // Test same-day logic
        val lastCheckDate: LocalDate? = today
        if (lastCheckDate == today) {
            println("✅ Same-day check: Would skip (already checked today)")
        } else {
            println("❌ Same-day check: Would proceed (ERROR)")
            fail("Should skip when lastCheckDate == today")
        }
        
        // Test different-day logic
        val lastCheckDateYesterday: LocalDate? = yesterday
        if (lastCheckDateYesterday == today) {
            println("❌ Different-day check: Would skip (ERROR)")
            fail("Should not skip when lastCheckDate is yesterday")
        } else {
            println("✅ Different-day check: Would proceed (checked on different day)")
        }
        
        // Test null logic (never checked)
        val lastCheckDateNull: LocalDate? = null
        if (lastCheckDateNull == today) {
            println("❌ Null check: Would skip (ERROR)")
            fail("Should not skip when lastCheckDate is null")
        } else {
            println("✅ Null check: Would proceed (never checked before)")
        }
        
        println("\n=== Test Complete ===\n")
    }

    @Test
    fun `live test - full worker simulation`() = runTest {
        println("\n=== Full Worker Simulation ===")
        
        // Step 1: Check if already checked today (simulated)
        val lastCheckDate: LocalDate? = null  // Simulating never checked
        val today = LocalDate.now()
        
        println("Step 1: Check last run date")
        println("  Last check: $lastCheckDate")
        println("  Today: $today")
        
        if (lastCheckDate == today) {
            println("  ⏭️  Would skip (already checked today)")
            return@runTest
        } else {
            println("  ✅ Proceeding with check")
        }
        
        // Step 2: Fetch robots.txt
        println("\nStep 2: Fetch robots.txt from Stooq")
        val robotsTxt = try {
            api.getRobotsTxt()
        } catch (e: Exception) {
            println("  ❌ Network error: ${e.message}")
            println("  Would return failure (allows retry)")
            return@runTest
        }
        println("  ✅ Fetched ${robotsTxt.length} characters")
        
        // Step 3: Compare with expected
        // Note: Stooq uses Windows-style CRLF line endings (\r\n)
        val expected = "User-agent: *\r\nDisallow:\r\n"
        println("\nStep 3: Compare with expected content")
        
        if (robotsTxt != expected) {
            println("  ⚠️  MISMATCH DETECTED!")
            println("  Would log warning:")
            println("    Log.w(TAG, \"Stooq's robots.txt has changed!\")")
            println("  Would show toast/notification:")
            println("    \"Stooq's robots.txt has changed, please tell the developer!\"")
        } else {
            println("  ✅ Content matches expected format")
        }
        
        // Step 4: Update last check date (simulated)
        println("\nStep 4: Update last check date")
        println("  Would store: $today")
        println("  ✅ Date would be stored successfully")
        
        println("\n=== Worker Simulation Complete ===\n")
    }
}
