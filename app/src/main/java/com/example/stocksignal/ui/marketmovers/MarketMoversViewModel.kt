package com.example.stocksignal.ui.marketmovers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class MarketMoversViewModel @Inject constructor(
    private val repository: MarketMoversRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketMoversUiState())
    val uiState: StateFlow<MarketMoversUiState> = _uiState.asStateFlow()

    init {
        observeWatchlist()
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
        val direction = _uiState.value.direction
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getMarketMovers(MarketMoverRange.ONE_DAY, direction, forceRefresh)) {
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

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistRepository.watchlistFlow.collectLatest { items ->
                val symbols = items.map { it.symbol }.toSet()
                _uiState.update { it.copy(watchlistSymbols = symbols) }
            }
        }
    }

    fun addToWatchlist(item: MarketMoverItem) {
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(item.ticker)
            if (existing != null) return@launch

            val items = watchlistRepository.getAll()
            val maxSort = items.mapNotNull { it.sortOrder }.maxOrNull()
            val nextOrder = (maxSort ?: (items.size - 1)).coerceAtLeast(-1) + 1

            val entity = WatchlistItemEntity(
                symbol = item.ticker,
                companyName = item.companyName,
                exchange = item.exchange,
                addedAt = LocalDateTime.now(),
                alertEnabled = true,
                minScoreForNotify = 60,
                quietHoursStart = null,
                quietHoursEnd = null,
                snoozedUntil = null,
                lastSignalScore = null,
                lastSignalLabel = null,
                lastSignalConfidence = null,
                lastSignalTime = null,
                notes = null,
                sortOrder = nextOrder,
                tags = emptyList(),
                muteMarketMovers = false,
                lastNotifiedAt = null,
                indicatorAlertsJson = null
            )
            watchlistRepository.upsert(entity)
        }
    }
}

data class MarketMoversUiState(
    val direction: MarketMoverDirection = MarketMoverDirection.MOST_ACTIVE,
    val items: List<MarketMoverItem> = emptyList(),
    val watchlistSymbols: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: LocalDateTime? = null,
    val isStale: Boolean = false,
    val isFallback: Boolean = false
)
