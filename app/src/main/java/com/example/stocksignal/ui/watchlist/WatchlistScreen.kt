package com.example.stocksignal.ui.watchlist

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.domain.model.WatchlistItem
import com.example.stocksignal.ui.components.CompanyExchangeText
import com.example.stocksignal.ui.components.SignalBadge
import com.example.stocksignal.ui.components.SignalChip
import com.example.stocksignal.ui.components.SignalScoreRow
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.components.signalColors
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun WatchlistRoute(
    onSearchClick: () -> Unit = {},
    onOpenDetail: (String, String?) -> Unit = { _, _ -> },
    onAddNote: (String) -> Unit = {},
    onOpenAlert: (String) -> Unit = {},
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val items by viewModel.watchlistCards.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    WatchlistScreen(
        items = items,
        errorMessage = errorMessage,
        onClearError = viewModel::clearError,
        onReorder = viewModel::persistCustomOrder,
        onRemove = viewModel::remove,
        onSnooze = viewModel::snooze,
        onSearchClick = onSearchClick,
        onOpenDetail = onOpenDetail,
        onAddNote = onAddNote,
        onOpenAlert = onOpenAlert
    )
}

private enum class SortMode(val label: String) {
    STRONG_BUY_FIRST("Strong Buy"),
    STRONG_SELL_FIRST("Strong Sell"),
    ALPHABETICAL("A-Z"),
    PRICE_CHANGE("Price %"),
    CUSTOM("Custom")
}

