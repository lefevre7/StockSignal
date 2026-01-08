package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class StockDetail(
    val symbol: String,
    val companyName: String,
    val exchange: String?,
    val latestPrice: Double?,
    val percentChange: Double?,
    val lastUpdated: LocalDateTime?,
    val seriesByRange: Map<ChartRange, List<PriceCandle>>,
    val indicators: TechnicalIndicators?,
    val signal: SignalResult?,
    val signalHistory: List<SignalHistoryEntry>
)
