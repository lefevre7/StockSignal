package com.example.stocksignal.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stocksignal.data.local.entity.NoteEntity
import com.example.stocksignal.ui.components.StockCard
import com.example.stocksignal.ui.components.TagChip
import com.example.stocksignal.ui.theme.StockSignalDimens
import java.time.format.DateTimeFormatter

@Composable
fun NotesRoute(
    initialSymbol: String? = null,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NotesScreen(
        state = state,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::deleteNote,
        onClearError = viewModel::clearError,
        initialSymbol = initialSymbol
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NotesUiState,
    onSaveNote: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onClearError: () -> Unit = {},
    initialSymbol: String? = null,
    modifier: Modifier = Modifier
) {
    var symbolInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(initialSymbol) {
        if (!initialSymbol.isNullOrBlank()) {
            symbolInput = initialSymbol
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(StockSignalDimens.cardPadding)
    ) {
        Text(text = "Notes", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "One note per ticker — quick context for your portfolio.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        state.errorMessage?.let { error ->
            ErrorBanner(
                message = error,
                onDismiss = onClearError
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        StockCard {
            Text(text = "Add or edit note", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = symbolInput,
                onValueChange = { symbolInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ticker") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = contentInput,
                onValueChange = { contentInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { onSaveNote(symbolInput, contentInput) }) { Text("Save") }
                TextButton(onClick = { symbolInput = ""; contentInput = "" }) { Text("Clear") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (state.notes.isEmpty()) {
            StockCard {
                Text(text = "No notes yet.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add a ticker note to track your ideas.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.notes, key = { it.symbol }) { note ->
                    NoteCard(
                        note = note,
                        onSelect = {
                            symbolInput = note.symbol
                            contentInput = note.content
                        },
                        onDelete = { onDeleteNote(note.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, HH:mm") }

    StockCard(
        modifier = Modifier.clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = note.symbol, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Updated ${note.updatedAt.format(formatter)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TagChip(label = "Note")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = note.content, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onSelect) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
