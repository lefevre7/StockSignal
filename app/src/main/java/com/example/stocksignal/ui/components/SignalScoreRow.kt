package com.example.stocksignal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.stocksignal.domain.model.SignalTier

@Composable
fun SignalScoreRow(
    tier: SignalTier,
    score: Int?,
    confidence: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    scoreLabel: String = "Score",
    confidenceLabel: String = "Confidence"
) {
    var showConfidenceDialog by remember { mutableStateOf(false) }
    val label = tier.label
    val colors = signalColors(tier)
    val scoreText = score?.toString() ?: "--"
    val confidenceText = confidence?.let { "$it%" } ?: "--"
    val desc = buildString {
        append("$label, ${scoreLabel.lowercase()} $scoreText")
        append(", ${confidenceLabel.lowercase()} $confidenceText")
    }
    Row(
        modifier = modifier.semantics { contentDescription = desc },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SignalChip(
            tier = tier,
            label = label
        )
        Text(
            text = if (score != null) "$scoreLabel $score/100" else "$scoreLabel --",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.primary
        )
        Spacer(modifier = Modifier.width(2.dp))
        val compactLabel = if (confidenceLabel.length <= 6) {
            confidenceLabel
        } else {
            val parts = confidenceLabel.split(" ")
            if (parts.size > 1) "${parts.first()} Conf" else "Conf"
        }
        Text(
            text = if (compact) "$compactLabel: $confidenceText" else "$confidenceLabel: $confidenceText",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = colors.primary
        )
        if (confidence != null) {
            IconButton(
                onClick = { showConfidenceDialog = true },
                modifier = Modifier.width(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Confidence explanation",
                    tint = colors.primary.copy(alpha = 0.7f),
                    modifier = Modifier.width(16.dp)
                )
            }
        }
    }
    
    if (showConfidenceDialog) {
        ConfidenceExplanationDialog(
            onDismiss = { showConfidenceDialog = false }
        )
    }
}
