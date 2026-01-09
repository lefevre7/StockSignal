package com.example.stocksignal.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SearchRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.domain.model.RecentSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val watchlistRepository: WatchlistRepository,
    private val marketMoversRepository: MarketMoversRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var watchlistSnapshot: List<WatchlistItemEntity> = emptyList()
    private var searchJob: Job? = null

    init {
        observeRecentSearches()
        observeWatchlist()
        observeQuery()
        loadTopMovers()
    }

    fun updateQuery(query: String) {
        queryFlow.value = query
        _uiState.update { it.copy(query = query, errorMessage = null) }
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
        }
    }

    fun clearQuery() {
        updateQuery("")
    }

    fun selectQuickFilter(direction: MarketMoverDirection) {
        _uiState.update { it.copy(quickFilter = direction) }
    }

    fun addToWatchlist(result: SearchResult, alertsEnabled: Boolean) {
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(result.symbol)
            if (existing != null) return@launch

            val maxSort = watchlistSnapshot.mapNotNull { it.sortOrder }.maxOrNull()
            val nextOrder = (maxSort ?: (watchlistSnapshot.size - 1)).coerceAtLeast(-1) + 1

            val entity = WatchlistItemEntity(
                symbol = result.symbol,
                companyName = result.companyName,
                exchange = result.exchange,
                addedAt = LocalDateTime.now(),
                alertEnabled = alertsEnabled,
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

    fun setAlertEnabled(symbol: String, enabled: Boolean) {
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(symbol) ?: return@launch
            watchlistRepository.upsert(existing.copy(alertEnabled = enabled))
        }
    }

    fun selectRecentSearch(query: String) {
        updateQuery(query)
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchRepository.clearHistory()
        }
    }

    private fun observeRecentSearches() {
        viewModelScope.launch {
            searchRepository.recentSearches.collectLatest { history ->
                _uiState.update { it.copy(recentSearches = history) }
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistRepository.watchlistFlow.collectLatest { items ->
                watchlistSnapshot = items
                val watchlistMap = items.associate {
                    it.symbol to WatchlistSummary(symbol = it.symbol, alertEnabled = it.alertEnabled)
                }
                _uiState.update { it.copy(watchlist = watchlistMap) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            queryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) return@collectLatest
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    when (val result = searchRepository.search(query)) {
                        is Result.Success -> {
                            _uiState.update {
                                it.copy(
                                    results = result.data,
                                    isLoading = false,
                                    errorMessage = null
                                )
                            }
                        }
                        is Result.Error -> {
                            _uiState.update {
                                it.copy(
                                    results = emptyList(),
                                    isLoading = false,
                                    errorMessage = result.message
                                )
                            }
                        }
                    }
                }
        }
    }

    private fun loadTopMovers() {
        viewModelScope.launch {
            val mostActive = when (val result = marketMoversRepository.getMarketMovers(
                range = MarketMoverRange.ONE_DAY,
                direction = MarketMoverDirection.MOST_ACTIVE,
                forceRefresh = false
            )) {
                is Result.Success -> result.data.items
                is Result.Error -> emptyList()
            }
            val increasers = when (val result = marketMoversRepository.getMarketMovers(
                range = MarketMoverRange.ONE_DAY,
                direction = MarketMoverDirection.INCREASERS,
                forceRefresh = false
            )) {
                is Result.Success -> result.data.items
                is Result.Error -> emptyList()
            }
            val decreasers = when (val result = marketMoversRepository.getMarketMovers(
                range = MarketMoverRange.ONE_DAY,
                direction = MarketMoverDirection.DECREASERS,
                forceRefresh = false
            )) {
                is Result.Success -> result.data.items
                is Result.Error -> emptyList()
            }
            val moverSymbols = (mostActive + increasers + decreasers).map { it.ticker }.toSet()
            _uiState.update {
                it.copy(
                    topMostActive = mostActive,
                    topIncreasers = increasers,
                    topDecreasers = decreasers,
                    moverSymbols = moverSymbols
                )
            }
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val topMostActive: List<MarketMoverItem> = emptyList(),
    val topIncreasers: List<MarketMoverItem> = emptyList(),
    val topDecreasers: List<MarketMoverItem> = emptyList(),
    val moverSymbols: Set<String> = emptySet(),
    val watchlist: Map<String, WatchlistSummary> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val quickFilter: MarketMoverDirection = MarketMoverDirection.MOST_ACTIVE
)

data class WatchlistSummary(
    val symbol: String,
    val alertEnabled: Boolean
)
