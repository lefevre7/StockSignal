package com.example.stocksignal.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Singleton
class NotificationDiagnosticsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    data class WindowRunInfo(
        val lastRunAtMillis: Long?,
        val lastResult: String?,
        val lastReason: String?
    )

    data class WindowPreNotifyRunInfo(
        val lastRunAtMillis: Long?,
        val lastResult: String?,
        val lastReason: String?
    )

    data class RobotsRunInfo(
        val lastRunAtMillis: Long?,
        val lastResult: String?,
        val lastReason: String?
    )

    data class StooqBlockedInfo(
        val blockedAtMillis: Long?,
        val blockedUntilMillis: Long?,
        val message: String?
    )

    data class PremarketRunInfo(
        val lastStartAtMillis: Long?,
        val lastRunAtMillis: Long?,
        val lastResult: String?,
        val lastReason: String?,
        val lastCandleLabel: String?,
        val lastUpsertedCount: Int?,
        val lastQuoteCount: Int?,
        val lastErrorCount: Int?
    )

    data class StooqTimeoutStreakInfo(
        val count: Int,
        val lastAtMillis: Long?
    )

    data class AlarmScheduleErrorInfo(
        val lastAtMillis: Long?,
        val reason: String?
    )

    suspend fun recordWindowRun(
        windowId: String,
        result: String,
        reason: String?
    ) {
        dataStore.edit { prefs ->
            prefs[windowLastRunKey(windowId)] = System.currentTimeMillis()
            prefs[windowLastResultKey(windowId)] = result
            if (reason.isNullOrBlank()) {
                prefs.remove(windowLastReasonKey(windowId))
            } else {
                prefs[windowLastReasonKey(windowId)] = reason
            }
        }
    }

    suspend fun recordWindowPreNotifyRun(
        windowId: String,
        result: String,
        reason: String?
    ) {
        dataStore.edit { prefs ->
            prefs[windowPreNotifyLastRunKey(windowId)] = System.currentTimeMillis()
            prefs[windowPreNotifyLastResultKey(windowId)] = result
            if (reason.isNullOrBlank()) {
                prefs.remove(windowPreNotifyLastReasonKey(windowId))
            } else {
                prefs[windowPreNotifyLastReasonKey(windowId)] = reason
            }
        }
    }

    suspend fun getWindowRunInfo(windowIds: Set<String>): Map<String, WindowRunInfo> {
        if (windowIds.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return windowIds.associateWith { windowId ->
            WindowRunInfo(
                lastRunAtMillis = prefs[windowLastRunKey(windowId)],
                lastResult = prefs[windowLastResultKey(windowId)],
                lastReason = prefs[windowLastReasonKey(windowId)]
            )
        }
    }

    suspend fun getWindowPreNotifyRunInfo(windowIds: Set<String>): Map<String, WindowPreNotifyRunInfo> {
        if (windowIds.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return windowIds.associateWith { windowId ->
            WindowPreNotifyRunInfo(
                lastRunAtMillis = prefs[windowPreNotifyLastRunKey(windowId)],
                lastResult = prefs[windowPreNotifyLastResultKey(windowId)],
                lastReason = prefs[windowPreNotifyLastReasonKey(windowId)]
            )
        }
    }

    suspend fun getNextWindowRunTimes(windowIds: Set<String>): Map<String, Long?> {
        if (windowIds.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return windowIds.associateWith { windowId ->
            prefs[windowNextRunKey(windowId)]
        }
    }

    suspend fun getWindowPreNotifyNextRuns(windowIds: Set<String>): Map<String, Long?> {
        if (windowIds.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return windowIds.associateWith { windowId ->
            prefs[windowPreNotifyNextRunKey(windowId)]
        }
    }

    suspend fun setNextWindowRun(windowId: String, nextRunAtMillis: Long?) {
        dataStore.edit { prefs ->
            if (nextRunAtMillis == null) {
                prefs.remove(windowNextRunKey(windowId))
            } else {
                prefs[windowNextRunKey(windowId)] = nextRunAtMillis
            }
        }
    }

    suspend fun setWindowPreNotifyNextRun(windowId: String, nextRunAtMillis: Long?) {
        dataStore.edit { prefs ->
            if (nextRunAtMillis == null) {
                prefs.remove(windowPreNotifyNextRunKey(windowId))
            } else {
                prefs[windowPreNotifyNextRunKey(windowId)] = nextRunAtMillis
            }
        }
    }

    suspend fun setScheduledWindowIds(windowIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[scheduledWindowIdsKey] = windowIds
        }
    }

    suspend fun getScheduledWindowIds(): Set<String> {
        val prefs = dataStore.data.first()
        return prefs[scheduledWindowIdsKey] ?: emptySet()
    }

    suspend fun setRobotsNextRun(nextRunAtMillis: Long?) {
        dataStore.edit { prefs ->
            if (nextRunAtMillis == null) {
                prefs.remove(robotsNextRunKey)
            } else {
                prefs[robotsNextRunKey] = nextRunAtMillis
            }
        }
    }

    suspend fun getRobotsNextRun(): Long? {
        val prefs = dataStore.data.first()
        return prefs[robotsNextRunKey]
    }

    suspend fun recordRobotsRun(
        result: String,
        reason: String?
    ) {
        dataStore.edit { prefs ->
            prefs[robotsLastRunKey] = System.currentTimeMillis()
            prefs[robotsLastResultKey] = result
            if (reason.isNullOrBlank()) {
                prefs.remove(robotsLastReasonKey)
            } else {
                prefs[robotsLastReasonKey] = reason
            }
        }
    }

    suspend fun getRobotsRunInfo(): RobotsRunInfo {
        val prefs = dataStore.data.first()
        return RobotsRunInfo(
            lastRunAtMillis = prefs[robotsLastRunKey],
            lastResult = prefs[robotsLastResultKey],
            lastReason = prefs[robotsLastReasonKey]
        )
    }

    suspend fun recordStooqBlocked(message: String, blockedUntilMillis: Long?) {
        dataStore.edit { prefs ->
            prefs[stooqBlockedAtKey] = System.currentTimeMillis()
            if (blockedUntilMillis == null || blockedUntilMillis <= 0L) {
                prefs.remove(stooqBlockedUntilKey)
            } else {
                prefs[stooqBlockedUntilKey] = blockedUntilMillis
            }
            if (message.isBlank()) {
                prefs.remove(stooqBlockedMessageKey)
            } else {
                prefs[stooqBlockedMessageKey] = message
            }
        }
    }

    suspend fun clearStooqBlocked() {
        dataStore.edit { prefs ->
            prefs.remove(stooqBlockedAtKey)
            prefs.remove(stooqBlockedUntilKey)
            prefs.remove(stooqBlockedMessageKey)
        }
    }

    suspend fun getStooqBlockedInfo(): StooqBlockedInfo {
        val prefs = dataStore.data.first()
        return StooqBlockedInfo(
            blockedAtMillis = prefs[stooqBlockedAtKey],
            blockedUntilMillis = prefs[stooqBlockedUntilKey],
            message = prefs[stooqBlockedMessageKey]
        )
    }

    suspend fun recordPremarketRunStarted(key: String) {
        dataStore.edit { prefs ->
            prefs[premarketLastStartKey(key)] = System.currentTimeMillis()
        }
    }

    suspend fun recordPremarketRunResult(
        key: String,
        result: String,
        reason: String?,
        candleLabel: String?,
        upsertedCount: Int?,
        quoteCount: Int?,
        errorCount: Int?
    ) {
        dataStore.edit { prefs ->
            prefs[premarketLastRunKey(key)] = System.currentTimeMillis()
            prefs[premarketLastResultKey(key)] = result
            if (reason.isNullOrBlank()) {
                prefs.remove(premarketLastReasonKey(key))
            } else {
                prefs[premarketLastReasonKey(key)] = reason
            }
            if (candleLabel.isNullOrBlank()) {
                prefs.remove(premarketLastCandleKey(key))
            } else {
                prefs[premarketLastCandleKey(key)] = candleLabel
            }
            if (upsertedCount == null) {
                prefs.remove(premarketLastUpsertedKey(key))
            } else {
                prefs[premarketLastUpsertedKey(key)] = upsertedCount
            }
            if (quoteCount == null) {
                prefs.remove(premarketLastQuoteCountKey(key))
            } else {
                prefs[premarketLastQuoteCountKey(key)] = quoteCount
            }
            if (errorCount == null) {
                prefs.remove(premarketLastErrorCountKey(key))
            } else {
                prefs[premarketLastErrorCountKey(key)] = errorCount
            }
        }
    }

    suspend fun getPremarketRunInfo(keys: Set<String>): Map<String, PremarketRunInfo> {
        if (keys.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return keys.associateWith { key ->
            PremarketRunInfo(
                lastStartAtMillis = prefs[premarketLastStartKey(key)],
                lastRunAtMillis = prefs[premarketLastRunKey(key)],
                lastResult = prefs[premarketLastResultKey(key)],
                lastReason = prefs[premarketLastReasonKey(key)],
                lastCandleLabel = prefs[premarketLastCandleKey(key)],
                lastUpsertedCount = prefs[premarketLastUpsertedKey(key)],
                lastQuoteCount = prefs[premarketLastQuoteCountKey(key)],
                lastErrorCount = prefs[premarketLastErrorCountKey(key)]
            )
        }
    }

    suspend fun recordStooqTimeoutStreak(count: Int) {
        dataStore.edit { prefs ->
            if (count <= 0) {
                prefs.remove(stooqTimeoutStreakCountKey)
                prefs.remove(stooqTimeoutStreakAtKey)
            } else {
                prefs[stooqTimeoutStreakCountKey] = count
                prefs[stooqTimeoutStreakAtKey] = System.currentTimeMillis()
            }
        }
    }

    suspend fun clearStooqTimeoutStreak() {
        dataStore.edit { prefs ->
            prefs.remove(stooqTimeoutStreakCountKey)
            prefs.remove(stooqTimeoutStreakAtKey)
        }
    }

    suspend fun getStooqTimeoutStreakInfo(): StooqTimeoutStreakInfo {
        val prefs = dataStore.data.first()
        return StooqTimeoutStreakInfo(
            count = prefs[stooqTimeoutStreakCountKey] ?: 0,
            lastAtMillis = prefs[stooqTimeoutStreakAtKey]
        )
    }

    suspend fun recordStooqRequest(path: String, method: String, waitMs: Long) {
        val timeLabel = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(STOOQ_REQUEST_TIME_FORMATTER)
        val entry = "$timeLabel $method $path wait=${waitMs}ms"
        dataStore.edit { prefs ->
            val existing = prefs[stooqRequestLogKey]
            val entries = if (existing.isNullOrBlank()) {
                mutableListOf()
            } else {
                existing.split('\n').filter { it.isNotBlank() }.toMutableList()
            }
            entries.add(entry)
            val trimmed = if (entries.size > STOOQ_REQUEST_LOG_LIMIT) {
                entries.takeLast(STOOQ_REQUEST_LOG_LIMIT)
            } else {
                entries
            }
            prefs[stooqRequestLogKey] = trimmed.joinToString("\n")
        }
    }

    suspend fun getStooqRequestLog(): List<String> {
        val prefs = dataStore.data.first()
        val raw = prefs[stooqRequestLogKey] ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    suspend fun recordAlarmScheduleError(reason: String?) {
        dataStore.edit { prefs ->
            prefs[alarmScheduleErrorAtKey] = System.currentTimeMillis()
            if (reason.isNullOrBlank()) {
                prefs.remove(alarmScheduleErrorReasonKey)
            } else {
                prefs[alarmScheduleErrorReasonKey] = reason
            }
        }
    }

    suspend fun clearAlarmScheduleError() {
        dataStore.edit { prefs ->
            prefs.remove(alarmScheduleErrorAtKey)
            prefs.remove(alarmScheduleErrorReasonKey)
        }
    }

    suspend fun getAlarmScheduleErrorInfo(): AlarmScheduleErrorInfo {
        val prefs = dataStore.data.first()
        return AlarmScheduleErrorInfo(
            lastAtMillis = prefs[alarmScheduleErrorAtKey],
            reason = prefs[alarmScheduleErrorReasonKey]
        )
    }

    fun stooqBlockedFlow(): Flow<StooqBlockedInfo> {
        return dataStore.data.map { prefs ->
            StooqBlockedInfo(
                blockedAtMillis = prefs[stooqBlockedAtKey],
                blockedUntilMillis = prefs[stooqBlockedUntilKey],
                message = prefs[stooqBlockedMessageKey]
            )
        }
    }

    suspend fun setScheduledPremarketKeys(keys: Set<String>) {
        dataStore.edit { prefs ->
            prefs[scheduledPremarketKeysKey] = keys
        }
    }

    suspend fun getScheduledPremarketKeys(): Set<String> {
        val prefs = dataStore.data.first()
        return prefs[scheduledPremarketKeysKey] ?: emptySet()
    }

    suspend fun setPremarketNextRun(key: String, nextRunAtMillis: Long?) {
        dataStore.edit { prefs ->
            if (nextRunAtMillis == null) {
                prefs.remove(premarketNextRunKey(key))
            } else {
                prefs[premarketNextRunKey(key)] = nextRunAtMillis
            }
        }
    }

    suspend fun getPremarketNextRuns(keys: Set<String>): Map<String, Long?> {
        if (keys.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return keys.associateWith { key ->
            prefs[premarketNextRunKey(key)]
        }
    }

    suspend fun setLastExactAlarmAllowed(allowed: Boolean) {
        dataStore.edit { prefs ->
            prefs[lastExactAlarmAllowedKey] = allowed
        }
    }

    suspend fun getLastExactAlarmAllowed(): Boolean? {
        val prefs = dataStore.data.first()
        return prefs[lastExactAlarmAllowedKey]
    }

    suspend fun getLastScheduleFingerprint(): String? {
        val prefs = dataStore.data.first()
        return prefs[scheduleFingerprintKey]
    }

    suspend fun setLastScheduleFingerprint(fingerprint: String) {
        dataStore.edit { prefs ->
            prefs[scheduleFingerprintKey] = fingerprint
        }
    }

    suspend fun clearScheduleFingerprint() {
        dataStore.edit { prefs ->
            prefs.remove(scheduleFingerprintKey)
        }
    }

    private fun windowLastRunKey(windowId: String) =
        longPreferencesKey("notif_window_${windowId}_last_run")

    private fun windowLastResultKey(windowId: String) =
        stringPreferencesKey("notif_window_${windowId}_last_result")

    private fun windowLastReasonKey(windowId: String) =
        stringPreferencesKey("notif_window_${windowId}_last_reason")

    private fun windowNextRunKey(windowId: String) =
        longPreferencesKey("notif_window_${windowId}_next_run")

    private fun windowPreNotifyLastRunKey(windowId: String) =
        longPreferencesKey("notif_window_${windowId}_pre_last_run")

    private fun windowPreNotifyLastResultKey(windowId: String) =
        stringPreferencesKey("notif_window_${windowId}_pre_last_result")

    private fun windowPreNotifyLastReasonKey(windowId: String) =
        stringPreferencesKey("notif_window_${windowId}_pre_last_reason")

    private fun windowPreNotifyNextRunKey(windowId: String) =
        longPreferencesKey("notif_window_${windowId}_pre_next_run")

    private fun premarketLastStartKey(key: String) =
        longPreferencesKey("premarket_${key}_last_start")

    private fun premarketLastRunKey(key: String) =
        longPreferencesKey("premarket_${key}_last_run")

    private fun premarketLastResultKey(key: String) =
        stringPreferencesKey("premarket_${key}_last_result")

    private fun premarketLastReasonKey(key: String) =
        stringPreferencesKey("premarket_${key}_last_reason")

    private fun premarketLastCandleKey(key: String) =
        stringPreferencesKey("premarket_${key}_last_candle")

    private fun premarketLastUpsertedKey(key: String) =
        intPreferencesKey("premarket_${key}_last_upserted")

    private fun premarketLastQuoteCountKey(key: String) =
        intPreferencesKey("premarket_${key}_last_quotes")

    private fun premarketLastErrorCountKey(key: String) =
        intPreferencesKey("premarket_${key}_last_errors")

    companion object {
        private val scheduleFingerprintKey = stringPreferencesKey("notif_schedule_fingerprint")
        private val scheduledWindowIdsKey = stringSetPreferencesKey("notif_scheduled_window_ids")
        private val robotsNextRunKey = longPreferencesKey("notif_robots_next_run")
        private val robotsLastRunKey = longPreferencesKey("notif_robots_last_run")
        private val robotsLastResultKey = stringPreferencesKey("notif_robots_last_result")
        private val robotsLastReasonKey = stringPreferencesKey("notif_robots_last_reason")
        private val stooqBlockedAtKey = longPreferencesKey("stooq_blocked_at")
        private val stooqBlockedUntilKey = longPreferencesKey("stooq_blocked_until")
        private val stooqBlockedMessageKey = stringPreferencesKey("stooq_blocked_message")
        private val stooqTimeoutStreakCountKey = intPreferencesKey("stooq_timeout_streak_count")
        private val stooqTimeoutStreakAtKey = longPreferencesKey("stooq_timeout_streak_at")
        private val stooqRequestLogKey = stringPreferencesKey("stooq_request_log")
        private val scheduledPremarketKeysKey = stringSetPreferencesKey("notif_scheduled_premarket_keys")
        private val lastExactAlarmAllowedKey = booleanPreferencesKey("notif_exact_alarm_allowed")
        private val alarmScheduleErrorAtKey = longPreferencesKey("notif_alarm_schedule_error_at")
        private val alarmScheduleErrorReasonKey = stringPreferencesKey("notif_alarm_schedule_error_reason")
        private const val STOOQ_REQUEST_LOG_LIMIT = 50
        private val STOOQ_REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

        private fun premarketNextRunKey(key: String) =
            longPreferencesKey("notif_premarket_${key}_next_run")
    }
}
