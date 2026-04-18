package com.example.stocksignal.data.stooq.di

import com.example.stocksignal.core.StooqExecutionGate
import com.example.stocksignal.data.stooq.network.StooqBlockInterceptor
import com.example.stocksignal.data.stooq.network.StooqRequestBlocker
import com.example.stocksignal.data.stooq.network.StooqBlockReporter
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Verifies the OkHttp client wired up by [StooqModule] has `retryOnConnectionFailure`
 * disabled (and 120s timeouts preserved).
 *
 * Without this guarantee, Stooq's multi-A-record DNS would cause OkHttp to silently
 * fan a single app-level call out to 2–3 HTTP attempts per route, each consuming a
 * full `connectTimeout`, re-entering [StooqBlockInterceptor] and inflating the
 * consecutive-timeout counter. That is what caused the recurring 24h Stooq blocks
 * documented in `docs/CURRENT_DESIGN.md` §14.9.
 */
class StooqOkHttpClientConfigTest {

    @Test
    fun `OkHttpClient has retryOnConnectionFailure disabled`() {
        val client = buildClient()

        assertFalse(
            "retryOnConnectionFailure must stay disabled — see docs/CURRENT_DESIGN.md §14.9",
            client.retryOnConnectionFailure
        )
    }

    @Test
    fun `OkHttpClient keeps 120 second connect, read, and write timeouts`() {
        val client = buildClient()

        assertEquals(
            "connectTimeout must stay at 120s (AGENTS.md forbids lowering it)",
            TimeUnit.SECONDS.toMillis(120).toInt(),
            client.connectTimeoutMillis
        )
        assertEquals(
            TimeUnit.SECONDS.toMillis(120).toInt(),
            client.readTimeoutMillis
        )
        assertEquals(
            TimeUnit.SECONDS.toMillis(120).toInt(),
            client.writeTimeoutMillis
        )
    }

    @Test
    fun `OkHttpClient installs the Stooq block interceptor first`() {
        val blockInterceptor = buildBlockInterceptor()
        val client = buildClient(blockInterceptor)

        assertTrue(
            "StooqBlockInterceptor must be present in the interceptor chain",
            client.interceptors.contains(blockInterceptor)
        )
        // Must be the first interceptor so pacing and block-check run before any
        // header decoration or logging.
        assertTrue(
            "StooqBlockInterceptor must run first in the chain",
            client.interceptors.firstOrNull() === blockInterceptor
        )
    }

    private fun buildClient(
        blockInterceptor: StooqBlockInterceptor = buildBlockInterceptor()
    ): okhttp3.OkHttpClient {
        val headerInterceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        return StooqModule.provideOkHttpClient(
            stooqBlockInterceptor = blockInterceptor,
            headerInterceptor = headerInterceptor,
            loggingInterceptor = loggingInterceptor
        )
    }

    private fun buildBlockInterceptor(): StooqBlockInterceptor {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
        }
        val blocker = StooqRequestBlocker(diagnostics)
        val reporter = mockk<StooqBlockReporter>(relaxed = true)
        return StooqBlockInterceptor(
            blocker = blocker,
            blockReporter = reporter,
            diagnosticsRepository = diagnostics,
            executionGate = StooqExecutionGate()
        )
    }
}
