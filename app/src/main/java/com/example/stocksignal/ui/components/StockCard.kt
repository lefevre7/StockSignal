package com.example.stocksignal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stocksignal.ui.theme.StockSignalDimens
import com.example.stocksignal.ui.theme.UiSurface

@Composable
fun StockCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(StockSignalDimens.cardPadding),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StockSignalDimens.cardRadius),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(cardGradient(highlight))
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
private fun cardGradient(highlight: Boolean): Brush {
    val surface = UiSurface
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = if (highlight) 0.28f else 0.12f)
    return Brush.linearGradient(listOf(surface, glow))
}
