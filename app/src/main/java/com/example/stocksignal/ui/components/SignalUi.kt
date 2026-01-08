package com.example.stocksignal.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.stocksignal.domain.model.SignalTier
import com.example.stocksignal.ui.theme.AccentGreen
import com.example.stocksignal.ui.theme.Amber
import com.example.stocksignal.ui.theme.MidGreen
import com.example.stocksignal.ui.theme.NeutralGray
import com.example.stocksignal.ui.theme.StrongRed

data class SignalUiColors(
    val primary: Color,
    val background: Color,
    val content: Color
)

@Composable
fun signalColors(tier: SignalTier): SignalUiColors {
    val primary = when (tier) {
        SignalTier.STRONG_BUY -> AccentGreen
        SignalTier.BUY -> MidGreen
        SignalTier.NEUTRAL -> NeutralGray
        SignalTier.SELL -> Amber
        SignalTier.STRONG_SELL -> StrongRed
    }
    val background = primary.copy(alpha = 0.18f)
    val content = if (tier == SignalTier.NEUTRAL) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }
    return SignalUiColors(primary = primary, background = background, content = content)
}
