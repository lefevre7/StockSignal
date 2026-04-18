package com.example.stocksignal.data.stooq.network

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.stocksignal.core.StooqExecutionGate
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class StooqBlockInterceptor @Inject constructor(
    private val blocker: StooqRequestBlocker,
    private val blockReporter: StooqBlockReporter,
    private val diagnosticsRepository: NotificationDiagnosticsRepository,
    private val executionGate: StooqExecutionGate
) : Interceptor {

    @Volatile private var nextRequestAtMillis: Long = 0L
    @Volatile private var baseRequestGapMs: Long = BASE_REQUEST_GAP_MS
    @Volatile private var jitterMs: Long = JITTER_MS
    private val requestLock = Any()
    private val consecutiveTimeouts = AtomicInteger(0)
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        return executionGate.withPermitBlocking(scope = "stooq_http") {
            interceptSerialized(chain)
        }
    }

    private fun interceptSerialized(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        val method = chain.request().method

        // Always enforce the minimum gap between requests, even when blocked.
        // This ensures Stooq never sees a burst of requests after a block expires
        // and keeps the pacing timer current at all times.
        val waitMs = enforceMinGap()

        if (blocker.isBlocked()) {
            reserveNextGap()
            val message = blocker.buildBlockedMessage()
            blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
            throw StooqBlockedException(message)
        }

        diagnosticsScope.launch {
            diagnosticsRepository.recordStooqRequest(path, method, waitMs)
        }

        val requestStartMillis = System.currentTimeMillis()
        return try {
            val response = chain.proceed(chain.request())
            warnOnAmplifiedHold(path, method, requestStartMillis)
            val blockReason = blockReasonFor(response, path)
            if (blockReason != null) {
                clearTimeoutStreakIfNeeded()
                blocker.blockFor(
                    BLOCK_DURATION,
                    "$blockReason Blocked for 24 hours."
                )
                val message = blocker.buildBlockedMessage()
                blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
                response.close()
                throw StooqBlockedException(message)
            }
            clearTimeoutStreakIfNeeded()
            response
        } catch (e: SocketTimeoutException) {
            warnOnAmplifiedHold(path, method, requestStartMillis)
            val count = consecutiveTimeouts.incrementAndGet()
            recordTimeoutStreak(count)
            if (count < TIMEOUT_BLOCK_THRESHOLD) {
                throw e
            }
            consecutiveTimeouts.set(0)
            blocker.blockFor(
                BLOCK_DURATION,
                "Stooq timed out $count times in a row. Blocked for 24 hours."
            )
            val message = blocker.buildBlockedMessage()
            blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
            throw StooqBlockedException(message, e)
        } finally {
            reserveNextGap()
        }
    }

    /**
     * Emits a warning when a single interceptor pass takes dramatically longer than
     * a single `connectTimeout`. This is a canary for OkHttp route-retry amplification
     * (see `docs/CURRENT_DESIGN.md` §14.9). With `retryOnConnectionFailure=false`, a
     * single request's wall-clock time is bounded by `connectTimeout + readTimeout`
     * plus a small amount of overhead; anything past [AMPLIFIED_HOLD_WARN_MS]
     * indicates that the guarantee has regressed and Stooq is being hit multiple
     * times per app-level call.
     */
    private fun warnOnAmplifiedHold(path: String, method: String, startMillis: Long) {
        val elapsed = System.currentTimeMillis() - startMillis
        if (elapsed >= AMPLIFIED_HOLD_WARN_MS) {
            Log.w(
                TAG,
                "Stooq request $method $path took ${elapsed}ms (> ${AMPLIFIED_HOLD_WARN_MS}ms); " +
                    "possible OkHttp retry amplification — verify retryOnConnectionFailure is disabled."
            )
        }
    }

    private fun enforceMinGap(): Long {
        return synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val waitMs = (nextRequestAtMillis - now).coerceAtLeast(0L)
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            waitMs
        }
    }

    private fun reserveNextGap() {
        synchronized(requestLock) {
            val jitter = if (jitterMs <= 0L) {
                0L
            } else {
                Random.nextLong(jitterMs + 1L)
            }
            val nextGap = baseRequestGapMs + jitter
            nextRequestAtMillis = System.currentTimeMillis() + nextGap
        }
    }

    @VisibleForTesting
    internal fun configurePacingForTest(
        baseRequestGapMs: Long,
        jitterMs: Long
    ) {
        require(baseRequestGapMs >= 0L) { "baseRequestGapMs must be >= 0" }
        require(jitterMs >= 0L) { "jitterMs must be >= 0" }
        this.baseRequestGapMs = baseRequestGapMs
        this.jitterMs = jitterMs
        synchronized(requestLock) {
            nextRequestAtMillis = 0L
        }
    }

    private fun blockReasonFor(response: Response, path: String): String? {
        if (response.code in BLOCK_HTTP_CODES) {
            return "Stooq blocked (HTTP ${response.code} at $path)."
        }
        return null
    }

    private fun clearTimeoutStreakIfNeeded() {
        if (consecutiveTimeouts.get() == 0) return
        consecutiveTimeouts.set(0)
        recordTimeoutStreak(0)
    }

    private fun recordTimeoutStreak(count: Int) {
        diagnosticsScope.launch {
            diagnosticsRepository.recordStooqTimeoutStreak(count)
        }
    }

    companion object {
        private const val TAG = "StooqBlockInterceptor"
        private val BLOCK_DURATION = Duration.ofHours(24)
        private const val BASE_REQUEST_GAP_MS = 3000L
        private const val JITTER_MS = 2000L
        private val BLOCK_HTTP_CODES = setOf(403, 429, 503)
        private const val TIMEOUT_BLOCK_THRESHOLD = 5

        // 1.25 × max single-timeout wall-clock (connectTimeout 120s + readTimeout 120s ≈ 240s).
        // Anything beyond this strongly suggests OkHttp retried at least one route under the hood.
        private const val AMPLIFIED_HOLD_WARN_MS = 300_000L
    }
}
