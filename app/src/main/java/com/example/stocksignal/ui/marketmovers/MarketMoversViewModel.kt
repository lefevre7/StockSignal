package com.example.stocksignal.ui.marketmovers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.data.stooq.repository.StooqRepository
import com.example.stocksignal.domain.model.ChartRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class MarketMoversViewModel @Inject constructor(
    private val repository: MarketMoversRepository,
    private val watchlistRepository: WatchlistRepository,
    private val stockRepository: StockRepository,
    private val stooqRepository: StooqRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketMoversUiState())
    val uiState: StateFlow<MarketMoversUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "MarketMoversViewModel"
    }

    init {
        observeWatchlist()
        loadMarketMovers()
    }

    private fun fetchStockDataForMovers(
        items: List<MarketMoverItem>,
        forceRefresh: Boolean,
        isStale: Boolean
    ) {
        viewModelScope.launch {
            items.forEach { item ->
                val hasCachedSeries = item.series.isNotEmpty()
                val hasCachedExchange = item.exchange != null
                if (!forceRefresh && !isStale && hasCachedSeries && hasCachedExchange) {
                    return@forEach
                }

                try {
                    // Random delay between 1-3 seconds
                    val delayMs = Random.nextLong(1000, 3001)
                    delay(delayMs)
                    
                    Log.d(TAG, "Fetching stock data for ${item.ticker}")
                    
                    // Fetch enriched intraday data with exchange
                    when (val enrichedResult = stooqRepository.getEnrichedIntradayData(
                        ticker = item.ticker,
                        intervalMinutes = 10
                    )) {
                        is Result.Success -> {
                            val enrichedData = enrichedResult.data
                            val intradayData = enrichedData.data
                            val exchange = enrichedData.exchange
                            
                            // Convert to PriceCandle list
                            val series = intradayData.entries
                                .sortedBy { (time, _) -> time }
                                .map { (time, stockData) ->
                                    com.example.stocksignal.domain.model.PriceCandle(
                                        time = time,
                                        open = stockData.open,
                                        high = stockData.high,
                                        low = stockData.low,
                                        close = stockData.close,
                                        volume = stockData.volume
                                    )
                                }
                            
                            // Update the item with series and exchange (if missing)
                            _uiState.update { state ->
                                val updatedItems = state.items.map { existingItem ->
                                    if (existingItem.ticker == item.ticker) {
                                        existingItem.copy(
                                            series = series,
                                            exchange = exchange ?: existingItem.exchange
                                        )
                                    } else {
                                        existingItem
                                    }
                                }
                                state.copy(items = updatedItems)
                            }
                            Log.d(TAG, "Updated ${item.ticker} with ${series.size} candles, exchange=$exchange")
                        }
                        is Result.Error -> {
                            Log.w(TAG, "Failed to fetch stock data for ${item.ticker}: ${enrichedResult.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching stock data for ${item.ticker}", e)
                }
            }
            Log.d(TAG, "Finished fetching stock data for all market movers")            
            // Persist enriched data to cache
            val currentState = _uiState.value
            repository.updateItemsInCache(
                range = MarketMoverRange.ONE_DAY,
                direction = currentState.direction,
                items = currentState.items
            )
        }
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
            if (!forceRefresh) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                when (val cached = repository.getFreshCachedMovers(MarketMoverRange.ONE_DAY, direction)) {
                    is Result.Success -> {
                        val data = cached.data
                        applyMarketMoversResult(data, isLoading = false)
                        fetchStockDataForMovers(
                            items = data.items,
                            forceRefresh = false,
                            isStale = data.isStale
                        )
                        // Refresh in background without blocking the UI.
                        viewModelScope.launch {
                            fetchAndApplyMarketMovers(direction, showLoading = false)
                        }
                        return@launch
                    }
                    is Result.Error -> {
                        // No fresh cache; fall through to live fetch.
                    }
                }
            }
            fetchAndApplyMarketMovers(direction, showLoading = true)
        }
    }

    private suspend fun fetchAndApplyMarketMovers(
        direction: MarketMoverDirection,
        showLoading: Boolean
    ) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }
        when (val result = repository.getMarketMovers(MarketMoverRange.ONE_DAY, direction, true)) {
            is Result.Success -> {
                val data = result.data
                applyMarketMoversResult(data, isLoading = false)
                fetchStockDataForMovers(
                    items = data.items,
                    forceRefresh = true,
                    isStale = data.isStale
                )
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

    private fun applyMarketMoversResult(
        data: com.example.stocksignal.data.local.model.MarketMoversSnapshot,
        isLoading: Boolean
    ) {
        _uiState.update {
            it.copy(
                items = data.items,
                isLoading = isLoading,
                errorMessage = null,
                lastUpdated = data.fetchedAt,
                isStale = data.isStale,
                isFallback = data.isFallback
            )
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
