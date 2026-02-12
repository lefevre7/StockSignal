package com.example.stocksignal.data.stooq.network

import com.example.stocksignal.core.ExternalExecutionGate
import okhttp3.Interceptor
import okhttp3.Response
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class StooqBlockInterceptor @Inject constructor(
    private val blocker: StooqRequestBlocker,
    private val blockReporter: StooqBlockReporter,
    private val diagnosticsRepository: NotificationDiagnosticsRepository,
    private val executionGate: ExternalExecutionGate
) : Interceptor {

    @Volatile private var nextRequestAtMillis: Long = 0L
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
        if (blocker.isBlocked()) {
            val message = blocker.buildBlockedMessage()
            blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
            throw StooqBlockedException(message)
        }

        val waitMs = enforceMinGap()
        diagnosticsScope.launch {
            diagnosticsRepository.recordStooqRequest(path, method, waitMs)
        }

        return try {
            val response = chain.proceed(chain.request())
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
            if (!isBlockingPath(path)) {
                throw e
            }
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
            val targetGap = BASE_REQUEST_GAP_MS + Random.nextLong(JITTER_MS + 1)
            nextRequestAtMillis = System.currentTimeMillis() + targetGap
            waitMs
        }
    }

    private fun blockReasonFor(response: Response, path: String): String? {
        if (!isBlockingPath(path)) return null
        if (response.code in BLOCK_HTTP_CODES) {
            return "Stooq blocked (HTTP ${response.code} at $path)."
        }
        return null
    }

    private fun isBlockingPath(path: String): Boolean {
        return BLOCKING_PATHS.any { path.startsWith(it) }
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
        private val BLOCK_DURATION = Duration.ofHours(24)
        private const val BASE_REQUEST_GAP_MS = 3000L
        private const val JITTER_MS = 2000L
        private val BLOCK_HTTP_CODES = setOf(403, 429, 503)
        private val BLOCKING_PATHS = listOf("/q/a2/d/", "/q/d/l/")
        private const val TIMEOUT_BLOCK_THRESHOLD = 5
    }
}
