package com.example.stocksignal.core

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dedicated execution gate for Stooq HTTP requests.
 */
@Singleton
class StooqExecutionGate @Inject constructor(
    private val diagnosticsRecorder: ExecutionGateDiagnosticsRecorder
) {

    constructor() : this(NoOpExecutionGateDiagnosticsRecorder)

    private val mutex = Mutex()

    fun <T> withPermitBlocking(scope: String = DEFAULT_SCOPE, block: () -> T): T {
        val waitStart = System.currentTimeMillis()
        var acquiredAt = 0L
        return try {
            runBlocking {
                mutex.withLock {
                    acquiredAt = System.currentTimeMillis()
                    block()
                }
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
        private const val DEFAULT_SCOPE = "stooq_http"
    }
}
