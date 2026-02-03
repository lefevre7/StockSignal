package com.example.stocksignal.ui.stockdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.stocksignal.ui.components.InfoIconButton
import com.example.stocksignal.ui.components.MetricExplanations
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertDefaults
import com.example.stocksignal.domain.model.IndicatorAlertSetting
import com.example.stocksignal.domain.model.IndicatorMetric
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.AiScoreReason
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.domain.model.StockNewsItem
import com.example.stocksignal.domain.model.TechnicalIndicators
import com.example.stocksignal.ui.components.ChartFrame
import com.example.stocksignal.ui.model.AiGenerationState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.example.stocksignal.ui.components.SignalBadge
import com.example.stocksignal.ui.components.SignalChip
import com.example.stocksignal.ui.components.SignalScoreRow
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.components.CompanyExchangeText
import com.example.stocksignal.ui.components.HtmlText
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.net.URLEncoder
import java.util.Locale
import java.nio.charset.StandardCharsets
import kotlin.math.abs

@Composable
fun StockDetailRoute(
    onBack: () -> Unit,
    onAddNote: (String) -> Unit,
    onShare: (String, String?) -> Unit,
    viewModel: StockDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Show export message if present
    LaunchedEffect(state.exportMessage) {
        state.exportMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.translationToastMessage) {
        state.translationToastMessage?.let { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearTranslationToast()
        }
    }

    StockDetailScreen(
        state = state,
        onBack = onBack,
        onSelectRange = viewModel::selectRange,
        onToggleWatchlist = viewModel::toggleWatchlist,
        onRefresh = viewModel::refresh,
        onLoadIndicatorAlerts = viewModel::loadIndicatorAlerts,
        onUpdateIndicatorAlert = viewModel::updateIndicatorAlert,
        onSaveIndicatorAlerts = viewModel::saveIndicatorAlerts,
        onAddTag = viewModel::addTag,
        onRemoveTag = viewModel::removeTag,
        onAddNote = onAddNote,
        onShare = onShare,
        onRetryTranslationDownload = viewModel::retryTranslationDownload,
        onDownloadOfflineModel = viewModel::requestOfflineModelDownload,
        onDismissTranslationPrompt = viewModel::dismissTranslationPrompt,
        onExportData = {
            // Create export file in Downloads directory
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val fileName = "${state.ticker.replace(".", "_")}_intraday_data.csv"
            val file = java.io.File(downloadsDir, fileName)
            viewModel.exportHistoricalData(file)
        }
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
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onAddNote: (String) -> Unit,
    onShare: (String, String?) -> Unit,
    onRetryTranslationDownload: () -> Unit,
    onDownloadOfflineModel: () -> Unit,
    onDismissTranslationPrompt: () -> Unit,
    onExportData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable(state.highlightEventId) {
        mutableStateOf(
            if (state.highlightEventId != null) StockDetailTab.HISTORY else StockDetailTab.OVERVIEW
        )
    }
    val scrollState = rememberScrollState()
    var showAlertSheet by rememberSaveable { mutableStateOf(false) }
    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val alertOptions = if (state.indicatorAlerts.isEmpty()) {
        IndicatorAlertDefaults.defaultAlerts()
    } else {
        state.indicatorAlerts
    }
    LaunchedEffect(state.openAlerts) {
        if (state.openAlerts) {
            onLoadIndicatorAlerts()
            showAlertSheet = true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        StockDetailTopBar(
            ticker = state.ticker,
            companyName = state.companyName,
            exchange = state.exchange,
            inWatchlist = state.inWatchlist,
            onBack = onBack,
            onToggleWatchlist = onToggleWatchlist,
            menuExpanded = menuExpanded,
            onMenuExpandedChange = { menuExpanded = it },
            onManageTags = { showTagSheet = true },
            onExportData = onExportData
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
                signal = state.signal,
                range = state.range,
                aiGenerationState = state.aiGenerationState
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 8.dp
            ) {
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
                StockDetailTab.METRICS -> MetricsTab(indicators = state.indicators, range = state.range)
                StockDetailTab.NEWS -> NewsTab(
                    news = state.news,
                    translationMessage = state.translationMessage,
                    showTranslationRetry = state.showTranslationRetry,
                    showDownloadOfflineModel = !state.localModelAvailable,
                    onRetryTranslationDownload = onRetryTranslationDownload,
                    onDownloadOfflineModel = onDownloadOfflineModel
                )
                StockDetailTab.SIGNALS -> SignalsTab(signal = state.signal, range = state.range)
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

    if (showTagSheet) {
        TagManagementSheet(
            tags = state.tags,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            onDismiss = { showTagSheet = false }
        )
    }

    if (state.showTranslationPrompt) {
        val promptTitle = state.translationPromptTitle ?: "Download translation and scoring model"
        val promptMessage = state.translationPromptMessage
            ?: "Download the 1B offline model to translate headlines?"
        val isDownloading = state.translationDownloadInProgress
        AlertDialog(
            onDismissRequest = onDismissTranslationPrompt,
            title = { Text(promptTitle) },
            text = {
                Column {
                    Text(promptMessage)
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (state.translationPromptType == TranslationPromptType.LOCAL_MODEL) {
                            val percent = state.translationDownloadProgress ?: 0
                            LinearProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) })
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "$percent%")
                        } else {
                            LinearProgressIndicator()
                        }
                    }
                }
            },
            confirmButton = {
                if (state.translationPromptType == TranslationPromptType.LOCAL_MODEL && !isDownloading) {
                    TextButton(onClick = onRetryTranslationDownload) { Text("Download") }
                }
            },
            dismissButton = {
                val dismissLabel = if (isDownloading) "Hide" else "Not now"
                TextButton(onClick = onDismissTranslationPrompt) { Text(dismissLabel) }
            }
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
    onToggleWatchlist: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onManageTags: () -> Unit,
    onExportData: () -> Unit
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
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { onMenuExpandedChange(false) }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Manage tags") },
                        onClick = {
                            onMenuExpandedChange(false)
                            onManageTags()
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Export historical data") },
                        onClick = {
                            onMenuExpandedChange(false)
                            onExportData()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceSignalSection(
    series: List<PriceCandle>,
    signal: SignalResult?,
    range: ChartRange,
    aiGenerationState: AiGenerationState = AiGenerationState.IDLE
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
    val displayScore = signal?.displayScore ?: 0
    val displayConfidence = signal?.displayConfidence
    val aiScore = signal?.aiScore
    val aiConfidence = signal?.aiConfidence

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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Signal for ${range.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        SignalBadge(
            tier = signalTier,
            score = displayScore,
            confidence = displayConfidence,
            ticker = null
        )
        }

        Spacer(modifier = Modifier.height(12.dp))
        SignalScoreRow(
            tier = signalTier,
            score = aiScore,
            confidence = aiConfidence,
            compact = false,
            scoreLabel = "AI Score",
            confidenceLabel = "AI Confidence",
            aiGenerationState = aiGenerationState
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
    val confidence = signal?.confidence
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Avg ${average ?: 0}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        Text(
            text = "Mode ${mode ?: 0}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        Text(
            text = "Rule Conf ${confidence ?: 0}%",
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
        
        if (state.overviewError != null) {
            Text(
                text = state.overviewError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyStat(label = "Market Cap", value = formatOverviewMarketCap(state.marketCap))
                KeyStat(label = "P/E", value = formatOverviewDecimal(state.peRatio))
                KeyStat(label = "Dividend", value = formatOverviewPercent(state.dividend))
                KeyStat(label = "52W High", value = formatOverviewPrice(state.week52High))
                KeyStat(label = "52W Low", value = formatOverviewPrice(state.week52Low))
            }
        }
    }
}

@Composable
private fun formatOverviewMarketCap(value: Double?): String {
    if (value == null) return "—"
    return when {
        value >= 1_000_000_000_000 -> "${"%.2f".format(value / 1_000_000_000_000)}T"
        value >= 1_000_000_000 -> "${"%.2f".format(value / 1_000_000_000)}B"
        value >= 1_000_000 -> "${"%.2f".format(value / 1_000_000)}M"
        else -> "${"%.2f".format(value)}"
    }
}

@Composable
private fun formatOverviewDecimal(value: Double?): String {
    return value?.let { "%.2f".format(it) } ?: "—"
}

@Composable
private fun formatOverviewPercent(value: Double?): String {
    return value?.let { "%.2f%%".format(it) } ?: "—"
}

@Composable
private fun formatOverviewPrice(value: Double?): String {
    return value?.let { "$%.2f".format(it) } ?: "—"
}

@Composable
private fun MetricsTab(indicators: TechnicalIndicators?, range: ChartRange) {
    StockCard {
        Text(text = "Metrics", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Range: ${range.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        IndicatorRow(
            label = "RSI 14",
            value = formatIndicator(indicators?.rsi14),
            explanation = MetricExplanations.RSI_14
        )
        IndicatorRow(
            label = "MACD",
            value = formatIndicator(indicators?.macd),
            explanation = MetricExplanations.MACD
        )
        IndicatorRow(
            label = "Signal",
            value = formatIndicator(indicators?.macdSignal),
            explanation = MetricExplanations.MACD_SIGNAL
        )
        IndicatorRow(
            label = "Histogram",
            value = formatIndicator(indicators?.macdHistogram),
            explanation = MetricExplanations.MACD_HISTOGRAM
        )
        IndicatorRow(
            label = "SMA 50",
            value = formatIndicator(indicators?.sma50),
            explanation = MetricExplanations.SMA_50
        )
        IndicatorRow(
            label = "SMA 200",
            value = formatIndicator(indicators?.sma200),
            explanation = MetricExplanations.SMA_200
        )
        IndicatorRow(
            label = "ATR 14",
            value = formatIndicator(indicators?.atr14),
            explanation = MetricExplanations.ATR_14
        )
    }
}

@Composable
private fun NewsTab(
    news: List<StockNewsItem>,
    translationMessage: String?,
    showTranslationRetry: Boolean,
    showDownloadOfflineModel: Boolean,
    onRetryTranslationDownload: () -> Unit,
    onDownloadOfflineModel: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    StockCard {
        Text(text = "News", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Show translation error/warning message prominently
        translationMessage?.let { message ->
            val isError = message.contains("incompatible", ignoreCase = true) || 
                         message.contains("failed", ignoreCase = true)
            Surface(
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        if (showDownloadOfflineModel || showTranslationRetry) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDownloadOfflineModel) {
                    TextButton(onClick = onDownloadOfflineModel) { Text("Download offline model") }
                }
                if (showTranslationRetry) {
                    TextButton(onClick = onRetryTranslationDownload) { Text("Try download again") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (news.isEmpty()) {
            Text(text = "No recent headlines available.", style = MaterialTheme.typography.bodySmall)
            return@StockCard
        }
        news.forEachIndexed { index, item ->
            val displayTitle = item.translatedTitle?.takeIf { it.isNotBlank() } ?: item.title
            val searchUrl = buildGoogleSearchUrl(displayTitle)
            if (index > 0) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(searchUrl) }
            ) {
                Text(text = displayTitle, style = MaterialTheme.typography.bodyMedium)
                if (item.translatedTitle.isNullOrBlank()) {
                    val meta = buildString {
                        // Format publishedAt in local phone timezone
                        item.publishedAt?.let { instant ->
                            val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a z")
                            val localTime = instant.atZone(ZoneId.systemDefault())
                            append(localTime.format(formatter))
                        } ?: run {
                            // Fallback to original text if Instant not available
                            if (item.publishedAtText.isNotBlank()) {
                                append(item.publishedAtText)
                            }
                        }
                        if (!item.source.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(item.source)
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun buildGoogleSearchUrl(title: String): String {
    val query = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
    return "https://www.google.com/search?q=$query"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalsTab(signal: SignalResult?, range: ChartRange) {
    StockCard {
        Text(text = "Signal Details", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Range: ${range.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (signal == null) {
            Text(text = "Signal data is unavailable.", style = MaterialTheme.typography.bodySmall)
            return@StockCard
        }
        AverageModeRow(signal = signal)
        Spacer(modifier = Modifier.height(8.dp))
        if (!signal.aiSummary.isNullOrBlank() || signal.aiReasons.isNotEmpty()) {
            Text(text = "AI reasoning", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            if (!signal.aiSummary.isNullOrBlank()) {
                Text(
                    text = signal.aiSummary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            signal.aiReasons.take(3).forEach { reason ->
                AiReasonRow(reason = reason)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(text = "Rule-based reasons", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        signal.reasons.take(3).forEach { reason ->
            ReasonRow(reason = reason)
            Spacer(modifier = Modifier.height(6.dp))
        }
        if (signal.modelScores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Metric scores", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                signal.modelScores.forEach { (metric, score) ->
                    val displayName = when (metric) {
                        "ma" -> "MA"
                        "rsi" -> "RSI"
                        "macd" -> "MACD"
                        "bb" -> "BB"
                        "volume" -> "Vol"
                        "breakout" -> "Break"
                        "zscore" -> "Z-Score"
                        else -> metric
                    }
                    TagChip(label = "$displayName $score")
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
                    SignalChip(tier = event.tier, label = "AI ${event.displayScore}")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Avg ${event.averageScore ?: event.score} • " +
                        "Mode ${event.modeScore ?: event.averageScore ?: event.score} • " +
                        "Rule Conf ${event.confidence}%",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagManagementSheet(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = StockSignalDimens.cardPadding, vertical = 12.dp)
        ) {
            Text(text = "Manage tags", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (tags.isEmpty()) {
                Text(
                    text = "No tags yet. Add one below.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = "Tap a tag to remove it.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove tag"
                                )
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("Done") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onAddTag(input)
                        input = ""
                    },
                    enabled = input.trim().isNotEmpty()
                ) { Text("Add") }
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

    val haptic = LocalHapticFeedback.current
    var touchedIndex by remember { mutableStateOf<Int?>(null) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }

    val currentCandle = series.lastOrNull() ?: return
    val touchedCandle = touchedIndex?.let { series.getOrNull(it) }
    val displayCandle = touchedCandle ?: currentCandle

    // Calculate price and volume ranges
    val minPrice = series.minOfOrNull { minOf(it.low, it.open, it.close) } ?: 0.0
    val maxPrice = series.maxOfOrNull { maxOf(it.high, it.open, it.close) } ?: 1.0
    val priceRange = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0
    
    val maxVolume = series.maxOfOrNull { it.volume } ?: 1L

    Column(modifier = Modifier.fillMaxWidth()) {
        // Top data row showing current or touched candle
        CandleDataRow(
            candle = displayCandle,
            previousClose = if (displayCandle == currentCandle) {
                series.getOrNull(series.size - 2)?.close
            } else {
                val index = series.indexOf(displayCandle)
                if (index > 0) series[index - 1].close else null
            },
            isTouched = touchedCandle != null
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Chart with candlesticks, volume bars, and touch interaction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .semantics {
                    contentDescription = "Price candlestick chart with ${series.size} data points. " +
                        "Current price: ${formatPrice(currentCandle.close)}. " +
                        "Drag to explore historical data."
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(series) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val index = (offset.x / size.width * series.size).toInt()
                                    .coerceIn(0, series.size - 1)
                                touchedIndex = index
                                touchPosition = offset
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, _ ->
                                val index = (change.position.x / size.width * series.size).toInt()
                                    .coerceIn(0, series.size - 1)
                                if (touchedIndex != index) {
                                    touchedIndex = index
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                touchPosition = change.position
                                change.consume()
                            },
                            onDragEnd = {
                                touchedIndex = null
                                touchPosition = null
                            }
                        )
                    }
                    .pointerInput(series) {
                        detectTapGestures(
                            onTap = { offset ->
                                val index = (offset.x / size.width * series.size).toInt()
                                    .coerceIn(0, series.size - 1)
                                touchedIndex = if (touchedIndex == index) null else index
                                touchPosition = if (touchedIndex != null) offset else null
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
            ) {
                val chartHeight = size.height * 0.7f  // 70% for price chart
                val volumeHeight = size.height * 0.25f  // 25% for volume
                val volumeTop = chartHeight + (size.height * 0.05f)  // 5% gap
                val candleWidth = (size.width / series.size) * 0.7f
                
                // Draw Y-axis price labels (right side)
                val priceSteps = 5
                for (i in 0..priceSteps) {
                    val price = minPrice + (priceRange * i / priceSteps)
                    val y = chartHeight - (chartHeight * i / priceSteps)
                    
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.6f).toArgb()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                        drawText(
                            formatPrice(price),
                            size.width - 8f,
                            y + 8f,
                            paint
                        )
                    }
                    
                    // Horizontal grid line
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f),
                        start = Offset(0f, y),
                        end = Offset(size.width - 60f, y),
                        strokeWidth = 1f
                    )
                }
                
                // Draw X-axis time labels (bottom)
                val timeSteps = minOf(5, series.size - 1)
                for (i in 0..timeSteps) {
                    val index = (series.size - 1) * i / timeSteps
                    val candle = series[index]
                    val x = (size.width / series.size) * (index + 0.5f)
                    
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.6f).toArgb()
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawText(
                            formatTimeForRange(candle.time, series),
                            x,
                            size.height - 4f,
                            paint
                        )
                    }
                }
                
                // Draw candlesticks and volume bars
                series.forEachIndexed { index, candle ->
                    val x = (size.width / series.size) * (index + 0.5f)
                    val isUp = candle.close >= candle.open
                    val candleColor = if (isUp) Color(0xFF26A69A) else Color(0xFFEF5350)
                    
                    // Candlestick
                    val openY = chartHeight - ((candle.open - minPrice) / priceRange * chartHeight).toFloat()
                    val closeY = chartHeight - ((candle.close - minPrice) / priceRange * chartHeight).toFloat()
                    val highY = chartHeight - ((candle.high - minPrice) / priceRange * chartHeight).toFloat()
                    val lowY = chartHeight - ((candle.low - minPrice) / priceRange * chartHeight).toFloat()
                    
                    // High-low wick
                    drawLine(
                        color = candleColor,
                        start = Offset(x, highY),
                        end = Offset(x, lowY),
                        strokeWidth = 2f
                    )
                    
                    // Open-close body
                    val bodyTop = minOf(openY, closeY)
                    val bodyBottom = maxOf(openY, closeY)
                    val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(2f)
                    
                    drawRect(
                        color = candleColor,
                        topLeft = Offset(x - candleWidth / 2, bodyTop),
                        size = androidx.compose.ui.geometry.Size(candleWidth, bodyHeight)
                    )
                    
                    // Highlight touched candle
                    if (index == touchedIndex) {
                        drawRect(
                            color = Color.Blue.copy(alpha = 0.2f),
                            topLeft = Offset(x - candleWidth / 2 - 4f, 0f),
                            size = androidx.compose.ui.geometry.Size(candleWidth + 8f, chartHeight)
                        )
                    }
                    
                    // Volume bar
                    val volHeight = (candle.volume.toFloat() / maxVolume.toFloat() * volumeHeight)
                    val volumeBarColor = candleColor.copy(alpha = 0.3f)
                    
                    drawRect(
                        color = volumeBarColor,
                        topLeft = Offset(x - candleWidth / 2, volumeTop + (this@Canvas.size.height * 0.25f - volHeight)),
                        size = androidx.compose.ui.geometry.Size(candleWidth, volHeight)
                    )
                }
                
                // Draw crosshair vertical line if touching
                touchPosition?.let { pos ->
                    drawLine(
                        color = Color.Blue.copy(alpha = 0.5f),
                        start = Offset(pos.x, 0f),
                        end = Offset(pos.x, chartHeight),
                        strokeWidth = 2f
                    )
                }
            }
            
            // Floating popup on touch
            touchPosition?.let { pos ->
                touchedCandle?.let { candle ->
                    FloatingCandlePopup(
                        candle = candle,
                        previousClose = series.getOrNull(touchedIndex!! - 1)?.close,
                        xPosition = pos.x,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        }
    }
}

@Composable
private fun CandleDataRow(
    candle: PriceCandle,
    previousClose: Double?,
    isTouched: Boolean
) {
    val change = previousClose?.let { candle.close - it }
    val changePercent = previousClose?.let { ((candle.close - it) / it) * 100 }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTouched) 
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTouched) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    append("O: ${formatPrice(candle.open)}  ")
                    append("H: ${formatPrice(candle.high)}  ")
                    append("L: ${formatPrice(candle.low)}  ")
                    append("C: ${formatPrice(candle.close)}  ")
                    append("Vol: ${formatVolume(candle.volume)}")
                    changePercent?.let {
                        append("  ${formatPercentChange(it)}")
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.semantics {
                    contentDescription = "Open ${formatPrice(candle.open)}, " +
                        "High ${formatPrice(candle.high)}, " +
                        "Low ${formatPrice(candle.low)}, " +
                        "Close ${formatPrice(candle.close)}, " +
                        "Volume ${formatVolume(candle.volume)}" +
                        (changePercent?.let { ", Change ${formatPercentChange(it)}" } ?: "")
                }
            )
        }
    }
}

@Composable
private fun FloatingCandlePopup(
    candle: PriceCandle,
    previousClose: Double?,
    xPosition: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val change = previousClose?.let { candle.close - it }
    val changePercent = previousClose?.let { ((candle.close - it) / it) * 100 }
    
    Box(
        modifier = modifier
            .offset { IntOffset(xPosition.toInt() + with(density) { 8.dp.toPx() }.toInt(), with(density) { 8.dp.toPx() }.toInt()) }
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.semantics {
                contentDescription = "Candle details popup"
            }
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = candle.time.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "O: ${formatPrice(candle.open)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "H: ${formatPrice(candle.high)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "L: ${formatPrice(candle.low)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "C: ${formatPrice(candle.close)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Vol: ${formatVolume(candle.volume)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                changePercent?.let {
                    Text(
                        text = "Change: ${formatPercentChange(it)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (it >= 0) Color(0xFF26A69A) else Color(0xFFEF5350)
                    )
                }
            }
        }
    }
}

private fun formatTimeForRange(time: java.time.LocalDateTime, series: List<PriceCandle>): String {
    if (series.isEmpty()) return ""
    
    val duration = java.time.Duration.between(series.first().time, series.last().time)
    
    return when {
        duration.toDays() <= 1 -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
        duration.toDays() <= 31 -> time.format(DateTimeFormatter.ofPattern("MMM dd"))
        duration.toDays() <= 365 -> time.format(DateTimeFormatter.ofPattern("MMM dd"))
        else -> time.format(DateTimeFormatter.ofPattern("MMM yy"))
    }
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1fB", volume / 1_000_000_000.0)
        volume >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", volume / 1_000_000.0)
        volume >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", volume / 1_000.0)
        else -> volume.toString()
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
private fun AiReasonRow(reason: AiScoreReason) {
    var expanded by rememberSaveable(reason.title) { mutableStateOf(false) }
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
                text = reason.detail,
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
private fun IndicatorRow(
    label: String,
    value: String,
    explanation: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            if (explanation != null) {
                Spacer(modifier = Modifier.width(4.dp))
                InfoIconButton(explanation = explanation)
            }
        }
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
