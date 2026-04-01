package com.example.stocksignal.data.stooq.network

import android.util.Log
import com.example.stocksignal.core.StooqExecutionGate
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
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

class StooqBlockInterceptorTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
    }

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
            executionGate = StooqExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = 0L, jitterMs = 0L)

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
    fun blocksOnHttp429ForSearchPath() {
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
            executionGate = StooqExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = 0L, jitterMs = 0L)

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

        assertThrows(StooqBlockedException::class.java) {
            interceptor.intercept(chain)
        }

        assertTrue(blocker.isBlocked())
        verify {
            reporter.reportBlocked(match { it.contains("HTTP 429") }, any())
        }
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
            executionGate = StooqExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = 0L, jitterMs = 0L)

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
            executionGate = StooqExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = 0L, jitterMs = 0L)

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

    @Test
    fun enforcesConfiguredGapAfterSuccessfulRequest() {
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
            executionGate = StooqExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = 40L, jitterMs = 0L)

        val request = Request.Builder()
            .url("https://stooq.com/q/a2/d/")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("ticker,price".toResponseBody("text/plain".toMediaType()))
            .build()
        val chain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } returns response
        }

        interceptor.intercept(chain).close()
        val secondStartNs = System.nanoTime()
        interceptor.intercept(chain).close()
        val secondDurationMs = (System.nanoTime() - secondStartNs) / 1_000_000

        assertTrue("Expected >=35ms gap, got ${secondDurationMs}ms", secondDurationMs >= 35L)
    }

    @Test
    fun enforcesConfiguredGapAfterTimeout() {
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
            executionGate = StooqExecutionGate()
        )
        interceptor.configurePacingForTest(baseRequestGapMs = 30L, jitterMs = 0L)

        val request = Request.Builder()
            .url("https://stooq.com/q/a2/d/")
            .build()
        val timeoutChain = mockk<Interceptor.Chain> {
            every { request() } returns request
            every { proceed(any()) } throws SocketTimeoutException("timeout")
        }

        assertThrows(SocketTimeoutException::class.java) {
            interceptor.intercept(timeoutChain)
        }
        val secondStartNs = System.nanoTime()
        assertThrows(SocketTimeoutException::class.java) {
            interceptor.intercept(timeoutChain)
        }
        val secondDurationMs = (System.nanoTime() - secondStartNs) / 1_000_000

        assertTrue("Expected >=25ms gap, got ${secondDurationMs}ms", secondDurationMs >= 25L)
    }
}
