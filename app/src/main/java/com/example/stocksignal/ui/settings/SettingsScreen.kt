package com.example.stocksignal.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.theme.StockSignalDimens
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = state.settings,
        onFrequencyChange = viewModel::setFrequency,
        onNotificationTypeToggle = viewModel::toggleNotificationType,
        onQuietHoursToggle = viewModel::setQuietHoursEnabled,
        onQuietHoursChange = viewModel::setQuietHours,
        onScheduleWindowChange = viewModel::updateScheduleWindow,
        onSignalSensitivityChange = viewModel::setSignalSensitivity,
        onImmediatePostsToggle = viewModel::setImmediatePostsEnabled
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onFrequencyChange: (NotificationFrequency) -> Unit,
    onNotificationTypeToggle: (NotificationType, Boolean) -> Unit,
    onQuietHoursToggle: (Boolean) -> Unit,
    onQuietHoursChange: (String, String) -> Unit,
    onScheduleWindowChange: (ScheduleWindow) -> Unit,
    onSignalSensitivityChange: (SignalSensitivity) -> Unit,
    onImmediatePostsToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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

        item {
            StockCard {
                Text(text = "Notification frequency", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationFrequency.values().forEach { option ->
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
            QuietHoursCard(
                quietHours = settings.quietHours,
                onToggle = onQuietHoursToggle,
                onApply = onQuietHoursChange
            )
        }

        item {
            StockCard {
                Text(text = "Schedule windows", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Default windows are 10 minutes before market open, 11:00, and 14:00 local.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        items(settings.scheduleWindows, key = { it.id }) { window ->
            ScheduleWindowCard(
                window = window,
                onApply = onScheduleWindowChange
            )
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
                            text = "Coming soon. Scheduled windows only.",
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
                    text = "Powered by Stooq.com data. Not affiliated with Stooq.",
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TextField(
                    value = minuteText,
                    onValueChange = { minuteText = it },
                    label = { Text("Minute") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        } else {
            TextField(
                value = offsetText,
                onValueChange = { offsetText = it },
                label = { Text("Offset minutes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                enabled = canApply
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

private fun frequencyLabel(option: NotificationFrequency): String {
    return when (option) {
        NotificationFrequency.THREE_PER_DAY -> "3x/day"
        NotificationFrequency.ONE_PER_DAY -> "1x/day"
        NotificationFrequency.ONE_PER_WEEK -> "1x/week"
        NotificationFrequency.ONLY_WHEN_OPEN -> "Only when open"
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
