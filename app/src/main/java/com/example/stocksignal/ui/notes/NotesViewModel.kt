package com.example.stocksignal.ui.notes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.NoteEntity
import com.example.stocksignal.data.local.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notesRepository: NotesRepository
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotesUiState> = combine(
        notesRepository.notesFlow,
        _errorMessage
    ) { notes, error ->
        NotesUiState(notes = notes, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun saveNote(symbol: String, content: String) {
        val normalized = symbol.trim().uppercase()
        val noteContent = content.trim()
        if (normalized.isBlank() || noteContent.isBlank()) return
        viewModelScope.launch {
            try {
                notesRepository.upsert(
                    NoteEntity(
                        symbol = normalized,
                        content = noteContent,
                        updatedAt = LocalDateTime.now()
                    )
                )
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error saving note for $normalized", e)
                _errorMessage.value = "Failed to save note: ${e.message}"
            }
        }
    }

    fun deleteNote(symbol: String) {
        val normalized = symbol.trim().uppercase()
        if (normalized.isBlank()) return
        viewModelScope.launch {
            try {
                notesRepository.delete(normalized)
                _errorMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting note for $normalized", e)
                _errorMessage.value = "Failed to delete note: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    companion object {
        private const val TAG = "NotesViewModel"
    }
}

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList(),
    val errorMessage: String? = null
)