private fun <T> MutableList<T>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    val targetIndex = if (to > from) to - 1 else to
    add(targetIndex, item)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchlistScreen(
    items: List<WatchlistCardState>,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onClearError: () -> Unit = {},
    onReorder: (List<WatchlistItem>) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onSnooze: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onOpenDetail: (String, String?) -> Unit = { _, _ -> },
    onAddNote: (String) -> Unit = {},
    onOpenAlert: (String) -> Unit = {}
) {
    var sortMode by remember { mutableStateOf(SortMode.STRONG_BUY_FIRST) }
    var groupByTag by remember { mutableStateOf(false) }
    val isCustomSort = sortMode == SortMode.CUSTOM
    val listState = rememberLazyListState()
    val customItems = remember { mutableStateListOf<WatchlistCardState>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var reorderPending by remember { mutableStateOf(false) }

    LaunchedEffect(isCustomSort) {
        if (isCustomSort) {
            groupByTag = false
        }
    }

    val sorted = remember(items, sortMode) {
        when (sortMode) {
            SortMode.STRONG_BUY_FIRST ->
                items.sortedByDescending { it.displaySignal()?.displayScore ?: Int.MIN_VALUE }
            SortMode.STRONG_SELL_FIRST ->
                items.sortedBy { it.displaySignal()?.displayScore ?: Int.MAX_VALUE }
            SortMode.ALPHABETICAL -> items.sortedBy { it.item.symbol }
            SortMode.PRICE_CHANGE -> items.sortedByDescending { it.percentChange ?: Double.NEGATIVE_INFINITY }
            SortMode.CUSTOM -> items.sortedBy { it.item.sortOrder ?: Int.MAX_VALUE }
        }
    }

    LaunchedEffect(sorted, isCustomSort) {
        if (isCustomSort) {
            customItems.clear()
            customItems.addAll(sorted)
        }
    }

    LaunchedEffect(isCustomSort, groupByTag) {
        if (!isCustomSort || groupByTag) {
            draggingIndex = null
            dragOffset = 0f
            reorderPending = false
        }
    }

    val displayItems = if (isCustomSort) customItems else sorted
    val reorderEnabled = isCustomSort && !groupByTag
    val finalizeReorder = {
        if (reorderPending && reorderEnabled) {
            onReorder(customItems.map { it.item })
            reorderPending = false
        }
        draggingIndex = null
        dragOffset = 0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StockSignalDimens.cardPadding)
    ) {
        WatchlistTopBar(onSearchClick = onSearchClick)
        Spacer(modifier = Modifier.height(12.dp))
        WatchlistSummary(items = items)
        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let { error ->
            ErrorBanner(
                message = error,
                onDismiss = onClearError
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Sort", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Group by tag", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = groupByTag,
                    onCheckedChange = { checked ->
                        if (!isCustomSort) {
                            groupByTag = checked
                        }
                    },
                    enabled = !isCustomSort
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortMode.values().forEach { mode ->
                FilterChip(
                    selected = sortMode == mode,
                    onClick = { sortMode = mode },
                    label = { Text(text = mode.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            EmptyWatchlist()
        } else {
            if (groupByTag) {
                val grouped = displayItems.groupBy { it.item.tags.firstOrNull() ?: "Untagged" }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.forEach { (tag, tagItems) ->
                        item(key = "header_$tag") {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(tagItems, key = { it.item.symbol }) { item ->
                            WatchlistCard(
                                item = item,
                                onClick = { onOpenDetail(item.item.symbol, null) },
                                onAddNote = { onAddNote(item.item.symbol) },
                                onSetAlert = { onOpenAlert(item.item.symbol) },
                                onRemove = { onRemove(item.item.symbol) },
                                onSnooze = { onSnooze(item.item.symbol) }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(displayItems, key = { _, item -> item.item.symbol }) { index, item ->
                        val isDragging = draggingIndex == index
                        val dragModifier = if (reorderEnabled) {
                            Modifier.pointerInput(reorderEnabled, customItems, draggingIndex) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingIndex = index },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                        dragOffset += dragAmount.y
                                        val layoutInfo = listState.layoutInfo
                                        val currentItemInfo = layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.index == currentIndex }
                                            ?: return@detectDragGesturesAfterLongPress
                                        val draggedCenter = currentItemInfo.offset +
                                            dragOffset + currentItemInfo.size / 2f
                                        val targetItemInfo = layoutInfo.visibleItemsInfo
                                            .firstOrNull { info ->
                                                draggedCenter.toInt() in info.offset..(info.offset + info.size)
                                            }
                                        if (targetItemInfo != null && targetItemInfo.index != currentIndex) {
                                            customItems.move(currentIndex, targetItemInfo.index)
                                            draggingIndex = targetItemInfo.index
                                            dragOffset += (currentItemInfo.offset - targetItemInfo.offset)
                                            reorderPending = true
                                        }
                                    },
                                    onDragEnd = { finalizeReorder() },
                                    onDragCancel = { finalizeReorder() }
                                )
                            }
                        } else {
                            Modifier
                        }

                        WatchlistCard(
                            item = item,
                            showDragHandle = reorderEnabled,
                            onClick = if (reorderEnabled) null else { { onOpenDetail(item.item.symbol, null) } },
                            onAddNote = { onAddNote(item.item.symbol) },
                            onSetAlert = { onOpenAlert(item.item.symbol) },
                            onRemove = { onRemove(item.item.symbol) },
                            onSnooze = { onSnooze(item.item.symbol) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                    shadowElevation = if (isDragging) 12f else 0f
                                }
                                .then(dragModifier)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistTopBar(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Your Signals", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Buy/sell signals and explanations — quick, clear, and private.",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { 
                Toast.makeText(context, "Coming soon but not currently needed", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.Person, contentDescription = "Profile")
            }
        }
    }
}

@Composable
private fun WatchlistSummary(items: List<WatchlistCardState>) {
    val activeSignals = items.count { card ->
        card.displaySignal()?.displayScore?.let { score -> abs(score) >= 30 } == true
    }
    val today = LocalDate.now()
    val newBuysToday = items.count {
        val signal = it.displaySignal() ?: return@count false
        signal.displayScore >= 60 && signal.generatedAt.toLocalDate() == today
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Active signals: $activeSignals",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "New buys today: $newBuysToday",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchlistCard(
    item: WatchlistCardState,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = false,
    onClick: (() -> Unit)? = null,
    onAddNote: () -> Unit,
    onSetAlert: () -> Unit,
    onRemove: () -> Unit,
    onSnooze: () -> Unit
) {
    val displaySignal = item.displaySignal()
    val displayScore = displaySignal?.displayScore ?: 0
    val displayConfidence = displaySignal?.displayConfidence
    val aiScore = displaySignal?.aiScore
    val aiConfidence = displaySignal?.aiConfidence
    val tier = SignalTier.fromScore(displayScore)
    val ruleAvg = displaySignal?.averageScore ?: displaySignal?.ruleScore
    val ruleMode = displaySignal?.modeScore ?: ruleAvg
    val ruleConfidence = displaySignal?.ruleConfidence
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val price = item.price
    val percentChange = item.percentChange
    val changeColor = when {
        percentChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        percentChange >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    val cardModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    StockCard(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = item.item.symbol, style = MaterialTheme.typography.headlineMedium)
                CompanyExchangeText(
                    companyName = item.item.companyName,
                    exchange = item.item.exchange,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showDragHandle) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                SignalBadge(
                    tier = tier,
                    score = displayScore,
                    confidence = displayConfidence,
                    ticker = item.item.symbol
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SignalScoreRow(
            tier = tier,
            score = aiScore,
            confidence = aiConfidence,
            compact = true,
            scoreLabel = "AI Score",
            confidenceLabel = "AI Conf"
        )

        if (displaySignal != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Avg ${ruleAvg ?: 0} • Mode ${ruleMode ?: 0} • Rule Conf ${ruleConfidence ?: 0}%",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Price ${formatPrice(price)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "1D ${formatPercentChange(percentChange)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = changeColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Sparkline(
            series = item.series,
            lineColor = signalColors(tier).primary
        )

        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item.item.tags.forEach { tag ->
                TagChip(label = tag)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        NotificationStatusRow(item = item.item, formatter = timeFormatter)

        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onAddNote) { Text("Add note") }
            TextButton(onClick = onSetAlert) { Text("Set alert") }
            TextButton(onClick = onRemove) { Text("Remove") }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onSnooze) { Text("Snooze") }
        }
    }
}

@Composable
private fun NotificationStatusRow(item: WatchlistItem, formatter: DateTimeFormatter) {
    val lastNotified = item.lastNotifiedAt
    val statusText = if (lastNotified == null) {
        "Last notified: —"
    } else {
        val elapsed = Duration.between(lastNotified, LocalDateTime.now())
        val human = when {
            elapsed.toHours() >= 24 -> "${elapsed.toDays()}d ago"
            elapsed.toHours() >= 1 -> "${elapsed.toHours()}h ago"
            else -> "${elapsed.toMinutes()}m ago"
        }
        "Last notified: $human (${lastNotified.format(formatter)})"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val icon = if (item.notificationActive) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
        if (item.alertSettings.enabled) {
            SignalChip(tier = SignalTier.NEUTRAL, label = "Alerts on")
        }
        val snoozedUntil = item.alertSettings.snoozedUntil
        if (snoozedUntil != null && snoozedUntil.isAfter(LocalDateTime.now())) {
            TagChip(label = "Snoozed until ${formatSnoozeTime(snoozedUntil)}")
        }
    }
}

@Composable
private fun Sparkline(series: List<PriceCandle>, lineColor: Color) {
    if (series.isEmpty()) {
        Text(
            text = "No chart data",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        return
    }
    val closes = series.map { it.close }
    val min = closes.minOrNull() ?: return
    val max = closes.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (closes.size == 1) {
            val y = size.height - ((closes.first() - min) / range).toFloat() * size.height
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 3f
            )
            return@Canvas
        }
        val stepX = size.width / (closes.size - 1)
        val points = closes.mapIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - min) / range).toFloat() * size.height
            androidx.compose.ui.geometry.Offset(x, y)
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
private fun EmptyWatchlist() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your watchlist is empty — search a ticker to get started.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "We’ll send signals for stocks you add.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private data class DisplaySignal(
    val displayScore: Int,
    val displayConfidence: Int?,
    val aiScore: Int?,
    val aiConfidence: Int?,
    val ruleScore: Int,
    val ruleConfidence: Int,
    val averageScore: Int?,
    val modeScore: Int?,
    val generatedAt: LocalDateTime
)

private fun WatchlistCardState.displaySignal(): DisplaySignal? {
    val computed = signal
    return if (computed != null) {
        DisplaySignal(
            displayScore = computed.displayScore,
            displayConfidence = computed.displayConfidence,
            aiScore = computed.aiScore,
            aiConfidence = computed.aiConfidence,
            ruleScore = computed.score,
            ruleConfidence = computed.confidence,
            averageScore = computed.averageScore,
            modeScore = computed.modeScore,
            generatedAt = computed.generatedAt
        )
    } else {
        null
    }
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

private fun formatSnoozeTime(time: LocalDateTime): String {
    val today = LocalDate.now()
    val pattern = if (time.toLocalDate() == today) "HH:mm" else "MMM d HH:mm"
    return time.format(DateTimeFormatter.ofPattern(pattern))
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
