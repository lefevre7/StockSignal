package com.example.stocksignal.notifications

import com.example.stocksignal.core.ExecutionGateDiagnosticsRecorder
import com.example.stocksignal.core.NoOpExecutionGateDiagnosticsRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FIFO gate for background tasks that can hit Stooq.
 */
@Singleton
class BackgroundStooqExecutionGate @Inject constructor(
    private val diagnosticsRecorder: ExecutionGateDiagnosticsRecorder
) {

    constructor() : this(NoOpExecutionGateDiagnosticsRecorder)

    private val mutex = Mutex()

    suspend fun <T> withPermit(scope: String = DEFAULT_SCOPE, block: suspend () -> T): T {
        val waitStart = System.currentTimeMillis()
        var acquiredAt = 0L
        return try {
            mutex.withLock {
                acquiredAt = System.currentTimeMillis()
                block()
            }
        } finally {
            recordMetrics(scope, waitStart, acquiredAt)
        }
    }

    private fun recordMetrics(scope: String, waitStart: Long, acquiredAt: Long) {
        if (acquiredAt <= 0L) return
        val waitMs = (acquiredAt - waitStart).coerceAtLeast(0L)
        val holdMs = (System.currentTimeMillis() - acquiredAt).coerceAtLeast(0L)
        diagnosticsRecorder.record(scope, waitMs, holdMs)
    }

    companion object {
        private const val DEFAULT_SCOPE = "stooq_background"
    }
}
