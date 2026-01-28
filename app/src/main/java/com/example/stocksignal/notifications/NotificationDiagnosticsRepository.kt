package com.example.stocksignal.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    companion object {
        private val scheduleFingerprintKey = stringPreferencesKey("notif_schedule_fingerprint")
    }
}
