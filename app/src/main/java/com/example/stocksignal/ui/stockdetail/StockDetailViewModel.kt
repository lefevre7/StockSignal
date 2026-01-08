package com.example.stocksignal.ui.stockdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.TechnicalIndicators
import com.example.stocksignal.domain.signal.IndicatorCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class StockDetailViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val signalsRepository: SignalsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(StockDetailUiState())
    val uiState: StateFlow<StockDetailUiState> = _uiState.asStateFlow()

    private var watchlistSnapshot: List<WatchlistItemEntity> = emptyList()
    private var historyJob: Job? = null

    init {
        observeWatchlist()
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            _uiState.update { it.copy(range = settings.selectedChartRange) }
            val tickerArg = savedStateHandle.get<String>(ARG_TICKER).orEmpty()
            val eventIdArg = savedStateHandle.get<String>(ARG_EVENT_ID)
            _uiState.update { it.copy(highlightEventId = eventIdArg) }
            if (tickerArg.isNotBlank()) {
                setTicker(tickerArg)
            }
        }
    }

    fun setTicker(ticker: String) {
        if (ticker.isBlank() || ticker == _uiState.value.ticker) return
        _uiState.update {
            it.copy(
                ticker = ticker,
                companyName = null,
                exchange = null,
                errorMessage = null
            )
        }
        updateWatchlistMetadata()
        startHistoryObserver(ticker)
        loadSeries()
    }

    fun selectRange(range: ChartRange) {
        if (range == _uiState.value.range) return
        _uiState.update { it.copy(range = range) }
        viewModelScope.launch {
            settingsRepository.setSelectedChartRange(range)
        }
        loadSeries()
    }

    fun refresh() {
        loadSeries(forceRefresh = true)
    }

    fun toggleWatchlist() {
        val state = _uiState.value
        if (state.ticker.isBlank()) return
        viewModelScope.launch {
            val existing = watchlistRepository.getBySymbol(state.ticker)
            if (existing != null) {
                watchlistRepository.deleteBySymbol(state.ticker)
            } else {
                val maxSort = watchlistSnapshot.mapNotNull { it.sortOrder }.maxOrNull()
                val nextOrder = (maxSort ?: (watchlistSnapshot.size - 1)).coerceAtLeast(-1) + 1
                val entity = WatchlistItemEntity(
                    symbol = state.ticker,
                    companyName = state.companyName ?: state.ticker,
                    exchange = state.exchange,
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
                    lastNotifiedAt = null
                )
                watchlistRepository.upsert(entity)
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            watchlistRepository.watchlistFlow.collectLatest { items ->
                watchlistSnapshot = items
                updateWatchlistMetadata()
            }
        }
    }

    private fun updateWatchlistMetadata() {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val entry = watchlistSnapshot.firstOrNull { it.symbol == ticker }
        _uiState.update {
            it.copy(
                companyName = entry?.companyName ?: it.companyName,
                exchange = entry?.exchange ?: it.exchange,
                inWatchlist = entry != null,
                alertEnabled = entry?.alertEnabled ?: it.alertEnabled
            )
        }
    }

    private fun startHistoryObserver(ticker: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            signalsRepository.eventsForTicker(ticker).collectLatest { events ->
                _uiState.update { it.copy(history = events) }
            }
        }
    }

    private fun loadSeries(forceRefresh: Boolean = false) {
        val ticker = _uiState.value.ticker
        if (ticker.isBlank()) return
        val range = _uiState.value.range
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = stockRepository.getSeries(ticker, range, forceRefresh, eventType = null)) {
                is Result.Success -> handleSeriesSuccess(result.data, range)
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                            series = emptyList(),
                            signal = null,
                            indicators = null
                        )
                    }
                }
            }
        }
    }

    private fun handleSeriesSuccess(series: List<PriceCandle>, range: ChartRange) {
        if (series.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "No data available.",
                    series = emptyList(),
                    signal = null,
                    indicators = null
                )
            }
            return
        }

        val signal = signalsRepository.computeSignal(series, range)
        val indicators = computeIndicators(series)
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = null,
                series = series,
                signal = signal,
                indicators = indicators
            )
        }
    }

    private fun computeIndicators(series: List<PriceCandle>): TechnicalIndicators {
        val closes = series.map { it.close }
        val macd = IndicatorCalculator.macd(closes)
        return TechnicalIndicators(
            rsi14 = IndicatorCalculator.rsi(closes, 14),
            macd = macd?.macd,
            macdSignal = macd?.signal,
            macdHistogram = macd?.histogram,
            sma5 = IndicatorCalculator.sma(closes, 5),
            sma20 = IndicatorCalculator.sma(closes, 20),
            sma50 = IndicatorCalculator.sma(closes, 50),
            sma200 = IndicatorCalculator.sma(closes, 200),
            atr14 = IndicatorCalculator.atr(series, 14)
        )
    }

    companion object {
        const val ARG_TICKER = "ticker"
        const val ARG_EVENT_ID = "eventId"
    }
}

data class StockDetailUiState(
    val ticker: String = "",
    val companyName: String? = null,
    val exchange: String? = null,
    val inWatchlist: Boolean = false,
    val alertEnabled: Boolean = false,
    val range: ChartRange = ChartRange.ONE_DAY,
    val series: List<PriceCandle> = emptyList(),
    val signal: SignalResult? = null,
    val indicators: TechnicalIndicators? = null,
    val history: List<NotificationEvent> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val highlightEventId: String? = null
)
