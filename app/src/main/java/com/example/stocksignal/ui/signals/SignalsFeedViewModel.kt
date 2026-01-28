package com.example.stocksignal.ui.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.domain.model.NotificationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignalsFeedViewModel @Inject constructor(
    private val signalsRepository: SignalsRepository
) : ViewModel() {

    val uiState: StateFlow<SignalsFeedUiState> = signalsRepository.eventsFlow
        .map { events ->
            val sorted = events.sortedByDescending { it.generatedAt }
            SignalsFeedUiState(events = sorted)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignalsFeedUiState())

    fun dismissEvent(id: String) {
        viewModelScope.launch {
            signalsRepository.dismissEvent(id)
        }
    }

    fun undoDismissEvent(id: String) {
        viewModelScope.launch {
            signalsRepository.undoDismissEvent(id)
        }
    }
}

data class SignalsFeedUiState(
    val events: List<NotificationEvent> = emptyList()
)
