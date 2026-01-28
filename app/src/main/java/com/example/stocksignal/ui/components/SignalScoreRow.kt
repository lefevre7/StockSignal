package com.example.stocksignal.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.ui.model.AiGenerationState
import com.example.stocksignal.ui.components.MetricExplanations

@Composable
fun SignalScoreRow(
    tier: SignalTier,
    score: Int?,
    confidence: Int?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    scoreLabel: String = "Score",
    confidenceLabel: String = "Confidence",
    aiGenerationState: AiGenerationState = AiGenerationState.IDLE
) {
    val label = tier.label
    val colors = signalColors(tier)
    val scoreText = score?.toString() ?: "--"
    val confidenceText = confidence?.let { "$it%" } ?: "--"
    val desc = buildString {
        append("$label, ${scoreLabel.lowercase()} $scoreText")
        append(", ${confidenceLabel.lowercase()} $confidenceText")
        when (aiGenerationState) {
            AiGenerationState.QUEUED -> append(", queued for AI generation")
            AiGenerationState.GENERATING -> append(", AI generation in progress")
            else -> {}
        }
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
        
        // Show loading indicator based on AI generation state
        when (aiGenerationState) {
            AiGenerationState.QUEUED -> {
                // Pulsing dot for queued state
                PulsingDot(color = colors.primary)
            }
            AiGenerationState.GENERATING -> {
                // Circular progress indicator for generating state
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = colors.primary,
                    strokeWidth = 2.dp
                )
            }
            else -> {}
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (score != null) "$scoreLabel $score/100" else "$scoreLabel --",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.primary
            )
            Spacer(modifier = Modifier.width(2.dp))
            InfoIconButton(explanation = MetricExplanations.SCORE_CALCULATION)
        }
        Spacer(modifier = Modifier.width(2.dp))
        val compactLabel = if (confidenceLabel.length <= 6) {
            confidenceLabel
        } else {
            val parts = confidenceLabel.split(" ")
            if (parts.size > 1) "${parts.first()} Conf" else "Conf"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (compact) "$compactLabel: $confidenceText" else "$confidenceLabel: $confidenceText",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.primary
            )
            Spacer(modifier = Modifier.width(2.dp))
            InfoIconButton(explanation = MetricExplanations.CONFIDENCE)
        }
    }
}

@Composable
private fun PulsingDot(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(12.dp)
            .scale(scale)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}
