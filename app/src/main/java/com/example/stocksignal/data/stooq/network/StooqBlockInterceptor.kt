package com.example.stocksignal.data.stooq.network

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
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) : Interceptor {

    @Volatile private var nextRequestAtMillis: Long = 0L
    private val requestLock = Any()
    private val consecutiveTimeouts = AtomicInteger(0)
    private val diagnosticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (blocker.isBlocked()) {
            val message = blocker.buildBlockedMessage()
            blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
            throw StooqBlockedException(message)
        }

        enforceMinGap()

        return try {
            val response = chain.proceed(chain.request())
            val blockReason = blockReasonFor(response, chain.request().url.encodedPath)
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

    private fun enforceMinGap() {
        synchronized(requestLock) {
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
        }
    }

    private fun blockReasonFor(response: Response, path: String): String? {
        if (response.code in BLOCK_HTTP_CODES) {
            return "Stooq blocked (HTTP ${response.code})."
        }
        if (!CSV_LIKE_PATHS.any { path.startsWith(it) }) return null
        val contentType = response.header("Content-Type")?.lowercase() ?: ""
        if (contentType.contains("text/html")) {
            return "Stooq blocked (HTML response)."
        }
        val peek = response.peekBody(PEEK_BODY_BYTES).string()
        val trimmed = peek.trimStart()
        val looksHtml = trimmed.startsWith("<!doctype", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        return if (looksHtml) "Stooq blocked (HTML response)." else null
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
        private const val BASE_REQUEST_GAP_MS = 200L
        private const val JITTER_MS = 200L
        private val BLOCK_HTTP_CODES = setOf(403, 429, 503)
        private const val PEEK_BODY_BYTES = 2048L
        private val CSV_LIKE_PATHS = listOf("/q/a2/d/", "/q/d/l/", "/cmp/", "/robots.txt")
        private const val TIMEOUT_BLOCK_THRESHOLD = 5
    }
}
