package com.example.stocksignal.data.stooq.network

import okhttp3.Interceptor
import okhttp3.Response
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StooqBlockInterceptor @Inject constructor(
    private val blocker: StooqRequestBlocker,
    private val blockReporter: StooqBlockReporter
) : Interceptor {

    @Volatile private var lastRequestAtMillis: Long = 0L
    private val requestLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (blocker.isBlocked()) {
            val message = blocker.buildBlockedMessage()
            blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
            throw StooqBlockedException(message)
        }

        enforceMinGap()

        return try {
            val response = chain.proceed(chain.request())
            if (response.code in BLOCK_HTTP_CODES) {
                blocker.blockFor(
                    BLOCK_DURATION,
                    "Stooq blocked (HTTP ${response.code}). Blocked for 24 hours."
                )
                val message = blocker.buildBlockedMessage()
                blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
                response.close()
                throw StooqBlockedException(message)
            }
            response
        } catch (e: SocketTimeoutException) {
            blocker.blockFor(BLOCK_DURATION, "Stooq timed out. Blocked for 24 hours.")
            val message = blocker.buildBlockedMessage()
            blockReporter.reportBlocked(message, blocker.blockedUntilMillis())
            throw StooqBlockedException(message, e)
        }
    }

    private fun enforceMinGap() {
        synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestAtMillis
            val targetGap = BASE_REQUEST_GAP_MS + Random.nextLong(JITTER_MS + 1)
            val waitMs = targetGap - elapsed
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestAtMillis = System.currentTimeMillis()
        }
    }

    companion object {
        private val BLOCK_DURATION = Duration.ofHours(24)
        private const val BASE_REQUEST_GAP_MS = 1000L
        private const val JITTER_MS = 400L
        private val BLOCK_HTTP_CODES = setOf(403, 429, 503)
    }
}
