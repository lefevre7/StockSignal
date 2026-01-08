package com.example.stocksignal.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.NoteEntity
import com.example.stocksignal.data.local.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notesRepository: NotesRepository
) : ViewModel() {

    val uiState: StateFlow<NotesUiState> = notesRepository.notesFlow
        .map { notes -> NotesUiState(notes = notes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun saveNote(symbol: String, content: String) {
        val normalized = symbol.trim().uppercase()
        val noteContent = content.trim()
        if (normalized.isBlank() || noteContent.isBlank()) return
        viewModelScope.launch {
            notesRepository.upsert(
                NoteEntity(
                    symbol = normalized,
                    content = noteContent,
                    updatedAt = LocalDateTime.now()
                )
            )
        }
    }

    fun deleteNote(symbol: String) {
        val normalized = symbol.trim().uppercase()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            notesRepository.delete(normalized)
        }
    }
}

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList()
)
