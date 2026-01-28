package com.example.stocksignal.data.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.stocksignal.domain.model.ChartRange
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    val settingsFlow: Flow<AppSettings> = dataStore.data
        .map { prefs -> prefs.toAppSettings() }
        .catch { e ->
            Log.e(TAG, "Error reading settings from DataStore", e)
            emit(createDefaultSettings())
        }

    suspend fun isOfflineTranslationPreferenceSet(): Boolean {
        return try {
            val prefs = dataStore.data.first()
            prefs.asMap().containsKey(SettingsKeys.offlineTranslationEnabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking offline translation preference state", e)
            false
        }
    }

    suspend fun setFrequency(frequency: NotificationFrequency) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.frequency] = frequency.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting frequency: ${frequency.name}", e)
            throw e
        }
    }

    suspend fun setNotificationTypes(types: Set<NotificationType>) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.notificationTypes] = types.map { it.name }.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting notification types", e)
            throw e
        }
    }

    suspend fun setQuietHours(quietHours: QuietHours) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.quietHoursEnabled] = quietHours.enabled
                prefs[SettingsKeys.quietHoursStart] = quietHours.start
                prefs[SettingsKeys.quietHoursEnd] = quietHours.end
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting quiet hours", e)
            throw e
        }
    }

    suspend fun setScheduleWindows(windows: List<ScheduleWindow>) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.scheduleWindows] = SettingsJson.encodeScheduleWindows(windows)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting schedule windows", e)
            throw e
        }
    }

    suspend fun setWeeklyDay(day: DayOfWeek) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.weeklyDay] = day.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting weekly day: ${day.name}", e)
            throw e
        }
    }

    suspend fun setSnoozeDuration(duration: SnoozeDurationOption) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.snoozeDuration] = duration.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting snooze duration: ${duration.name}", e)
            throw e
        }
    }

    suspend fun setSignalSensitivity(sensitivity: SignalSensitivity) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.minScoreForNotify] = sensitivity.minScoreForNotify
                prefs[SettingsKeys.strongBuyThreshold] = sensitivity.strongBuyThreshold
                prefs[SettingsKeys.strongSellThreshold] = sensitivity.strongSellThreshold
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting signal sensitivity", e)
            throw e
        }
    }

    suspend fun setSelectedChartRange(range: ChartRange) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.selectedChartRange] = range.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting chart range: ${range.name}", e)
            throw e
        }
    }

    suspend fun setImmediatePostsEnabled(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.immediatePostsEnabled] = enabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting immediate posts: $enabled", e)
            throw e
        }
    }

    suspend fun setOfflineTranslationEnabled(enabled: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.offlineTranslationEnabled] = enabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting offline translation enabled: $enabled", e)
            throw e
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.onboardingCompleted] = completed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting onboarding completed: $completed", e)
            throw e
        }
    }

    suspend fun setHoldingPeriod(period: HoldingPeriod) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.holdingPeriod] = period.name
                // Also update the default chart range based on holding period
                val newChartRange = when (period) {
                    HoldingPeriod.HOURS -> ChartRange.ONE_DAY
                    HoldingPeriod.DAYS -> ChartRange.FIVE_DAY
                    HoldingPeriod.WEEKS -> ChartRange.ONE_MONTH
                    HoldingPeriod.MONTHS -> ChartRange.SIX_MONTH
                    HoldingPeriod.YEARS -> ChartRange.FIVE_YEAR
                }
                prefs[SettingsKeys.selectedChartRange] = newChartRange.name
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting holding period: ${period.name}", e)
            throw e
        }
    }

    suspend fun getLastRobotsTxtCheckDate(): java.time.LocalDate? {
        return try {
            val prefs = dataStore.data.first()
            val dateString = prefs[SettingsKeys.lastRobotsTxtCheckDate]
            dateString?.let { java.time.LocalDate.parse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading last robots.txt check date", e)
            null
        }
    }

    suspend fun setLastRobotsTxtCheckDate(date: java.time.LocalDate) {
        try {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.lastRobotsTxtCheckDate] = date.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting last robots.txt check date: $date", e)
            throw e
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val frequency = parseFrequency(this[SettingsKeys.frequency])
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
        val weeklyDay = runCatching {
            DayOfWeek.valueOf(this[SettingsKeys.weeklyDay] ?: DayOfWeek.MONDAY.name)
        }.getOrDefault(DayOfWeek.MONDAY)
        val snoozeDuration = runCatching {
            SnoozeDurationOption.valueOf(
                this[SettingsKeys.snoozeDuration] ?: SnoozeDurationOption.TWENTY_FOUR_HOURS.name
            )
        }.getOrDefault(SnoozeDurationOption.TWENTY_FOUR_HOURS)
        val sensitivity = SignalSensitivity(
            minScoreForNotify = this[SettingsKeys.minScoreForNotify] ?: 60,
            strongBuyThreshold = this[SettingsKeys.strongBuyThreshold] ?: 60,
            strongSellThreshold = this[SettingsKeys.strongSellThreshold] ?: -60
        )
        val selectedChartRange = runCatching {
            ChartRange.valueOf(this[SettingsKeys.selectedChartRange] ?: ChartRange.ONE_DAY.name)
        }.getOrDefault(ChartRange.ONE_DAY)
        val immediatePostsEnabled = this[SettingsKeys.immediatePostsEnabled] ?: false
        val offlineTranslationEnabled = this[SettingsKeys.offlineTranslationEnabled] ?: true
        val onboardingCompleted = this[SettingsKeys.onboardingCompleted] ?: false
        val holdingPeriod = runCatching {
            HoldingPeriod.valueOf(this[SettingsKeys.holdingPeriod] ?: HoldingPeriod.MONTHS.name)
        }.getOrDefault(HoldingPeriod.MONTHS)

        return AppSettings(
            frequency = frequency,
            notificationTypes = types,
            quietHours = quietHours,
            scheduleWindows = scheduleWindows,
            weeklyDay = weeklyDay,
            snoozeDuration = snoozeDuration,
            signalSensitivity = sensitivity,
            selectedChartRange = selectedChartRange,
            immediatePostsEnabled = immediatePostsEnabled,
            offlineTranslationEnabled = offlineTranslationEnabled,
            onboardingCompleted = onboardingCompleted,
            holdingPeriod = holdingPeriod
        )
    }

    companion object {
        private const val TAG = "SettingsRepository"
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

        private fun createDefaultSettings(): AppSettings {
            val defaultHoldingPeriod = HoldingPeriod.MONTHS
            val defaultChartRange = when (defaultHoldingPeriod) {
                HoldingPeriod.HOURS -> ChartRange.ONE_DAY
                HoldingPeriod.DAYS -> ChartRange.FIVE_DAY
                HoldingPeriod.WEEKS -> ChartRange.ONE_MONTH
                HoldingPeriod.MONTHS -> ChartRange.SIX_MONTH
                HoldingPeriod.YEARS -> ChartRange.FIVE_YEAR
            }
            
            return AppSettings(
                frequency = NotificationFrequency.THREE_PER_DAY,
                notificationTypes = defaultNotificationTypes,
                quietHours = QuietHours(
                    enabled = false,
                    start = "22:00",
                    end = "07:00"
                ),
                scheduleWindows = defaultScheduleWindows,
                weeklyDay = DayOfWeek.MONDAY,
                snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
                signalSensitivity = SignalSensitivity(
                    minScoreForNotify = 60,
                    strongBuyThreshold = 60,
                    strongSellThreshold = -60
                ),
                selectedChartRange = defaultChartRange,
                immediatePostsEnabled = false,
                offlineTranslationEnabled = true,
                onboardingCompleted = false,
                holdingPeriod = defaultHoldingPeriod
            )
        }

        private fun parseFrequency(raw: String?): NotificationFrequency {
            if (raw.isNullOrBlank()) return NotificationFrequency.THREE_PER_DAY
            return when (raw) {
                NotificationFrequency.DEV_FIVE_MINUTES.name -> NotificationFrequency.DEV_FIVE_MINUTES
                "DEV_ONE_MINUTE" -> NotificationFrequency.DEV_FIVE_MINUTES
                else -> runCatching { NotificationFrequency.valueOf(raw) }
                    .getOrDefault(NotificationFrequency.THREE_PER_DAY)
            }
        }
    }
}

private object SettingsKeys {
    val frequency = stringPreferencesKey("notification_frequency")
    val notificationTypes = stringSetPreferencesKey("notification_types")
    val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
    val quietHoursStart = stringPreferencesKey("quiet_hours_start")
    val quietHoursEnd = stringPreferencesKey("quiet_hours_end")
    val scheduleWindows = stringPreferencesKey("schedule_windows")
    val weeklyDay = stringPreferencesKey("weekly_day")
    val snoozeDuration = stringPreferencesKey("snooze_duration")
    val minScoreForNotify = intPreferencesKey("min_score_for_notify")
    val strongBuyThreshold = intPreferencesKey("strong_buy_threshold")
    val strongSellThreshold = intPreferencesKey("strong_sell_threshold")
    val selectedChartRange = stringPreferencesKey("selected_chart_range")
    val immediatePostsEnabled = booleanPreferencesKey("immediate_posts_enabled")
    val offlineTranslationEnabled = booleanPreferencesKey("offline_translation_enabled")
    val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    val holdingPeriod = stringPreferencesKey("holding_period")
    val lastRobotsTxtCheckDate = stringPreferencesKey("last_robots_txt_check_date")
}
