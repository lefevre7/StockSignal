package com.example.stocksignal.domain.model

/**
 * Stock overview/fundamental data extracted from Stooq quote page.
 * All numeric fields are nullable - null indicates data not available.
 */
data class StockOverview(
    val symbol: String,
    val marketCap: Double? = null,
    val peRatio: Double? = null,
    val dividend: Double? = null,
    val week52High: Double? = null,
    val week52Low: Double? = null,
    val news: List<StockNewsItem> = emptyList()
)
