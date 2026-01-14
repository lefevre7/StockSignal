package com.example.stocksignal.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.notifications.NotificationTestSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationTestSender: NotificationTestSender
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _errorMessage
    ) { settings, error ->
        SettingsUiState(settings = settings, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(settings = defaultSettings()))

    fun clearError() {
        _errorMessage.value = null
    }

    fun setHoldingPeriod(period: HoldingPeriod) {
        viewModelScope.launch {
            try {
                settingsRepository.setHoldingPeriod(period)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting holding period to $period", e)
                _errorMessage.value = "Failed to save holding period: ${e.message}"
            }
        }
    }

    fun setFrequency(frequency: NotificationFrequency) {
        viewModelScope.launch {
            settingsRepository.setFrequency(frequency)
        }
    }

    fun toggleNotificationType(type: NotificationType, enabled: Boolean) {
        val current = uiState.value.settings.notificationTypes
        val updated = if (enabled) current + type else current - type
        viewModelScope.launch {
            settingsRepository.setNotificationTypes(updated)
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        val current = uiState.value.settings.quietHours
        viewModelScope.launch {
            try {
                settingsRepository.setQuietHours(current.copy(enabled = enabled))
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting quiet hours enabled to $enabled", e)
                _errorMessage.value = "Failed to save quiet hours setting: ${e.message}"
            }
        }
    }

    fun setQuietHours(start: String, end: String) {
        val current = uiState.value.settings.quietHours
        viewModelScope.launch {
            try {
                settingsRepository.setQuietHours(current.copy(start = start, end = end))
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting quiet hours to $start - $end", e)
                _errorMessage.value = "Failed to save quiet hours: ${e.message}"
            }
        }
    }

    fun updateScheduleWindow(updated: ScheduleWindow) {
        val current = uiState.value.settings.scheduleWindows
        val next = current.map { window -> if (window.id == updated.id) updated else window }
        viewModelScope.launch {
            try {
                settingsRepository.setScheduleWindows(next)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error updating schedule window ${updated.id}", e)
                _errorMessage.value = "Failed to save schedule window: ${e.message}"
            }
        }
    }

    fun setSignalSensitivity(sensitivity: SignalSensitivity) {
        viewModelScope.launch {
            try {
                settingsRepository.setSignalSensitivity(sensitivity)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting signal sensitivity to $sensitivity", e)
                _errorMessage.value = "Failed to save signal sensitivity: ${e.message}"
            }
        }
    }

    fun setWeeklyDay(day: DayOfWeek) {
        viewModelScope.launch {
            try {
                settingsRepository.setWeeklyDay(day)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting weekly day to $day", e)
                _errorMessage.value = "Failed to save weekly day: ${e.message}"
            }
        }
    }

    fun setSnoozeDuration(duration: SnoozeDurationOption) {
        viewModelScope.launch {
            try {
                settingsRepository.setSnoozeDuration(duration)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting snooze duration to $duration", e)
                _errorMessage.value = "Failed to save snooze duration: ${e.message}"
            }
        }
    }

    fun setImmediatePostsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setImmediatePostsEnabled(enabled)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting immediate posts enabled to $enabled", e)
                _errorMessage.value = "Failed to save immediate posts setting: ${e.message}"
            }
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            try {
                val notificationId = notificationTestSender.sendTestNotification()
                if (notificationId == 0) {
                    Log.w(TAG, "Test notification failed to post.")
                    _errorMessage.value = "Failed to post test notification."
                } else {
                    Log.d(TAG, "Test notification posted with id=$notificationId")
                    _errorMessage.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error posting test notification", e)
                _errorMessage.value = "Failed to post test notification: ${e.message}"
            }
        }
    }

    private fun defaultSettings(): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(
                NotificationType.WATCHLIST,
                NotificationType.MARKET_MOVERS,
                NotificationType.DIGESTS
            ),
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
            scheduleWindows = listOf(
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
            ),
            weeklyDay = DayOfWeek.MONDAY,
            snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.SIX_MONTH,
            holdingPeriod = HoldingPeriod.MONTHS,
            immediatePostsEnabled = false,
            onboardingCompleted = false
        )
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

data class SettingsUiState(
    val settings: AppSettings,
    val errorMessage: String? = null
)
