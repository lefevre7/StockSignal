package com.example.stocksignal.data.stooq.model

/**
 * Snapshot of premarket/after-hours bid/ask values from Stooq quote page.
 */
data class PremarketQuote(
    val ticker: String,
    val bid: Double?,
    val ask: Double?,
    val volume: Long?
)
