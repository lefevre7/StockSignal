package com.example.stocksignal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.ui.theme.StockSignalDimens

@Composable
fun SignalBadge(
    tier: SignalTier,
    score: Int,
    confidence: Int?,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    ticker: String? = null
) {
    val colors = signalColors(tier)
    val label = labelOverride ?: tier.label
    val contentDesc = buildString {
        if (!ticker.isNullOrBlank()) append("$ticker: ")
        append("$label, score $score")
        if (confidence != null) append(", confidence $confidence percent")
    }
    val brush = Brush.radialGradient(
        colors = listOf(colors.primary, colors.background),
        radius = 140f
    )

    Box(
        modifier = modifier
            .size(StockSignalDimens.badgeSize)
            .clip(CircleShape)
            .background(brush)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = colors.content,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$score",
                color = colors.content,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace)
            )
            if (confidence != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ConfidenceRing(
                    confidence = confidence,
                    color = colors.content
                )
            }
        }
    }
}

@Composable
private fun ConfidenceRing(
    confidence: Int,
    color: Color
) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { (confidence / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = color
        )
        Text(
            text = "${confidence.coerceIn(0, 100)}%",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color
        )
    }
}
