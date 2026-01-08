package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class WatchlistItem(
    val symbol: String,
    val companyName: String,
    val exchange: String?,
    val addedAt: LocalDateTime,
    val alertSettings: AlertSettings,
    val lastSignal: SignalSnapshot?,
    val notes: String?,
    val tags: List<String>,
    val sortOrder: Int?,
    val lastNotifiedAt: LocalDateTime?,
    val notificationActive: Boolean
)
