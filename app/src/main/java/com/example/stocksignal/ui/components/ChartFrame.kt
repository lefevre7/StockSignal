package com.example.stocksignal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.stocksignal.ui.theme.StockSignalDimens

@Composable
fun ChartFrame(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GridBackdrop()
            Column(modifier = Modifier.padding(StockSignalDimens.cardPadding)) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun GridBackdrop() {
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = size.width / 8f
        val stepY = size.height / 6f
        for (i in 1..7) {
            drawLine(
                color = lineColor,
                start = Offset(stepX * i, 0f),
                end = Offset(stepX * i, size.height),
                strokeWidth = 1f
            )
        }
        for (i in 1..5) {
            drawLine(
                color = lineColor,
                start = Offset(0f, stepY * i),
                end = Offset(size.width, stepY * i),
                strokeWidth = 1f
            )
        }
    }
}
