package com.example.stocksignal.data.stooq.network

import android.util.Log
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StooqRequestBlocker @Inject constructor(
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) {

    private val blockedUntilMillis = AtomicLong(0L)
    private val lastReason = AtomicReference<String?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var initialized = false
    private val initLock = Any()

    fun isBlocked(nowMillis: Long = System.currentTimeMillis()): Boolean {
        ensureInitialized()
        val until = blockedUntilMillis.get()
        if (until <= nowMillis) {
            if (until != 0L) {
                blockedUntilMillis.set(0L)
                lastReason.set(null)
                scope.launch {
                    diagnosticsRepository.clearStooqBlocked()
                    diagnosticsRepository.clearStooqTimeoutStreak()
                }
            }
            return false
        }
        return true
    }

    fun blockFor(duration: Duration, reason: String?) {
        val now = System.currentTimeMillis()
        val existing = blockedUntilMillis.get()
        if (existing > now) {
            return
        }
        val until = now + duration.toMillis()
        blockedUntilMillis.set(until)
        lastReason.set(reason)
        Log.w(TAG, "Blocking Stooq requests until ${formatTime(until)}")
    }

    fun clearBlock() {
        blockedUntilMillis.set(0L)
        lastReason.set(null)
        scope.launch { diagnosticsRepository.clearStooqTimeoutStreak() }
        Log.i(TAG, "Stooq block cleared manually.")
    }

    fun buildBlockedMessage(): String {
        val until = blockedUntilMillis.get()
        val timeLabel = formatBlockUntil(until)
        val prefix = lastReason.get()?.takeIf { it.isNotBlank() } ?: "Stooq is temporarily unavailable."
        return "$prefix Requests are paused until $timeLabel."
    }

    fun blockedUntilMillis(): Long {
        ensureInitialized()
        return blockedUntilMillis.get()
    }

    private fun formatTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "soon"
        val localTime = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return localTime.format(TIME_FORMATTER)
    }

    private fun formatBlockUntil(epochMillis: Long): String {
        if (epochMillis <= 0L) return "soon"
        val zone = ZoneId.systemDefault()
        val now = Instant.now().atZone(zone)
        val until = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val dayLabel = when {
            until.toLocalDate().isEqual(now.toLocalDate()) -> "today"
            until.toLocalDate().isEqual(now.toLocalDate().plusDays(1)) -> "tomorrow"
            else -> until.format(DATE_FORMATTER)
        }
        val timeLabel = until.toLocalTime().format(TIME_FORMATTER)
        return if (dayLabel == "today" || dayLabel == "tomorrow") {
            "$dayLabel at $timeLabel"
        } else {
            "$dayLabel at $timeLabel"
        }
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            runBlocking(Dispatchers.IO) {
                val info = diagnosticsRepository.getStooqBlockedInfo()
                val until = info.blockedUntilMillis ?: 0L
                if (until > System.currentTimeMillis()) {
                    blockedUntilMillis.set(until)
                    lastReason.set(info.message)
                }
            }
            initialized = true
        }
    }

    companion object {
        private const val TAG = "StooqRequestBlocker"
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d")
    }
}
