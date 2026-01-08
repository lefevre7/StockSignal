package com.example.stocksignal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
) {
    val colors = signalColors(tier)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary, RoundedCornerShape(StockSignalDimens.chipRadius))
            .padding(padding)
            .semantics { contentDescription = label }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
