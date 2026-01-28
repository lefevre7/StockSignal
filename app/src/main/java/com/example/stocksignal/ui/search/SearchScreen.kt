package com.example.stocksignal.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.VisibleForTesting
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.domain.model.RecentSearch
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.ui.components.CompanyExchangeText
import com.example.stocksignal.ui.components.SignalChip
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.util.Locale
import kotlin.math.abs

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenMovers: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onAddToWatchlist = viewModel::addToWatchlist,
        onToggleAlert = viewModel::setAlertEnabled,
        onSelectRecentSearch = viewModel::selectRecentSearch,
        onClearHistory = viewModel::clearHistory,
        onSelectQuickFilter = viewModel::selectQuickFilter,
        onBack = onBack,
        onOpenMovers = onOpenMovers,
        onOpenDetail = onOpenDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onAddToWatchlist: (SearchResult, Boolean) -> Unit,
    onToggleAlert: (String, Boolean) -> Unit,
    onSelectRecentSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSelectQuickFilter: (MarketMoverDirection) -> Unit,
    onBack: () -> Unit,
    onOpenMovers: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val localAlertOverrides = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StockSignalDimens.cardPadding)
    ) {
        SearchTopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.query.isBlank()) {
                item {
                    QuickFilters(
                        selected = state.quickFilter,
                        onSelect = onSelectQuickFilter,
                        onOpenMovers = onOpenMovers
                    )
                }

                val movers = when (state.quickFilter) {
                    MarketMoverDirection.MOST_ACTIVE -> state.topMostActive
                    MarketMoverDirection.INCREASERS -> state.topIncreasers
                    MarketMoverDirection.DECREASERS -> state.topDecreasers
                }

                if (movers.isNotEmpty()) {
                    val title = when (state.quickFilter) {
                        MarketMoverDirection.MOST_ACTIVE -> "Most Active"
                        MarketMoverDirection.INCREASERS -> "Top Advancers"
                        MarketMoverDirection.DECREASERS -> "Top Decliners"
                    }
                    item {
                        SectionHeader(title = title)
                    }
                    items(movers.take(6), key = { it.ticker }) { mover ->
                        MarketMoverSuggestion(
                            mover = mover,
                            isInWatchlist = state.watchlist.containsKey(mover.ticker),
                            onAdd = { onAddToWatchlist(toSearchResult(mover), true) },
                            onOpenDetail = { onOpenDetail(mover.ticker) }
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "Recent searches",
                        action = if (state.recentSearches.isNotEmpty()) {
                            { TextButton(onClick = onClearHistory) { Text("Clear") } }
                        } else {
                            null
                        }
                    )
                }
                if (state.recentSearches.isEmpty()) {
                    item {
                        Text(
                            text = "No recent searches yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(state.recentSearches, key = { it.query }) { recent ->
                        RecentSearchRow(recent = recent, onClick = { onSelectRecentSearch(recent.query) })
                    }
                }
            } else {
                if (state.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.errorMessage != null) {
                    item {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (state.results.isEmpty()) {
                    item {
                        Text(
                            text = "No results for that query.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    items(state.results, key = { it.symbol }) { result ->
                        val watchlistEntry = state.watchlist[result.symbol]
                        val isInWatchlist = watchlistEntry != null
                        val alertsEnabled = if (isInWatchlist) {
                            watchlistEntry?.alertEnabled == true
                        } else {
                            localAlertOverrides[result.symbol] ?: true
                        }
                        val isMover = state.moverSymbols.contains(result.symbol)

                        SearchResultRow(
                            result = result,
                            isInWatchlist = isInWatchlist,
                            isMover = isMover,
                            alertsEnabled = alertsEnabled,
                            onAdd = {
                                onAddToWatchlist(result, alertsEnabled)
                            },
                            onOpenDetail = { onOpenDetail(result.symbol) },
                            onAlertsToggle = { enabled ->
                                if (isInWatchlist) {
                                    onToggleAlert(result.symbol, enabled)
                                } else {
                                    localAlertOverrides[result.symbol] = enabled
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Search", style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search tickers, names, exchange") },
        leadingIcon = { 
            Icon(
                Icons.Filled.Search, 
                contentDescription = null,
                modifier = Modifier.padding(4.dp),
                tint = MaterialTheme.colorScheme.primary
            ) 
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickFilters(
    selected: MarketMoverDirection,
    onSelect: (MarketMoverDirection) -> Unit,
    onOpenMovers: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == MarketMoverDirection.MOST_ACTIVE,
            onClick = { onSelect(MarketMoverDirection.MOST_ACTIVE) },
            label = { Text("Most Active") }
        )
        FilterChip(
            selected = selected == MarketMoverDirection.INCREASERS,
            onClick = { onSelect(MarketMoverDirection.INCREASERS) },
            label = { Text("Advancers") }
        )
        FilterChip(
            selected = selected == MarketMoverDirection.DECREASERS,
            onClick = { onSelect(MarketMoverDirection.DECREASERS) },
            label = { Text("Decliners") }
        )
        TextButton(onClick = onOpenMovers) { Text("Open Market Movers") }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        action?.invoke()
    }
}

@Composable
private fun MarketMoverSuggestion(
    mover: MarketMoverItem,
    isInWatchlist: Boolean,
    onAdd: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val changeText = formatPercentChange(mover.percentChange)
    val changeColor = when {
        mover.percentChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        mover.percentChange >= 0 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    StockCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .background(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = mover.ticker,
                        style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    CompanyExchangeText(
                        companyName = mover.companyName,
                        exchange = mover.exchange,
                        style = MaterialTheme.typography.bodySmall.copy(color = androidx.compose.ui.graphics.Color.White)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatPrice(mover.price),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                Text(
                    text = changeText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = changeColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip(label = "Mover")
            mover.rank?.let { TagChip(label = "Rank #$it") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isInWatchlist) {
                TagChip(label = "In watchlist")
            } else {
                TextButton(onClick = onAdd) { Text("Add") }
            }
            TextButton(onClick = onOpenDetail) { Text("Details") }
        }
    }
}

@Composable
private fun RecentSearchRow(recent: RecentSearch, onClick: () -> Unit) {
    val countLabel = if (recent.count <= 1) "1 time" else "${recent.count} times"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onClick) { Text(recent.query) }
        Text(
            text = countLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultRow(
    result: SearchResult,
    isInWatchlist: Boolean,
    isMover: Boolean,
    alertsEnabled: Boolean,
    onAdd: () -> Unit,
    onOpenDetail: () -> Unit,
    onAlertsToggle: (Boolean) -> Unit
) {
    val hasPriceAndChange = result.price != null && result.percentChange != null
    val priceText = formatPrice(result.price)
    val changeText = formatPercentChange(result.percentChange)
    val changeColor = when {
        result.percentChange == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        result.percentChange > 0 -> MaterialTheme.colorScheme.primary
        result.percentChange < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    StockCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .background(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    if (hasPriceAndChange) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = result.symbol,
                                style = MaterialTheme.typography.headlineMedium,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                text = " - $priceText",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                text = " ($changeText)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = changeColor
                            )
                        }
                    } else {
                        Text(
                            text = result.symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    CompanyExchangeText(
                        companyName = result.companyName,
                        exchange = result.exchange,
                        style = MaterialTheme.typography.bodySmall.copy(color = androidx.compose.ui.graphics.Color.White)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isInWatchlist) TagChip(label = "Watchlist")
            if (isMover) TagChip(label = "Mover")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Alerts", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(checked = alertsEnabled, onCheckedChange = onAlertsToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenDetail) { Text("Details") }
                if (!isInWatchlist) {
                    TextButton(onClick = onAdd) { Text("Add") }
                } else {
                    SignalChip(label = "Added", tier = SignalTier.NEUTRAL)
                }
            }
        }
    }
}

private fun toSearchResult(item: MarketMoverItem): SearchResult {
    return SearchResult(
        symbol = item.ticker,
        companyName = item.companyName,
        exchange = item.exchange,
        price = item.price,
        percentChange = item.percentChange
    )
}

@VisibleForTesting
internal fun formatPrice(price: Double?): String {
    if (price == null) return "—"
    val absPrice = abs(price)
    val decimals = when {
        absPrice >= 100.0 -> 2
        absPrice >= 1.0 -> 2
        absPrice >= 0.1 -> 3
        absPrice >= 0.01 -> 4
        else -> 6
    }
    return "$" + String.format(Locale.getDefault(), "%.${decimals}f", price)
}

@VisibleForTesting
internal fun formatPercentChange(change: Double?): String {
    if (change == null) return "—"
    val sign = if (change > 0) "+" else if (change < 0) "-" else ""
    val magnitude = abs(change)
    return sign + String.format(Locale.getDefault(), "%.2f", magnitude) + "%"
}
