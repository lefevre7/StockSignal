package com.example.stocksignal.ui.marketmovers

import androidx.compose.foundation.Canvas
import com.example.stocksignal.domain.model.PriceCandle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.ui.components.CompanyExchangeText
import com.example.stocksignal.ui.components.SignalChip
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun MarketMoversRoute(
    onOpenDetail: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    viewModel: MarketMoversViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MarketMoversScreen(
        state = state,
        onDirectionSelected = viewModel::selectDirection,
        onRefresh = { viewModel.refresh() },
        onOpenDetail = onOpenDetail,
        onOpenAlert = onOpenAlert,
        onAddToWatchlist = viewModel::addToWatchlist
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketMoversScreen(
    state: MarketMoversUiState,
    onDirectionSelected: (MarketMoverDirection) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenAlert: (String) -> Unit,
    onAddToWatchlist: (MarketMoverItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StockSignalDimens.cardPadding)
    ) {
        MarketMoversTopBar(onRefresh = onRefresh)
        Spacer(modifier = Modifier.height(12.dp))
        MarketMoverDirectionTabs(
            selected = state.direction,
            onDirectionSelected = onDirectionSelected
        )
        Spacer(modifier = Modifier.height(12.dp))
        MarketMoversMeta(state = state)
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Loading movers…", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.errorMessage != null && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
            }
            state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "No movers found for this range.", style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(state.items, key = { index, item -> "${item.ticker}_$index" }) { _, item ->
                        MarketMoverCard(
                            item = item,
                            isInWatchlist = state.watchlistSymbols.contains(item.ticker),
                            onAdd = { onAddToWatchlist(item) },
                            onAlert = { onOpenAlert(item.ticker) },
                            onOpenDetail = { onOpenDetail(item.ticker) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketMoversTopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Market Movers", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Most active, advancers, and decliners from Stooq.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
        }
    }
}

@Composable
private fun MarketMoverDirectionTabs(
    selected: MarketMoverDirection,
    onDirectionSelected: (MarketMoverDirection) -> Unit
) {
    val tabs = listOf(
        MarketMoverDirection.MOST_ACTIVE to "Most Active",
        MarketMoverDirection.INCREASERS to "Advancers",
        MarketMoverDirection.DECREASERS to "Decliners"
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, (direction, title) ->
            Tab(
                selected = tabs[index].first == selected,
                onClick = { onDirectionSelected(direction) },
                text = { Text(text = title) }
            )
        }
    }
}

@Composable
private fun MarketMoversMeta(state: MarketMoversUiState) {
    val timeLabel = state.lastUpdated?.let {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        it.format(formatter)
    }
    val prefix = when {
        timeLabel == null -> "Updated —"
        state.isFallback -> "Cached $timeLabel"
        else -> "Updated $timeLabel"
    }
    val suffix = if (state.isStale && timeLabel != null) " (stale)" else ""
    val updatedAt = prefix + suffix
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "Items: ${state.items.size}", style = MaterialTheme.typography.bodySmall)
        Text(text = updatedAt, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MarketMoverCard(
    item: MarketMoverItem,
    isInWatchlist: Boolean,
    onAdd: () -> Unit,
    onAlert: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val priceText = formatPrice(item.price)
    val changeText = formatPercentChange(item.percentChange)
    val changeColor = when {
        item.percentChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        item.percentChange >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val signalScore = item.signalScore
    val signalTier = signalScore?.let { SignalTier.fromScore(it) }
    val signalLabel = item.signalLabel ?: signalTier?.label

    StockCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = item.ticker, style = MaterialTheme.typography.headlineMedium)
                CompanyExchangeText(
                    companyName = item.companyName,
                    exchange = item.exchange,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = changeColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.rank?.let { TagChip(label = "Rank #$it") }
            if (isInWatchlist) {
                TagChip(label = "Already in watchlist")
            }
            if (signalTier != null && signalLabel != null) {
                SignalChip(tier = signalTier, label = signalLabel)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        MarketMoverSparkline(series = item.series)

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isInWatchlist) {
                TextButton(onClick = onAdd) { Text("Add") }
            }
            TextButton(onClick = onAlert) { Text("Alert") }
            TextButton(onClick = onOpenDetail) { Text("Details") }
        }
    }
}

@Composable
private fun MarketMoverSparkline(series: List<PriceCandle>) {
    if (series.isEmpty()) {
        // Show placeholder sparkline while loading
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val stepX = size.width / 8f
            val mid = size.height / 2f
            val lineColor = Color(0xFF4DA3FF).copy(alpha = 0.3f)
            val points = listOf(
                0f to mid + 4f,
                stepX to mid - 2f,
                stepX * 2 to mid + 1f,
                stepX * 3 to mid - 6f,
                stepX * 4 to mid + 5f,
                stepX * 5 to mid - 2f,
                stepX * 6 to mid + 2f,
                stepX * 7 to mid - 4f,
                stepX * 8 to mid
            )
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(points[i].first, points[i].second),
                    end = androidx.compose.ui.geometry.Offset(points[i + 1].first, points[i + 1].second),
                    strokeWidth = 3f
                )
            }
        }
        return
    }

    // Real sparkline using close prices (like watchlist)
    val closes = series.map { it.close }
    val min = closes.minOrNull() ?: return
    val max = closes.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        if (closes.size == 1) {
            val y = size.height - ((closes.first() - min) / range).toFloat() * size.height
            drawLine(
                color = Color(0xFF4DA3FF),
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
                color = Color(0xFF4DA3FF),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }
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
