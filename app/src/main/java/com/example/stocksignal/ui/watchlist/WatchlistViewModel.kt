package com.example.stocksignal.ui.watchlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.repository.StockRepository
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.network.StooqRequestBlocker
import com.example.stocksignal.domain.model.AlertSettings
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.SignalSnapshot
import com.example.stocksignal.domain.model.WatchlistItem
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import com.example.stocksignal.ui.model.AiGenerationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val stockRepository: StockRepository,
    private val signalsRepository: SignalsRepository,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsRepository: NotificationDiagnosticsRepository,
    private val stooqRequestBlocker: StooqRequestBlocker
) : ViewModel() {

    private val marketData = MutableStateFlow<Map<String, WatchlistMarketData>>(emptyMap())
    private val _errorMessage = MutableStateFlow<String?>(null)

    val stooqBlockedMessage: StateFlow<String?> = diagnosticsRepository.stooqBlockedFlow()
        .map { info ->
            val message = info.message?.takeIf { it.isNotBlank() }
            val until = info.blockedUntilMillis
            if (message == null) return@map null
            if (until != null && until <= System.currentTimeMillis()) return@map null
            message
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val watchlistCards: StateFlow<List<WatchlistCardState>> = combine(
        watchlistRepository.watchlistFlow,
        marketData
    ) { entities, data ->
        entities.map { entity ->
            val item = entity.toDomain()
            val market = data[item.symbol]
            WatchlistCardState(
                item = item,
                series = market?.series.orEmpty(),
                signal = market?.signal,
                price = market?.price,
                percentChange = market?.percentChange,
                updatedAt = market?.updatedAt
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        viewModelScope.launch {
            watchlistRepository.watchlistFlow.collect { items ->
                try {
                    refreshMarketData(items)
                    _errorMessage.value = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error refreshing market data", e)
                    _errorMessage.value = "Failed to refresh market data: ${e.message}"
                }
            }
        }
    }

    fun persistCustomOrder(items: List<WatchlistItem>) {
        viewModelScope.launch {
            try {
                watchlistRepository.updateSortOrder(items.map { it.symbol })
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting custom order", e)
                _errorMessage.value = "Failed to save order: ${e.message}"
            }
        }
    }

    fun remove(symbol: String) {
        viewModelScope.launch {
            try {
                watchlistRepository.deleteBySymbol(symbol)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing symbol: $symbol", e)
                _errorMessage.value = "Failed to remove $symbol: ${e.message}"
            }
        }
    }

    fun snooze(symbol: String) {
        viewModelScope.launch {
            try {
                val item = watchlistRepository.getBySymbol(symbol) ?: return@launch
                val settings = settingsRepository.settingsFlow.first()
                val duration = Duration.ofMinutes(settings.snoozeDuration.minutes)
                val until = LocalDateTime.now().plus(duration)
                watchlistRepository.upsert(item.copy(snoozedUntil = until))
            } catch (e: Exception) {
                Log.e(TAG, "Error snoozing symbol: $symbol", e)
                _errorMessage.value = "Failed to snooze $symbol: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearStooqBlock() {
        viewModelScope.launch {
            stooqRequestBlocker.clearBlock()
            diagnosticsRepository.clearStooqBlocked()
        }
    }

    private suspend fun refreshMarketData(items: List<WatchlistItemEntity>) = coroutineScope {
        val symbols = items.map { it.symbol }.toSet()
        for (symbol in symbols) {
            fetchMarketData(symbol)
        }
        marketData.update { current ->
            current.filterKeys { it in symbols }
        }
    }

    private suspend fun fetchMarketData(symbol: String) {
        try {
            val existing = marketData.value[symbol]
            var usedCached = false
            if (existing == null) {
                when (val cached = stockRepository.getFreshCachedSeries(symbol, ChartRange.ONE_DAY)) {
                    is Result.Success -> {
                        updateMarketData(symbol, cached.data, emptyList(), ChartRange.ONE_DAY)
                        usedCached = true
                    }
                    is Result.Error -> {
                        // No fresh cache available; fall through to live fetch.
                    }
                }
            }
            if (existing != null && !isStale(existing) && !usedCached) return

            when (val result = stockRepository.getSeries(symbol, ChartRange.ONE_DAY, forceRefresh = true, eventType = null)) {
                is Result.Success -> {
                    // If intraday data doesn't have enough candles for signal computation (need 20+),
                    // fall back to daily data which will have more history
                    if (result.data.size < MIN_CANDLES_FOR_SIGNAL) {
                        Log.d(TAG, "Insufficient intraday candles for $symbol (${result.data.size}), falling back to daily")
                        when (val fallback = stockRepository.getDailySeriesFallback(symbol, ChartRange.SIX_MONTH)) {
                            is Result.Success -> updateMarketData(symbol, result.data, fallback.data, ChartRange.ONE_DAY)
                            is Result.Error -> {
                                Log.w(TAG, "Daily fallback failed for $symbol, using limited intraday data")
                                updateMarketData(symbol, result.data, emptyList(), ChartRange.ONE_DAY)
                            }
                        }
                    } else {
                        updateMarketData(symbol, result.data, emptyList(), ChartRange.ONE_DAY)
                    }
                }
                is Result.Error -> {
                    Log.w(TAG, "Error fetching market data for $symbol: ${result.message}")
                    when (val fallback = stockRepository.getDailySeriesFallback(symbol, ChartRange.SIX_MONTH)) {
                        is Result.Success -> updateMarketData(symbol, emptyList(), fallback.data, ChartRange.ONE_DAY)
                        is Result.Error -> {
                            Log.e(TAG, "Fallback also failed for $symbol", fallback.exception)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching market data for $symbol", e)
        }
    }

    private suspend fun updateMarketData(
        symbol: String, 
        intradaySeries: List<PriceCandle>, 
        dailySeries: List<PriceCandle>,
        range: ChartRange
    ) {
        // Use intraday for display (price, chart) if available, otherwise daily
        val displaySeries = intradaySeries.ifEmpty { dailySeries }
        if (displaySeries.isEmpty()) return
        
        // Use daily series for signal computation if we have it and intraday is insufficient
        val signalSeries = if (dailySeries.isNotEmpty() && intradaySeries.size < MIN_CANDLES_FOR_SIGNAL) {
            dailySeries
        } else {
            intradaySeries.ifEmpty { dailySeries }
        }
        
        Log.d(TAG, "$symbol: display=${displaySeries.size}, signal=${signalSeries.size} candles")
        val overview = when (val overviewResult = stockRepository.getStockOverview(symbol)) {
            is Result.Success -> overviewResult.data
            is Result.Error -> null
        }
        // Use skipAiGeneration=true to avoid blocking on LLM generation
        // Watchlist will use cached AI scores or rule-based fallback for fast loading
        val signal = signalsRepository.computeSignal(symbol, signalSeries, range, overview, skipAiGeneration = false)
        
        val price = displaySeries.lastOrNull()?.close
        val prev = displaySeries.getOrNull(displaySeries.lastIndex - 1)
        val percentChange = when {
            price != null && prev != null && prev.close != 0.0 ->
                ((price - prev.close) / prev.close) * 100.0
            price != null && displaySeries.lastOrNull()?.open != null && displaySeries.last().open != 0.0 ->
                ((price - displaySeries.last().open) / displaySeries.last().open) * 100.0
            else -> null
        }
        val updated = WatchlistMarketData(
            series = displaySeries,
            signal = signal,
            price = price,
            percentChange = percentChange,
            updatedAt = LocalDateTime.now()
        )
        marketData.update { current -> current + (symbol to updated) }
    }

    private fun isStale(data: WatchlistMarketData): Boolean {
        return Duration.between(data.updatedAt, LocalDateTime.now()) > Duration.ofMinutes(10)
    }

    companion object {
        private const val TAG = "WatchlistViewModel"
        private const val MIN_CANDLES_FOR_SIGNAL = 20
    }
}

data class WatchlistCardState(
    val item: WatchlistItem,
    val series: List<PriceCandle>,
    val signal: SignalResult?,
    val price: Double?,
    val percentChange: Double?,
    val updatedAt: LocalDateTime?,
    val aiGenerationState: AiGenerationState = AiGenerationState.IDLE
)

private data class WatchlistMarketData(
    val series: List<PriceCandle>,
    val signal: SignalResult?,
    val price: Double?,
    val percentChange: Double?,
    val updatedAt: LocalDateTime
)

private fun WatchlistItemEntity.toDomain(): WatchlistItem {
    val alert = AlertSettings(
        enabled = alertEnabled,
        minScoreForNotify = minScoreForNotify ?: 60,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        snoozedUntil = snoozedUntil,
        alwaysNotify = false,
        ignoreMarketMovers = muteMarketMovers
    )
    val snapshot = if (lastSignalScore != null && lastSignalTime != null) {
        SignalSnapshot(
            score = lastSignalScore,
            averageScore = null,
            modeScore = null,
            confidence = lastSignalConfidence ?: 0,
            generatedAt = lastSignalTime
        )
    } else {
        null
    }
    val indicatorAlerts = IndicatorAlertJson.fromJson(indicatorAlertsJson)
    return WatchlistItem(
        symbol = symbol,
        companyName = companyName,
        exchange = exchange,
        addedAt = addedAt,
        alertSettings = alert,
        lastSignal = snapshot,
        notes = notes,
        tags = tags,
        sortOrder = sortOrder,
        lastNotifiedAt = lastNotifiedAt,
        notificationActive = false,
        indicatorAlerts = indicatorAlerts
    )
}
