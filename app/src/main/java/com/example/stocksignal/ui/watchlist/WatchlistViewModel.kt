package com.example.stocksignal.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.domain.model.AlertSettings
import com.example.stocksignal.domain.model.IndicatorAlertJson
import com.example.stocksignal.domain.model.SignalSnapshot
import com.example.stocksignal.domain.model.WatchlistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    val watchlistItems: StateFlow<List<WatchlistItem>> = watchlistRepository.watchlistFlow
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun persistCustomOrder(items: List<WatchlistItem>) {
        viewModelScope.launch {
            watchlistRepository.updateSortOrder(items.map { it.symbol })
        }
    }
}

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
