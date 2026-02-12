package com.example.stocksignal.data.stooq.network

import com.example.stocksignal.core.ExternalExecutionGate
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.SocketTimeoutException

class StooqBlockInterceptorTest {

    @Test
    fun blocksAndReportsOnHttp429() {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
            coEvery { clearStooqBlocked() } returns Unit
        }
        val blocker = StooqRequestBlocker(diagnostics)
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        val interceptor = StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = ExternalExecutionGate()
        )

        val request = Request.Builder()
            .url("https://stooq.com/q/d/l/")
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

    @Test
    fun doesNotBlockOnHttp429ForNonBlockingPath() {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
            coEvery { clearStooqBlocked() } returns Unit
        }
        val blocker = StooqRequestBlocker(diagnostics)
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        val interceptor = StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = ExternalExecutionGate()
        )

        val request = Request.Builder()
            .url("https://stooq.com/cmp/")
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

        val result = interceptor.intercept(chain)
        assertFalse(blocker.isBlocked())
        verify(exactly = 0) { reporter.reportBlocked(any(), any()) }
        result.close()
    }

    @Test
    fun blocksAfterFiveTimeoutsInRow() {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
            coEvery { clearStooqBlocked() } returns Unit
        }
        val blocker = StooqRequestBlocker(diagnostics)
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        val interceptor = StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = ExternalExecutionGate()
        )

        val request = Request.Builder()
            .url("https://stooq.com/q/d/l/")
            .build()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } throws SocketTimeoutException("timeout")
        }

        repeat(4) {
            assertThrows(SocketTimeoutException::class.java) {
                interceptor.intercept(chain)
            }
        }
        assertFalse(blocker.isBlocked())
        verify(exactly = 0) { reporter.reportBlocked(any(), any()) }

        assertThrows(StooqBlockedException::class.java) {
            interceptor.intercept(chain)
        }

        assertTrue(blocker.isBlocked())
        verify(exactly = 1) {
            reporter.reportBlocked(match { it.contains("timed out") }, any())
        }
    }

    @Test
    fun resetsTimeoutStreakOnSuccess() {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
            coEvery { clearStooqBlocked() } returns Unit
        }
        val blocker = StooqRequestBlocker(diagnostics)
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        val interceptor = StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = ExternalExecutionGate()
        )

        val request = Request.Builder()
            .url("https://stooq.com/q/d/l/")
            .build()
        val timeoutChain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } throws SocketTimeoutException("timeout")
        }
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("ticker,price".toResponseBody("text/plain".toMediaType()))
            .build()
        val successChain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns response
        }

        repeat(2) {
            assertThrows(SocketTimeoutException::class.java) {
                interceptor.intercept(timeoutChain)
            }
        }
        assertFalse(blocker.isBlocked())

        val ok = interceptor.intercept(successChain)
        ok.close()

        repeat(4) {
            assertThrows(SocketTimeoutException::class.java) {
                interceptor.intercept(timeoutChain)
            }
        }
        assertFalse(blocker.isBlocked())

        assertThrows(StooqBlockedException::class.java) {
            interceptor.intercept(timeoutChain)
        }
        assertTrue(blocker.isBlocked())
    }
}
