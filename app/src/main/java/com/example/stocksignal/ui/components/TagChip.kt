package com.example.stocksignal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.stocksignal.ui.theme.StockSignalDimens

@Composable
fun TagChip(
    label: String,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
) {
    val background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Text(
        text = label,
        modifier = modifier
            .background(background, RoundedCornerShape(StockSignalDimens.chipRadius))
            .padding(padding)
            .semantics { contentDescription = label },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}
