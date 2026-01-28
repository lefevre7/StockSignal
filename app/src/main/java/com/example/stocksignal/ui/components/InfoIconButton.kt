package com.example.stocksignal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoIconButton(
    explanation: String,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(20.dp)
            .clickable { showDialog = true }
            .padding(2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "More info",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
    
    if (showDialog) {
        InfoDialog(
            explanation = explanation,
            onDismiss = { showDialog = false }
        )
    }
}
