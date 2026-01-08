package com.example.stocksignal.ui.marketmovers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class MarketMoversViewModel @Inject constructor(
    private val repository: MarketMoversRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketMoversUiState())
    val uiState: StateFlow<MarketMoversUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            _uiState.update { it.copy(range = settings.selectedMarketMoverRange) }
            loadMarketMovers()
        }
    }

    fun selectRange(range: MarketMoverRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        viewModelScope.launch {
            settingsRepository.setSelectedMarketMoverRange(range)
        }
        loadMarketMovers()
    }

    fun selectDirection(direction: MarketMoverDirection) {
        if (_uiState.value.direction == direction) return
        _uiState.update { it.copy(direction = direction) }
        loadMarketMovers()
    }

    fun refresh(forceRefresh: Boolean = true) {
        loadMarketMovers(forceRefresh)
    }

    private fun loadMarketMovers(forceRefresh: Boolean = false) {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getMarketMovers(snapshot.range, snapshot.direction, forceRefresh)) {
                is Result.Success -> {
                    val data = result.data
                    _uiState.update {
                        it.copy(
                            items = data.items,
                            isLoading = false,
                            errorMessage = null,
                            lastUpdated = data.fetchedAt,
                            isStale = data.isStale,
                            isFallback = data.isFallback
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }
}

data class MarketMoversUiState(
    val range: MarketMoverRange = MarketMoverRange.ONE_DAY,
    val direction: MarketMoverDirection = MarketMoverDirection.INCREASERS,
    val items: List<MarketMoverItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: LocalDateTime? = null,
    val isStale: Boolean = false,
    val isFallback: Boolean = false
)
