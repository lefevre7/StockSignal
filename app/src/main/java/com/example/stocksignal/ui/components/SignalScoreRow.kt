package com.example.stocksignal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.stocksignal.domain.model.SignalTier

@Composable
fun SignalScoreRow(
    tier: SignalTier,
    score: Int,
    confidence: Int?,
    modifier: Modifier = Modifier
) {
    val label = tier.label
    val colors = signalColors(tier)
    val desc = buildString {
        append("$label, score $score")
        if (confidence != null) append(", confidence $confidence percent")
    }
    Row(
        modifier = modifier.semantics { contentDescription = desc },
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SignalChip(
            tier = tier,
            label = label
        )
        Text(
            text = "Score $score/100",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.primary
        )
        if (confidence != null) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "$confidence%",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.primary
            )
        }
    }
}
