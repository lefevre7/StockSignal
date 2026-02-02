package com.example.stocksignal.data.stooq.network

import android.util.Log
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StooqRequestBlocker @Inject constructor() {

    private val blockedUntilMillis = AtomicLong(0L)
    private val lastReason = AtomicReference<String?>(null)

    fun isBlocked(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = blockedUntilMillis.get()
        if (until <= nowMillis) {
            if (until != 0L) {
                blockedUntilMillis.set(0L)
                lastReason.set(null)
            }
            return false
        }
        return true
    }

    fun blockFor(duration: Duration, reason: String?) {
        val until = System.currentTimeMillis() + duration.toMillis()
        blockedUntilMillis.set(until)
        lastReason.set(reason)
        Log.w(TAG, "Blocking Stooq requests until ${formatTime(until)}")
    }

    fun buildBlockedMessage(): String {
        val until = blockedUntilMillis.get()
        val timeLabel = formatTime(until)
        val prefix = lastReason.get()?.takeIf { it.isNotBlank() } ?: "Stooq is temporarily unavailable."
        return "$prefix Requests are paused until $timeLabel."
    }

    fun blockedUntilMillis(): Long {
        return blockedUntilMillis.get()
    }

    private fun formatTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "soon"
        val localTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return localTime.format(TIME_FORMATTER)
    }

    companion object {
        private const val TAG = "StooqRequestBlocker"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }
}
