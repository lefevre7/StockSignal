package com.example.stocksignal.data.stooq.network

import okhttp3.Interceptor
import okhttp3.Response
import java.net.SocketTimeoutException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StooqBlockInterceptor @Inject constructor(
    private val blocker: StooqRequestBlocker
) : Interceptor {

    @Volatile private var lastRequestAtMillis: Long = 0L
    private val requestLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        if (blocker.isBlocked()) {
            throw StooqBlockedException(blocker.buildBlockedMessage())
        }

        enforceMinGap()

        return try {
            chain.proceed(chain.request())
        } catch (e: SocketTimeoutException) {
            blocker.blockFor(BLOCK_DURATION, "Stooq timed out.")
            throw StooqBlockedException(blocker.buildBlockedMessage(), e)
        }
    }

    private fun enforceMinGap() {
        synchronized(requestLock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestAtMillis
            val waitMs = MIN_REQUEST_GAP_MS - elapsed
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
        private const val MIN_REQUEST_GAP_MS = 500L
    }
}
