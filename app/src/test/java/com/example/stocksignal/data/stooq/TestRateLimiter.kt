package com.example.stocksignal.data.stooq

/**
 * Global rate limiter for Stooq API tests.
 * Ensures all HTTP requests across all test classes are serialized
 * to prevent rate limiting from stooq.com.
 */
object TestRateLimiter {
    val lock = Object()
    
    fun <T> withRateLimit(block: () -> T): T {
        synchronized(lock) {
            // Use longer delay (5-10 seconds) to prevent stooq.com IP blocking
            Thread.sleep((5000..10000).random().toLong())
            return block()
        }
    }
}
