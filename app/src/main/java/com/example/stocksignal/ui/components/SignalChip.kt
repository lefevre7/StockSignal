package com.example.stocksignal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.ui.theme.StockSignalDimens

@Composable
fun SignalChip(
    tier: SignalTier,
    label: String,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    showInfoIcon: Boolean = false
) {
    val colors = signalColors(tier)
    val explanation = when (tier) {
        SignalTier.STRONG_BUY -> MetricExplanations.STRONG_BUY
        SignalTier.BUY -> MetricExplanations.BUY
        SignalTier.NEUTRAL -> MetricExplanations.HOLD
        SignalTier.SELL -> MetricExplanations.SELL
        SignalTier.STRONG_SELL -> MetricExplanations.STRONG_SELL
    }
    
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary, RoundedCornerShape(StockSignalDimens.chipRadius))
            .padding(padding)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showInfoIcon) {
            Spacer(modifier = Modifier.width(4.dp))
            InfoIconButton(explanation = explanation)
        }
    }
}
