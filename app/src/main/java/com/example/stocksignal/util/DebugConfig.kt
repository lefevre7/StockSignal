package com.example.stocksignal.util

/**
 * Debug configuration flags for development features.
 * Set to false before production release.
 */
object DebugConfig {
    /**
     * Enables developer-only features:
     * - DEV_ONE_MINUTE notification frequency (2-min intervals)
     * - Verbose emoji-based logging in background workers
     * - Extended diagnostics in notification processing
     */
    const val ENABLE_DEV_MODE = true
}
