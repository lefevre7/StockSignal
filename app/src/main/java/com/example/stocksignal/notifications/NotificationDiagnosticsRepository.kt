package com.example.stocksignal.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class NotificationDiagnosticsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    data class WindowRunInfo(
        val lastRunAtMillis: Long?,
        val lastResult: String?,
        val lastReason: String?
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

    suspend fun getNextWindowRunTimes(windowIds: Set<String>): Map<String, Long?> {
        if (windowIds.isEmpty()) return emptyMap()
        val prefs = dataStore.data.first()
        return windowIds.associateWith { windowId ->
            prefs[windowNextRunKey(windowId)]
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

    companion object {
        private val scheduleFingerprintKey = stringPreferencesKey("notif_schedule_fingerprint")
        private val scheduledWindowIdsKey = stringSetPreferencesKey("notif_scheduled_window_ids")
        private val robotsNextRunKey = longPreferencesKey("notif_robots_next_run")
        private val scheduledPremarketKeysKey = stringSetPreferencesKey("notif_scheduled_premarket_keys")
        private val lastExactAlarmAllowedKey = booleanPreferencesKey("notif_exact_alarm_allowed")

        private fun premarketNextRunKey(key: String) =
            longPreferencesKey("notif_premarket_${key}_next_run")
    }
}
