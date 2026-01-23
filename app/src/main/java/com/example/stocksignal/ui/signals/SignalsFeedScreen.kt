package com.example.stocksignal.ui.signals

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.ui.components.SignalChip
import com.example.stocksignal.ui.components.SignalScoreRow
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.time.format.DateTimeFormatter

@Composable
fun SignalsFeedRoute(
    onOpenDetail: (String, String?) -> Unit,
    viewModel: SignalsFeedViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignalsFeedScreen(
        state = state,
        onOpenDetail = onOpenDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignalsFeedScreen(
    state: SignalsFeedUiState,
    onOpenDetail: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(SignalsFilter.ALL) }
    val filtered = when (filter) {
        SignalsFilter.ALL -> state.events
        SignalsFilter.WATCHLIST -> state.events.filter { it.type == NotificationEventType.WATCHLIST_SIGNAL }
        SignalsFilter.MOVERS -> state.events.filter { it.type == NotificationEventType.MARKET_MOVER }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StockSignalDimens.cardPadding)
    ) {
        Text(text = "Signals", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Latest signals for your watchlist and market movers.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SignalsFilter.values().forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            StockCard {
                Text(text = "No signals yet.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Signals appear when we detect strong buy/sell events.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.id }) { event ->
                    SignalEventCard(
                        event = event,
                        onOpenDetail = { onOpenDetail(event.ticker, event.id) }
                    )
                }
            }
        }
    }
}

private enum class SignalsFilter(val label: String) {
    ALL("All"),
    WATCHLIST("Watchlist"),
    MOVERS("Movers")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalEventCard(
    event: NotificationEvent,
    onOpenDetail: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, HH:mm") }
    val avg = event.averageScore ?: event.score
    val mode = event.modeScore ?: avg
    val aiScore = event.aiScore
    val aiConfidence = event.aiConfidence
    val aiScoreLabel = aiScore?.toString() ?: "--"
    val aiConfidenceLabel = aiConfidence?.let { "${it}%" } ?: "--"

    StockCard(
        modifier = Modifier.clickable { onOpenDetail() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = event.ticker, style = MaterialTheme.typography.headlineMedium)
                Text(text = formatter.format(event.generatedAt), style = MaterialTheme.typography.bodySmall)
            }
            SignalChip(tier = event.tier, label = event.tier.label)
        }

        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(label = if (event.type == NotificationEventType.MARKET_MOVER) "Market mover" else "Watchlist")
            TagChip(label = "AI Score $aiScoreLabel")
            TagChip(label = "AI Conf $aiConfidenceLabel")
        }

        Spacer(modifier = Modifier.height(8.dp))
        SignalScoreRow(
            tier = event.tier,
            score = aiScore,
            confidence = aiConfidence,
            scoreLabel = "AI Score",
            confidenceLabel = "AI Confidence"
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Avg $avg • Mode $mode • Rule Conf ${event.confidence}%",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )

        Spacer(modifier = Modifier.height(8.dp))
        MiniSparkline()

        if (!event.aiSummary.isNullOrBlank() || event.aiReasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI reasoning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            if (!event.aiSummary.isNullOrBlank()) {
                Text(text = event.aiSummary, style = MaterialTheme.typography.bodySmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                event.aiReasons.take(2).forEach { reason ->
                    Text(text = "• ${reason.title}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            event.reasons.take(3).forEach { reason ->
                Text(text = "• ${reason.title}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onOpenDetail) { Text("View") }
    }
}

@Composable
private fun MiniSparkline() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        val stepX = size.width / 8f
        val mid = size.height / 2f
        val lineColor = Color(0xFF4DA3FF)
        val points = listOf(
            0f to mid + 5f,
            stepX to mid - 3f,
            stepX * 2 to mid + 2f,
            stepX * 3 to mid - 6f,
            stepX * 4 to mid + 4f,
            stepX * 5 to mid - 1f,
            stepX * 6 to mid + 3f,
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
}
