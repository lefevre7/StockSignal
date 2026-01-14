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
import com.example.stocksignal.domain.model.AlertSettings
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.SignalResult
import com.example.stocksignal.domain.model.SignalSnapshot
import com.example.stocksignal.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val marketData = MutableStateFlow<Map<String, WatchlistMarketData>>(emptyMap())
    private val _errorMessage = MutableStateFlow<String?>(null)

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

    private suspend fun refreshMarketData(items: List<WatchlistItemEntity>) = coroutineScope {
        val symbols = items.map { it.symbol }.toSet()
        val tasks = symbols.map { symbol ->
            async { fetchMarketData(symbol) }
        }
        tasks.awaitAll()
        marketData.update { current ->
            current.filterKeys { it in symbols }
        }
    }

    private suspend fun fetchMarketData(symbol: String) {
        try {
            val existing = marketData.value[symbol]
            if (existing != null && !isStale(existing)) return

            when (val result = stockRepository.getSeries(symbol, ChartRange.ONE_DAY, eventType = null)) {
                is Result.Success -> updateMarketData(symbol, result.data, ChartRange.ONE_DAY)
                is Result.Error -> {
                    Log.w(TAG, "Error fetching market data for $symbol: ${result.message}")
                    when (val fallback = stockRepository.getDailySeriesFallback(symbol, ChartRange.ONE_DAY)) {
                        is Result.Success -> updateMarketData(symbol, fallback.data, ChartRange.ONE_DAY)
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

    private suspend fun updateMarketData(symbol: String, series: List<PriceCandle>, range: ChartRange) {
        if (series.isEmpty()) return
        val signal = signalsRepository.computeSignal(series, range)
        val price = series.lastOrNull()?.close
        val prev = series.getOrNull(series.lastIndex - 1)
        val percentChange = when {
            price != null && prev != null && prev.close != 0.0 ->
                ((price - prev.close) / prev.close) * 100.0
            price != null && series.lastOrNull()?.open != null && series.last().open != 0.0 ->
                ((price - series.last().open) / series.last().open) * 100.0
            else -> null
        }
        val updated = WatchlistMarketData(
            series = series,
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
    }
}

data class WatchlistCardState(
    val item: WatchlistItem,
    val series: List<PriceCandle>,
    val signal: SignalResult?,
    val price: Double?,
    val percentChange: Double?,
    val updatedAt: LocalDateTime?
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
