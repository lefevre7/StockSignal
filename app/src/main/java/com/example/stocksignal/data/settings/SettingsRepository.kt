package com.example.stocksignal.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.domain.model.ChartRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs.toAppSettings()
    }

    suspend fun setFrequency(frequency: NotificationFrequency) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.frequency] = frequency.name
        }
    }

    suspend fun setNotificationTypes(types: Set<NotificationType>) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.notificationTypes] = types.map { it.name }.toSet()
        }
    }

    suspend fun setQuietHours(quietHours: QuietHours) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.quietHoursEnabled] = quietHours.enabled
            prefs[SettingsKeys.quietHoursStart] = quietHours.start
            prefs[SettingsKeys.quietHoursEnd] = quietHours.end
        }
    }

    suspend fun setScheduleWindows(windows: List<ScheduleWindow>) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.scheduleWindows] = SettingsJson.encodeScheduleWindows(windows)
        }
    }

    suspend fun setSignalSensitivity(sensitivity: SignalSensitivity) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.minScoreForNotify] = sensitivity.minScoreForNotify
            prefs[SettingsKeys.strongBuyThreshold] = sensitivity.strongBuyThreshold
            prefs[SettingsKeys.strongSellThreshold] = sensitivity.strongSellThreshold
        }
    }

    suspend fun setSelectedChartRange(range: ChartRange) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.selectedChartRange] = range.name
        }
    }

    suspend fun setSelectedMarketMoverRange(range: MarketMoverRange) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.selectedMarketMoverRange] = range.name
        }
    }

    suspend fun setImmediatePostsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.immediatePostsEnabled] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.onboardingCompleted] = completed
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val frequency = NotificationFrequency.valueOf(
            this[SettingsKeys.frequency] ?: NotificationFrequency.THREE_PER_DAY.name
        )
        val types = this[SettingsKeys.notificationTypes]
            ?.mapNotNull { runCatching { NotificationType.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?: defaultNotificationTypes
        val quietHours = QuietHours(
            enabled = this[SettingsKeys.quietHoursEnabled] ?: false,
            start = this[SettingsKeys.quietHoursStart] ?: "22:00",
            end = this[SettingsKeys.quietHoursEnd] ?: "07:00"
        )
        val scheduleWindows = SettingsJson.decodeScheduleWindows(
            this[SettingsKeys.scheduleWindows]
        ).ifEmpty { defaultScheduleWindows }
        val sensitivity = SignalSensitivity(
            minScoreForNotify = this[SettingsKeys.minScoreForNotify] ?: 60,
            strongBuyThreshold = this[SettingsKeys.strongBuyThreshold] ?: 60,
            strongSellThreshold = this[SettingsKeys.strongSellThreshold] ?: -60
        )
        val selectedChartRange = runCatching {
            ChartRange.valueOf(this[SettingsKeys.selectedChartRange] ?: ChartRange.ONE_DAY.name)
        }.getOrDefault(ChartRange.ONE_DAY)
        val selectedMarketMoverRange = runCatching {
            MarketMoverRange.valueOf(
                this[SettingsKeys.selectedMarketMoverRange] ?: MarketMoverRange.ONE_DAY.name
            )
        }.getOrDefault(MarketMoverRange.ONE_DAY)
        val immediatePostsEnabled = this[SettingsKeys.immediatePostsEnabled] ?: false
        val onboardingCompleted = this[SettingsKeys.onboardingCompleted] ?: false

        return AppSettings(
            frequency = frequency,
            notificationTypes = types,
            quietHours = quietHours,
            scheduleWindows = scheduleWindows,
            signalSensitivity = sensitivity,
            selectedChartRange = selectedChartRange,
            selectedMarketMoverRange = selectedMarketMoverRange,
            immediatePostsEnabled = immediatePostsEnabled,
            onboardingCompleted = onboardingCompleted
        )
    }

    companion object {
        private val defaultNotificationTypes = setOf(
            NotificationType.WATCHLIST,
            NotificationType.MARKET_MOVERS,
            NotificationType.DIGESTS
        )
        private val defaultScheduleWindows = listOf(
            ScheduleWindow(
                id = "market_open_minus_10",
                type = ScheduleWindowType.MARKET_OPEN_MINUS,
                hour = null,
                minute = null,
                zoneId = "America/New_York",
                offsetMinutes = -10
            ),
            ScheduleWindow(
                id = "local_1100",
                type = ScheduleWindowType.FIXED_LOCAL,
                hour = 11,
                minute = 0,
                zoneId = null,
                offsetMinutes = null
            ),
            ScheduleWindow(
                id = "local_1400",
                type = ScheduleWindowType.FIXED_LOCAL,
                hour = 14,
                minute = 0,
                zoneId = null,
                offsetMinutes = null
            )
        )
    }
}

private object SettingsKeys {
    val frequency = stringPreferencesKey("notification_frequency")
    val notificationTypes = stringSetPreferencesKey("notification_types")
    val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
    val quietHoursStart = stringPreferencesKey("quiet_hours_start")
    val quietHoursEnd = stringPreferencesKey("quiet_hours_end")
    val scheduleWindows = stringPreferencesKey("schedule_windows")
    val minScoreForNotify = intPreferencesKey("min_score_for_notify")
    val strongBuyThreshold = intPreferencesKey("strong_buy_threshold")
    val strongSellThreshold = intPreferencesKey("strong_sell_threshold")
    val selectedChartRange = stringPreferencesKey("selected_chart_range")
    val selectedMarketMoverRange = stringPreferencesKey("selected_market_mover_range")
    val immediatePostsEnabled = booleanPreferencesKey("immediate_posts_enabled")
    val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
}
