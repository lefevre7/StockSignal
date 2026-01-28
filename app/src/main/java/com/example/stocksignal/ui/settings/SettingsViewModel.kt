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
import com.example.stocksignal.data.translation.NewsTranslationService
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import com.example.stocksignal.notifications.NotificationScheduler
import com.example.stocksignal.notifications.NotificationTestSender
import com.example.stocksignal.notifications.PremarketWindowUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationTestSender: NotificationTestSender,
    private val notificationScheduler: NotificationScheduler,
    private val diagnosticsRepository: NotificationDiagnosticsRepository,
    private val translationService: NewsTranslationService
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)
    private val _modelDownloadProgress = MutableStateFlow<Int?>(null)
    private val _isDownloadingModel = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        _errorMessage,
        _toastMessage,
        _modelDownloadProgress,
        _isDownloadingModel
    ) { settings, error, toast, downloadProgress, isDownloading ->
        SettingsUiState(
            settings = settings,
            errorMessage = error,
            toastMessage = toast,
            modelDownloadProgress = downloadProgress,
            isDownloadingModel = isDownloading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(settings = defaultSettings()))

    init {
        viewModelScope.launch {
            forceEnableOfflineTranslation()
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun setHoldingPeriod(period: HoldingPeriod) {
        viewModelScope.launch {
            try {
                settingsRepository.setHoldingPeriod(period)
                showToast("Holding period updated")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting holding period to $period", e)
                _errorMessage.value = "Failed to save holding period: ${e.message}"
            }
        }
    }

    fun setFrequency(frequency: NotificationFrequency) {
        viewModelScope.launch {
            try {
                settingsRepository.setFrequency(frequency)
                // Also reschedule alarms when frequency changes
                val settings = settingsRepository.settingsFlow.first()
                notificationScheduler.schedule(settings)
                val label = when (frequency) {
                    NotificationFrequency.THREE_PER_DAY -> "3x/day"
                    NotificationFrequency.ONE_PER_DAY -> "1x/day"
                    NotificationFrequency.ONE_PER_WEEK -> "1x/week"
                    NotificationFrequency.ONLY_WHEN_OPEN -> "Only when open"
                    NotificationFrequency.DEV_FIVE_MINUTES -> "DEV mode (5min)"
                }
                showToast("Frequency: $label - alarms rescheduled")
                Log.d(TAG, "Frequency changed to $frequency, alarms rescheduled")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting frequency to $frequency", e)
                _errorMessage.value = "Failed to save frequency: ${e.message}"
            }
        }
    }

    fun toggleNotificationType(type: NotificationType, enabled: Boolean) {
        val current = uiState.value.settings.notificationTypes
        val updated = if (enabled) current + type else current - type
        viewModelScope.launch {
            try {
                settingsRepository.setNotificationTypes(updated)
                val action = if (enabled) "enabled" else "disabled"
                showToast("${type.name.lowercase().replaceFirstChar { it.uppercase() }} $action")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling notification type $type", e)
                _errorMessage.value = "Failed to save: ${e.message}"
            }
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        val current = uiState.value.settings.quietHours
        viewModelScope.launch {
            try {
                settingsRepository.setQuietHours(current.copy(enabled = enabled))
                _errorMessage.value = null
                showToast("Quiet hours ${if (enabled) "enabled" else "disabled"}")
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
                showToast("Quiet hours: $start - $end")
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
                showToast("Schedule window updated")
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
                showToast("Signal sensitivity updated")
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

    fun deleteOfflineTranslationModel() {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                translationService.deleteLocalModel()
            }
            if (!success) {
                _errorMessage.value = "Failed to delete offline translation model."
            } else {
                Log.i(TAG, "Offline translation model deleted.")
            }
        }
    }

    fun setOfflineTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setOfflineTranslationEnabled(enabled)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error setting offline translation enabled to $enabled", e)
                _errorMessage.value = "Failed to save offline translation setting: ${e.message}"
            }
        }
    }

    private suspend fun forceEnableOfflineTranslation() {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.offlineTranslationEnabled) return
        try {
            settingsRepository.setOfflineTranslationEnabled(true)
            Log.i(TAG, "Forced offline translation enabled by default.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force-enable offline translation", e)
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

    fun checkWorkerStatus() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                val windows = PremarketWindowUtils.windowsForFrequency(settings)
                val windowIds = windows.map { it.id }.toSet()
                val nowMillis = System.currentTimeMillis()
                val runInfoByWindow = diagnosticsRepository.getWindowRunInfo(windowIds)
                val nextRuns = diagnosticsRepository.getNextWindowRunTimes(windowIds)
                val robotsNext = diagnosticsRepository.getRobotsNextRun()

                val status = buildString {
                    appendLine("📊 Alarm Schedule Status:")
                    appendLine()
                    if (windowIds.isEmpty()) {
                        appendLine("❌ No notification windows scheduled.")
                        appendLine("   Background notifications are disabled.")
                    } else {
                        appendLine("✓ ${windowIds.size} notification window alarm(s) scheduled")
                        windows.forEachIndexed { idx, window ->
                            appendLine("   Window ${idx + 1}: ${window.id}")
                            val nextRun = nextRuns[window.id]
                            appendLine("     Next run: ${formatNextRun(nextRun, nowMillis)}")
                            val runInfo = runInfoByWindow[window.id]
                            appendLine(
                                "     Last run: ${formatLastRun(runInfo?.lastRunAtMillis, nowMillis)}"
                            )
                            if (!runInfo?.lastResult.isNullOrBlank()) {
                                appendLine("     Last result: ${runInfo?.lastResult}")
                            }
                            if (!runInfo?.lastReason.isNullOrBlank()) {
                                appendLine("     Last reason: ${runInfo?.lastReason}")
                            }
                        }
                    }
                    appendLine()
                    if (robotsNext != null) {
                        appendLine("Robots.txt next check: ${formatNextRun(robotsNext, nowMillis)}")
                    }
                    appendLine()
                    appendLine("💡 Tap 'Force schedule' to reschedule alarms now")
                }
                
                Log.d(TAG, status)
                _errorMessage.value = status
            } catch (e: Exception) {
                Log.e(TAG, "Error checking alarm status", e)
                _errorMessage.value = "Error checking status: ${e.message}"
            }
        }
    }

    fun forceScheduleWorkers() {
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                notificationScheduler.schedule(settings, force = true)
                _errorMessage.value = "✓ Alarms scheduled! Frequency: ${settings.frequency}, Types: ${settings.notificationTypes.joinToString()}"
                Log.d(TAG, "Force scheduled alarms for frequency: ${settings.frequency}")
            } catch (e: Exception) {
                Log.e(TAG, "Error force scheduling alarms", e)
                _errorMessage.value = "Failed to schedule alarms: ${e.message}"
            }
        }
    }

    private fun formatNextRun(nextRunAtMillis: Long?, nowMillis: Long): String {
        if (nextRunAtMillis == null) return "unknown"
        val diffMillis = nextRunAtMillis - nowMillis
        if (diffMillis <= 0) {
            return "due now"
        }
        val minutes = (diffMillis + 59_999) / 60_000
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) {
            "in ${hours}h ${mins}m"
        } else {
            val unit = if (minutes == 1L) "minute" else "minutes"
            "in $minutes $unit"
        }
    }

    private fun formatLastRun(lastRunAtMillis: Long?, nowMillis: Long): String {
        if (lastRunAtMillis == null) return "never"
        val diffMillis = nowMillis - lastRunAtMillis
        if (diffMillis < 0) return "in the future"
        val minutes = diffMillis / 60_000
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes}m ago"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h ago"
        val days = hours / 24
        return "${days}d ago"
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
            offlineTranslationEnabled = true,
            onboardingCompleted = false
        )
    }

    fun getModelInfo(): Pair<String, String> {
        val path = translationService.getLocalModelFilePath()
        val file = java.io.File(path)
        val exists = file.exists()
        val size = if (exists) {
            val bytes = file.length()
            "%.1f MB".format(bytes / (1024.0 * 1024.0))
        } else {
            "Not downloaded"
        }
        return Pair("Gemma 3 1B int4", size)
    }

    fun isModelDownloaded(): Boolean {
        val path = translationService.getLocalModelFilePath()
        return java.io.File(path).exists()
    }

    fun downloadModel() {
        viewModelScope.launch {
            _isDownloadingModel.value = true
            _modelDownloadProgress.value = 0
            
            try {
                val success = translationService.downloadLocalModel { progress ->
                    _modelDownloadProgress.value = progress
                }
                
                if (success) {
                    showToast("Model downloaded successfully")
                    _modelDownloadProgress.value = null
                } else {
                    _errorMessage.value = "Download failed. Please check your connection and try again."
                    _modelDownloadProgress.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model", e)
                _errorMessage.value = "Download failed: ${e.message}"
                _modelDownloadProgress.value = null
            } finally {
                _isDownloadingModel.value = false
            }
        }
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}

data class SettingsUiState(
    val settings: AppSettings,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val modelDownloadProgress: Int? = null,
    val isDownloadingModel: Boolean = false
)
