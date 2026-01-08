package com.example.stocksignal.data.local.model

import java.time.LocalDateTime

data class MarketMoversSnapshot(
    val items: List<MarketMoverItem>,
    val fetchedAt: LocalDateTime,
    val isStale: Boolean,
    val isFallback: Boolean
)
