package com.example.stocksignal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.domain.model.ChartRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settingsFlow
        .map { settings -> SettingsUiState(settings = settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(settings = defaultSettings()))

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
            settingsRepository.setQuietHours(current.copy(enabled = enabled))
        }
    }

    fun setQuietHours(start: String, end: String) {
        val current = uiState.value.settings.quietHours
        viewModelScope.launch {
            settingsRepository.setQuietHours(current.copy(start = start, end = end))
        }
    }

    fun updateScheduleWindow(updated: ScheduleWindow) {
        val current = uiState.value.settings.scheduleWindows
        val next = current.map { window -> if (window.id == updated.id) updated else window }
        viewModelScope.launch {
            settingsRepository.setScheduleWindows(next)
        }
    }

    fun setSignalSensitivity(sensitivity: SignalSensitivity) {
        viewModelScope.launch {
            settingsRepository.setSignalSensitivity(sensitivity)
        }
    }

    fun setImmediatePostsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setImmediatePostsEnabled(enabled)
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
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.ONE_DAY,
            selectedMarketMoverRange = MarketMoverRange.ONE_DAY,
            immediatePostsEnabled = false,
            onboardingCompleted = false
        )
    }
}

data class SettingsUiState(
    val settings: AppSettings
)
