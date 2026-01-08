package com.example.stocksignal.ui.stockdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertDefaults
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.domain.model.TechnicalIndicators
import com.example.stocksignal.ui.components.ChartFrame
import com.example.stocksignal.ui.components.SignalBadge
import com.example.stocksignal.ui.components.SignalChip
import com.example.stocksignal.ui.components.SignalScoreRow
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.components.CompanyExchangeText
import com.example.stocksignal.ui.components.HtmlText
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun StockDetailRoute(
    onBack: () -> Unit,
    onAddNote: (String) -> Unit,
    onShare: (String, String?) -> Unit,
    viewModel: StockDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StockDetailScreen(
        state = state,
        onBack = onBack,
        onSelectRange = viewModel::selectRange,
        onToggleWatchlist = viewModel::toggleWatchlist,
        onRefresh = viewModel::refresh,
        onLoadIndicatorAlerts = viewModel::loadIndicatorAlerts,
        onUpdateIndicatorAlert = viewModel::updateIndicatorAlert,
        onSaveIndicatorAlerts = viewModel::saveIndicatorAlerts,
        onAddNote = onAddNote,
        onShare = onShare
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StockDetailScreen(
    state: StockDetailUiState,
    onBack: () -> Unit,
    onSelectRange: (ChartRange) -> Unit,
    onToggleWatchlist: () -> Unit,
    onRefresh: () -> Unit,
    onLoadIndicatorAlerts: () -> Unit,
    onUpdateIndicatorAlert: (IndicatorMetric, Boolean?, Double?, AlertDirection?) -> Unit,
    onSaveIndicatorAlerts: () -> Unit,
    onAddNote: (String) -> Unit,
    onShare: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable(state.highlightEventId) {
        mutableStateOf(
            if (state.highlightEventId != null) StockDetailTab.HISTORY else StockDetailTab.OVERVIEW
        )
    }
    val scrollState = rememberScrollState()
    var showAlertSheet by rememberSaveable { mutableStateOf(false) }
    val alertOptions = if (state.indicatorAlerts.isEmpty()) {
        IndicatorAlertDefaults.defaultAlerts()
    } else {
        state.indicatorAlerts
    }

    Column(modifier = modifier.fillMaxSize()) {
        StockDetailTopBar(
            ticker = state.ticker,
            companyName = state.companyName,
            exchange = state.exchange,
            inWatchlist = state.inWatchlist,
            onBack = onBack,
            onToggleWatchlist = onToggleWatchlist
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(StockSignalDimens.cardPadding)
        ) {
            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (state.errorMessage != null) {
                StockCard {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            PriceSignalSection(
                series = state.series,
                signal = state.signal
            )
            Spacer(modifier = Modifier.height(16.dp))

            ChartFrame(title = "Price") {
                PriceChart(series = state.series)
                Spacer(modifier = Modifier.height(12.dp))
                RangeChips(
                    selected = state.range,
                    onSelectRange = onSelectRange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                StockDetailTab.values().forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(text = tab.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            when (selectedTab) {
                StockDetailTab.OVERVIEW -> OverviewTab(state = state)
                StockDetailTab.METRICS -> MetricsTab(indicators = state.indicators)
                StockDetailTab.NEWS -> NewsTab()
                StockDetailTab.SIGNALS -> SignalsTab(signal = state.signal)
                StockDetailTab.HISTORY -> HistoryTab(
                    history = state.history,
                    highlightEventId = state.highlightEventId
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            CTASection(
                onSetAlert = {
                    if (state.ticker.isNotBlank()) {
                        onLoadIndicatorAlerts()
                        showAlertSheet = true
                    }
                },
                onAddNote = {
                    if (state.ticker.isNotBlank()) {
                        onAddNote(state.ticker)
                    }
                },
                onShare = {
                    if (state.ticker.isNotBlank()) {
                        onShare(state.ticker, state.highlightEventId)
                    }
                }
            )
        }
    }

    if (showAlertSheet) {
        IndicatorAlertSheet(
            alerts = alertOptions,
            onToggle = { metric, enabled -> onUpdateIndicatorAlert(metric, enabled, null, null) },
            onThresholdChange = { metric, value -> onUpdateIndicatorAlert(metric, null, value, null) },
            onDirectionChange = { metric, direction -> onUpdateIndicatorAlert(metric, null, null, direction) },
            onSave = {
                onSaveIndicatorAlerts()
                showAlertSheet = false
            },
            onDismiss = { showAlertSheet = false }
        )
    }
}

private enum class StockDetailTab(val label: String) {
    OVERVIEW("Overview"),
    METRICS("Metrics"),
    NEWS("News"),
    SIGNALS("Signals"),
    HISTORY("History")
}

@Composable
private fun StockDetailTopBar(
    ticker: String,
    companyName: String?,
    exchange: String?,
    inWatchlist: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(StockSignalDimens.cardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(
                    text = ticker.ifBlank { "Stock" },
                    style = MaterialTheme.typography.headlineLarge
                )
                CompanyExchangeText(
                    companyName = companyName,
                    exchange = exchange,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { /* TODO follow */ }) { Text("Follow") }
            IconButton(onClick = onToggleWatchlist) {
                Icon(
                    imageVector = if (inWatchlist) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (inWatchlist) "Remove from watchlist" else "Add to watchlist"
                )
            }
            IconButton(onClick = { /* TODO menu */ }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
        }
    }
}

@Composable
private fun PriceSignalSection(
    series: List<PriceCandle>,
    signal: SignalResult?
) {
    val last = series.lastOrNull()
    val prev = series.getOrNull(series.lastIndex - 1)
    val price = last?.close
    val percentChange = if (price != null && prev != null && prev.close != 0.0) {
        ((price - prev.close) / prev.close) * 100.0
    } else {
        null
    }
    val changeColor = when {
        percentChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        percentChange >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val updatedAt = last?.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—"
    val signalTier = signal?.tier ?: SignalTier.NEUTRAL

    StockCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatPrice(price),
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = formatPercentChange(percentChange),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = changeColor
                )
                Text(
                    text = "Updated $updatedAt",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                SignalChip(tier = signalTier, label = signalTier.label)
            }
            Spacer(modifier = Modifier.width(12.dp))
            SignalBadge(
                tier = signalTier,
                score = signal?.score ?: 0,
                confidence = signal?.confidence,
                ticker = null
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SignalScoreRow(
            tier = signalTier,
            score = signal?.score ?: 0,
            confidence = signal?.confidence
        )
        Spacer(modifier = Modifier.height(6.dp))
        AverageModeRow(signal = signal)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = signalTier.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AverageModeRow(signal: SignalResult?) {
    val average = signal?.averageScore
    val mode = signal?.modeScore ?: average
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Avg ${average ?: 0}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        Text(
            text = "Mode ${mode ?: 0}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeChips(
    selected: ChartRange,
    onSelectRange: (ChartRange) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChartRange.values().forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelectRange(range) },
                label = { Text(range.label) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(state: StockDetailUiState) {
    StockCard {
        Text(text = "Overview", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (!state.companyName.isNullOrBlank()) {
            HtmlText(
                html = state.companyName,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = "Company summary coming soon.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyStat(label = "Market Cap", value = "—")
            KeyStat(label = "P/E", value = "—")
            KeyStat(label = "Dividend", value = "—")
            KeyStat(label = "52W High", value = "—")
            KeyStat(label = "52W Low", value = "—")
        }
    }
}

@Composable
private fun MetricsTab(indicators: TechnicalIndicators?) {
    StockCard {
        Text(text = "Metrics", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        IndicatorRow(label = "RSI 14", value = formatIndicator(indicators?.rsi14))
        IndicatorRow(label = "MACD", value = formatIndicator(indicators?.macd))
        IndicatorRow(label = "Signal", value = formatIndicator(indicators?.macdSignal))
        IndicatorRow(label = "Histogram", value = formatIndicator(indicators?.macdHistogram))
        IndicatorRow(label = "SMA 50", value = formatIndicator(indicators?.sma50))
        IndicatorRow(label = "SMA 200", value = formatIndicator(indicators?.sma200))
        IndicatorRow(label = "ATR 14", value = formatIndicator(indicators?.atr14))
    }
}

@Composable
private fun NewsTab() {
    StockCard {
        Text(text = "News", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "No news yet. We'll surface headlines here.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalsTab(signal: SignalResult?) {
    StockCard {
        Text(text = "Signal Details", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (signal == null) {
            Text(text = "Signal data is unavailable.", style = MaterialTheme.typography.bodySmall)
            return@StockCard
        }
        AverageModeRow(signal = signal)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Why this signal", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        signal.reasons.take(3).forEach { reason ->
            ReasonRow(reason = reason)
            Spacer(modifier = Modifier.height(6.dp))
        }
        if (signal.modelScores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Model scores", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                signal.modelScores.forEach { (model, score) ->
                    TagChip(label = "$model $score")
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<NotificationEvent>,
    highlightEventId: String?
) {
    if (history.isEmpty()) {
        StockCard {
            Text(text = "History", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "No prior signals recorded.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        history.forEach { event ->
            StockCard(highlight = event.id == highlightEventId) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = event.generatedAt.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")))
                        Text(text = event.tier.label, style = MaterialTheme.typography.bodySmall)
                    }
                    SignalChip(tier = event.tier, label = event.score.toString())
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Avg ${event.averageScore ?: event.score} • Mode ${event.modeScore ?: event.averageScore ?: event.score}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
}

@Composable
private fun CTASection(
    onSetAlert: () -> Unit,
    onAddNote: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(onClick = onSetAlert) { Text("Set Alert") }
        TextButton(onClick = onAddNote) { Text("Add Note") }
        TextButton(onClick = onShare) { Text("Share") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorAlertSheet(
    alerts: List<IndicatorAlertSetting>,
    onToggle: (IndicatorMetric, Boolean) -> Unit,
    onThresholdChange: (IndicatorMetric, Double) -> Unit,
    onDirectionChange: (IndicatorMetric, AlertDirection) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val thresholdInputs = remember(alerts) {
        mutableStateMapOf<IndicatorMetric, String>().apply {
            alerts.forEach { alert ->
                put(alert.metric, IndicatorAlertDefaults.formatValue(alert.threshold))
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StockSignalDimens.cardPadding, vertical = 12.dp)
        ) {
            Text(text = "Indicator alerts", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(alerts, key = { it.metric }) { alert ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = alert.metric.label, style = MaterialTheme.typography.bodyMedium)
                            FilterChip(
                                selected = alert.enabled,
                                onClick = { onToggle(alert.metric, !alert.enabled) },
                                label = { Text(if (alert.enabled) "On" else "Off") }
                            )
                        }
                        Text(
                            text = IndicatorAlertDefaults.defaultDescription(alert.metric),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = alert.direction == AlertDirection.ABOVE,
                                onClick = { onDirectionChange(alert.metric, AlertDirection.ABOVE) },
                                label = { Text("Above") }
                            )
                            FilterChip(
                                selected = alert.direction == AlertDirection.BELOW,
                                onClick = { onDirectionChange(alert.metric, AlertDirection.BELOW) },
                                label = { Text("Below") }
                            )
                        }
                        TextField(
                            value = thresholdInputs[alert.metric].orEmpty(),
                            onValueChange = { raw ->
                                thresholdInputs[alert.metric] = raw
                                val value = raw.toDoubleOrNull()
                                if (value != null) {
                                    onThresholdChange(alert.metric, value)
                                }
                            },
                            label = { Text("Threshold") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onSave) { Text("Save") }
            }
        }
    }
}

@Composable
private fun PriceChart(series: List<PriceCandle>) {
    if (series.size < 2) {
        Text(text = "No chart data", style = MaterialTheme.typography.bodySmall)
        return
    }

    val closes = series.map { it.close }
    val min = closes.minOrNull() ?: return
    val max = closes.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val stepX = size.width / (closes.size - 1)
        val points = closes.mapIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - min) / range).toFloat() * size.height
            Offset(x, y)
        }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }
    }
}

@Composable
private fun ReasonRow(reason: SignalReason) {
    var expanded by rememberSaveable(reason.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = reason.title, style = MaterialTheme.typography.bodyMedium)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reason.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun KeyStat(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun IndicatorRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
}

private fun formatPrice(price: Double?): String {
    return if (price == null) {
        "—"
    } else {
        "$" + String.format(Locale.getDefault(), "%.2f", price)
    }
}

private fun formatPercentChange(change: Double?): String {
    return if (change == null) {
        "—"
    } else {
        val sign = if (change > 0) "+" else if (change < 0) "-" else ""
        val magnitude = abs(change)
        sign + String.format(Locale.getDefault(), "%.2f", magnitude) + "%"
    }
}

private fun formatIndicator(value: Double?): String {
    return if (value == null) {
        "—"
    } else {
        String.format(Locale.getDefault(), "%.2f", value)
    }
}
