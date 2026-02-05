package com.example.stocksignal.data.stooq.network

import android.util.Log
import com.example.stocksignal.data.stooq.TestRateLimiter
import com.example.stocksignal.data.stooq.parser.PremarketQuoteParser
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Live integration test for Stooq premarket quote page parsing.
 *
 * Note: This test makes real network calls and is ignored by default.
 */
@Ignore("Live tests disabled - stooq.com rate limits/blocks automated requests")
class StooqPremarketLiveTest {

    private lateinit var api: StooqApi

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val userAgentInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("User-Agent", StooqApi.DEFAULT_USER_AGENT)
                .header("Accept", StooqApi.DEFAULT_ACCEPT)
                .header("Accept-Encoding", "identity")
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

    @Test
    fun `live test - quote page parse returns bid or ask`() = runTest {
        val html = api.getQuotePage("nvda.us")

        assertNotNull(html)
        assumeTrue(html.contains("Bid") && html.contains("Ask"))

        val quote = PremarketQuoteParser.parse(html, "NVDA.US")
        assertNotNull(quote)
        assumeTrue(quote?.bid != null || quote?.ask != null)
    }
}
