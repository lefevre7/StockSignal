package com.example.stocksignal.data.stooq.network

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class StooqBlockInterceptorTest {

    @Test
    fun blocksAndReportsOnHttp429() {
        val blocker = StooqRequestBlocker()
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        val interceptor = StooqBlockInterceptor(blocker, reporter)

        val request = Request.Builder()
            .url("https://stooq.com/")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()

        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns response
        }

        assertThrows(StooqBlockedException::class.java) {
            interceptor.intercept(chain)
        }

        assertTrue(blocker.isBlocked())
        verify {
            reporter.reportBlocked(match { it.contains("HTTP 429") }, any())
        }
    }
}
