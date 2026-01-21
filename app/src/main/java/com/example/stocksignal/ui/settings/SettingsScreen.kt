package com.example.stocksignal.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.theme.StockSignalDimens
import com.example.stocksignal.util.DebugConfig
import java.time.DayOfWeek
import android.widget.Toast
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Show toast when toastMessage changes
    state.toastMessage?.let { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearToast()
    }
    
    SettingsScreen(
        settings = state.settings,
        errorMessage = state.errorMessage,
        onClearError = viewModel::clearError,
        onHoldingPeriodChange = viewModel::setHoldingPeriod,
        onFrequencyChange = viewModel::setFrequency,
        onNotificationTypeToggle = viewModel::toggleNotificationType,
        onQuietHoursToggle = viewModel::setQuietHoursEnabled,
        onQuietHoursChange = viewModel::setQuietHours,
        onScheduleWindowChange = viewModel::updateScheduleWindow,
        onWeeklyDayChange = viewModel::setWeeklyDay,
        onSnoozeDurationChange = viewModel::setSnoozeDuration,
        onSignalSensitivityChange = viewModel::setSignalSensitivity,
        onImmediatePostsToggle = viewModel::setImmediatePostsEnabled,
        onOfflineTranslationToggle = viewModel::setOfflineTranslationEnabled,
        onDeleteOfflineTranslationModel = viewModel::deleteOfflineTranslationModel,
        onSendTestNotification = viewModel::sendTestNotification,
        onCheckWorkerStatus = viewModel::checkWorkerStatus,
        onForceScheduleWorkers = viewModel::forceScheduleWorkers
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    onHoldingPeriodChange: (HoldingPeriod) -> Unit,
    onFrequencyChange: (NotificationFrequency) -> Unit,
    onNotificationTypeToggle: (NotificationType, Boolean) -> Unit,
    onQuietHoursToggle: (Boolean) -> Unit,
    onQuietHoursChange: (String, String) -> Unit,
    onScheduleWindowChange: (ScheduleWindow) -> Unit,
    onWeeklyDayChange: (DayOfWeek) -> Unit,
    onSnoozeDurationChange: (SnoozeDurationOption) -> Unit,
    onSignalSensitivityChange: (SignalSensitivity) -> Unit,
    onImmediatePostsToggle: (Boolean) -> Unit,
    onOfflineTranslationToggle: (Boolean) -> Unit,
    onDeleteOfflineTranslationModel: () -> Unit,
    onSendTestNotification: () -> Unit,
    onCheckWorkerStatus: () -> Unit,
    onForceScheduleWorkers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val postPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val showPermissionBanner = !postPermissionGranted
    val showNotificationsDisabledBanner = postPermissionGranted && !notificationsEnabled
    val showDigestsDisabledBanner = !settings.notificationTypes.contains(NotificationType.DIGESTS)

    val windowsEnabled = settings.frequency != NotificationFrequency.ONLY_WHEN_OPEN
    val scheduleWindows = when (settings.frequency) {
        NotificationFrequency.THREE_PER_DAY -> settings.scheduleWindows
        NotificationFrequency.ONE_PER_DAY ->
            settings.scheduleWindows.filter { it.type == ScheduleWindowType.MARKET_OPEN_MINUS }
        NotificationFrequency.ONE_PER_WEEK -> emptyList()
        NotificationFrequency.ONLY_WHEN_OPEN -> settings.scheduleWindows
        NotificationFrequency.DEV_ONE_MINUTE -> settings.scheduleWindows.take(1) // Dev mode: just first window
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(StockSignalDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(text = "Settings", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notification controls and signal sensitivity.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        errorMessage?.let { error ->
            item {
                ErrorBanner(
                    message = error,
                    onDismiss = onClearError
                )
            }
        }

        if (showPermissionBanner) {
            item {
                InfoBanner(
                    message = "Notifications permission is off. Enable POST_NOTIFICATIONS in system settings."
                )
            }
        } else if (showNotificationsDisabledBanner) {
            item {
                InfoBanner(
                    message = "Notifications are disabled for this app in system settings."
                )
            }
        }

        if (showDigestsDisabledBanner) {
            item {
                InfoBanner(
                    message = "Digests are off. Enable Digests to receive scheduled alerts."
                )
            }
        }

        item {
            HoldingPeriodCard(
                selected = settings.holdingPeriod,
                onSelect = onHoldingPeriodChange
            )
        }

        item {
            StockCard {
                Text(text = "Notification frequency", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableFrequencies = if (DebugConfig.ENABLE_DEV_MODE) {
                        NotificationFrequency.values().toList()
                    } else {
                        NotificationFrequency.values().filter { it != NotificationFrequency.DEV_ONE_MINUTE }
                    }
                    availableFrequencies.forEach { option ->
                        FilterChip(
                            selected = settings.frequency == option,
                            onClick = { onFrequencyChange(option) },
                            label = { Text(text = frequencyLabel(option)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Only when open disables background notifications.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            StockCard {
                Text(text = "Notification types", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                NotificationTypeRow(
                    label = "Watchlist signals",
                    enabled = settings.notificationTypes.contains(NotificationType.WATCHLIST),
                    onToggle = { onNotificationTypeToggle(NotificationType.WATCHLIST, it) }
                )
                NotificationTypeRow(
                    label = "Market movers",
                    enabled = settings.notificationTypes.contains(NotificationType.MARKET_MOVERS),
                    onToggle = { onNotificationTypeToggle(NotificationType.MARKET_MOVERS, it) }
                )
                NotificationTypeRow(
                    label = "Digests",
                    enabled = settings.notificationTypes.contains(NotificationType.DIGESTS),
                    onToggle = { onNotificationTypeToggle(NotificationType.DIGESTS, it) }
                )
            }
        }

        item {
            StockCard {
                Text(text = "Translation", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enable the 270M offline model for translations. Wi-Fi required; uses ~304MB.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Offline translation model", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.offlineTranslationEnabled,
                        onCheckedChange = onOfflineTranslationToggle
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDeleteOfflineTranslationModel) {
                    Text("Delete offline model")
                }
            }
        }

        item {
            StockCard {
                Text(text = "Test notification", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sends a local alert to verify sound/vibration and delivery. Appears in Signals.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSendTestNotification) { Text("Send test notification") }
            }
        }

        item {
            StockCard {
                Text(text = "Background work diagnostics", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Check if notification workers are scheduled to run in the background.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCheckWorkerStatus) { 
                        Text("Check status") 
                    }
                    TextButton(onClick = onForceScheduleWorkers) { 
                        Text("Force schedule") 
                    }
                }
                if (errorMessage != null && errorMessage.contains("worker", ignoreCase = true)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            SnoozeDurationCard(
                selected = settings.snoozeDuration,
                onSelect = onSnoozeDurationChange
            )
        }

        item {
            QuietHoursCard(
                quietHours = settings.quietHours,
                onToggle = onQuietHoursToggle,
                onApply = onQuietHoursChange
            )
        }

        item {
            StockCard {
                Text(text = scheduleHeader(settings.frequency), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = scheduleDescription(settings.frequency),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (settings.frequency == NotificationFrequency.ONE_PER_WEEK) {
            item {
                WeeklyDayCard(
                    selected = settings.weeklyDay,
                    enabled = windowsEnabled,
                    onSelect = onWeeklyDayChange
                )
            }
        } else {
            items(scheduleWindows, key = { it.id }) { window ->
                ScheduleWindowCard(
                    window = window,
                    enabled = windowsEnabled,
                    onApply = onScheduleWindowChange
                )
            }
        }

        item {
            SignalSensitivityCard(
                sensitivity = settings.signalSensitivity,
                onChange = onSignalSensitivityChange
            )
        }

        item {
            StockCard {
                Text(text = "Immediate posts", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Post immediately", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Coming soon. Scheduled windows only is the most granular for now.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = settings.immediatePostsEnabled,
                        onCheckedChange = onImmediatePostsToggle,
                        enabled = false
                    )
                }
            }
        }

        item {
            StockCard {
                Text(text = "Legal and attribution", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Signals are informational, not investment advice. We do not execute trades.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Market data is sourced from Stooq.com and may be delayed, incomplete, or inaccurate. The app does not guarantee the accuracy, completeness, or timeliness of any data. Not affiliated with Stooq.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NotificationTypeRow(
    label: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SnoozeDurationCard(
    selected: SnoozeDurationOption,
    onSelect: (SnoozeDurationOption) -> Unit
) {
    StockCard {
        Text(text = "Snooze duration", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Used when you snooze a watchlist alert.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnoozeDurationOption.values().forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursCard(
    quietHours: QuietHours,
    onToggle: (Boolean) -> Unit,
    onApply: (String, String) -> Unit
) {
    var startText by remember(quietHours.start) { mutableStateOf(quietHours.start) }
    var endText by remember(quietHours.end) { mutableStateOf(quietHours.end) }

    val startValid = parseTime(startText) != null
    val endValid = parseTime(endText) != null
    val canApply = startValid && endValid

    StockCard {
        Text(text = "Quiet hours", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Mute notifications", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Use HH:mm in local time.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = quietHours.enabled, onCheckedChange = onToggle)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("Start") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text("End") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { onApply(startText, endText) }, enabled = canApply) { Text("Apply") }
            if (!canApply) {
                Text(
                    text = "Enter valid HH:mm times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleWindowCard(
    window: ScheduleWindow,
    enabled: Boolean,
    onApply: (ScheduleWindow) -> Unit
) {
    var hourText by remember(window.id, window.hour) { mutableStateOf(window.hour?.toString() ?: "") }
    var minuteText by remember(window.id, window.minute) { mutableStateOf(window.minute?.toString() ?: "") }
    var offsetText by remember(window.id, window.offsetMinutes) { mutableStateOf(window.offsetMinutes?.toString() ?: "") }

    val hour = parseInt(hourText, 0, 23)
    val minute = parseInt(minuteText, 0, 59)
    val offset = parseInt(offsetText, -240, 240)
    val isFixed = window.type == ScheduleWindowType.FIXED_LOCAL
    val canApply = if (isFixed) hour != null && minute != null else offset != null

    StockCard {
        Text(text = scheduleLabel(window), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        if (isFixed) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = hourText,
                    onValueChange = { hourText = it },
                    label = { Text("Hour") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled
                )
                TextField(
                    value = minuteText,
                    onValueChange = { minuteText = it },
                    label = { Text("Minute") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled
                )
            }
        } else {
            TextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text("Offset minutes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Zone: ${window.zoneId ?: "Local"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                onClick = {
                    val updated = if (isFixed) {
                        window.copy(hour = hour, minute = minute, offsetMinutes = null, zoneId = null)
                    } else {
                        window.copy(hour = null, minute = null, offsetMinutes = offset)
                    }
                    onApply(updated)
                },
                enabled = canApply && enabled
            ) { Text("Apply") }
            if (!canApply) {
                Text(
                    text = "Enter valid values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SignalSensitivityCard(
    sensitivity: SignalSensitivity,
    onChange: (SignalSensitivity) -> Unit
) {
    var minScore by remember(sensitivity.minScoreForNotify) { mutableStateOf(sensitivity.minScoreForNotify) }
    var strongBuy by remember(sensitivity.strongBuyThreshold) { mutableStateOf(sensitivity.strongBuyThreshold) }
    var strongSell by remember(sensitivity.strongSellThreshold) { mutableStateOf(sensitivity.strongSellThreshold) }

    fun apply() {
        onChange(
            sensitivity.copy(
                minScoreForNotify = minScore,
                strongBuyThreshold = strongBuy,
                strongSellThreshold = strongSell
            )
        )
    }

    StockCard {
        Text(text = "Signal sensitivity", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SliderRow(
            label = "Min score for notify",
            value = minScore,
            valueRange = 0f..100f,
            onChange = {
                minScore = it
                apply()
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        SliderRow(
            label = "Strong buy threshold",
            value = strongBuy,
            valueRange = 0f..100f,
            onChange = {
                strongBuy = it
                apply()
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        SliderRow(
            label = "Strong sell threshold",
            value = strongSell,
            valueRange = -100f..0f,
            onChange = {
                strongSell = it
                apply()
            }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = 19
        )
    }
}

private fun scheduleLabel(window: ScheduleWindow): String {
    return when (window.type) {
        ScheduleWindowType.FIXED_LOCAL -> "Local time window"
        ScheduleWindowType.MARKET_OPEN_MINUS -> "Market open offset"
    }
}

private fun scheduleHeader(frequency: NotificationFrequency): String {
    return when (frequency) {
        NotificationFrequency.ONE_PER_WEEK -> "Weekly schedule"
        NotificationFrequency.ONE_PER_DAY -> "Schedule window"
        else -> "Schedule windows"
    }
}

private fun scheduleDescription(frequency: NotificationFrequency): String {
    return when (frequency) {
        NotificationFrequency.THREE_PER_DAY ->
            "Default windows are 10 minutes before market open, 11:00, and 14:00 local."
        NotificationFrequency.ONE_PER_DAY ->
            "Daily notifications use the market open offset window."
        NotificationFrequency.ONE_PER_WEEK ->
            "Weekly notifications use market open offset on the selected day."
        NotificationFrequency.ONLY_WHEN_OPEN ->
            "Background windows are disabled when notifications only run on open."
        NotificationFrequency.DEV_ONE_MINUTE ->
            "⚡ DEV MODE: Runs immediately + every 15min. Check Logcat for 'NotificationWindowWorker'."
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklyDayCard(
    selected: DayOfWeek,
    enabled: Boolean,
    onSelect: (DayOfWeek) -> Unit
) {
    StockCard {
        Text(text = "Weekly day", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DayOfWeek.values().forEach { day ->
                FilterChip(
                    selected = day == selected,
                    onClick = { onSelect(day) },
                    label = { Text(weekDayLabel(day)) },
                    enabled = enabled
                )
            }
        }
    }
}

private fun frequencyLabel(option: NotificationFrequency): String {
    return when (option) {
        NotificationFrequency.THREE_PER_DAY -> "3x/day"
        NotificationFrequency.ONE_PER_DAY -> "1x/day"
        NotificationFrequency.ONE_PER_WEEK -> "1x/week"
        NotificationFrequency.ONLY_WHEN_OPEN -> "Only when app is open"
        NotificationFrequency.DEV_ONE_MINUTE -> "⚡ 1min (DEV)"
    }
}

private fun weekDayLabel(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }
}

private fun parseInt(text: String, min: Int, max: Int): Int? {
    val value = text.trim().toIntOrNull() ?: return null
    return if (value in min..max) value else null
}

private fun parseTime(raw: String): Pair<Int, Int>? {
    val parts = raw.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour to minute
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HoldingPeriodCard(
    selected: HoldingPeriod,
    onSelect: (HoldingPeriod) -> Unit
) {
    StockCard {
        Text(text = "Investment Timeframe", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "How long do you typically hold positions? This optimizes signals and indicators for your trading style.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HoldingPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selected == period,
                    onClick = { onSelect(period) },
                    label = { Text(text = period.displayName) }
                )
            }
        }
    }
}
