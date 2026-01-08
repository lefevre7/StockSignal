package com.example.stocksignal.domain.model

data class TechnicalIndicators(
    val rsi14: Double?,
    val macd: Double?,
    val macdSignal: Double?,
    val macdHistogram: Double?,
    val sma5: Double?,
    val sma20: Double?,
    val sma50: Double?,
    val sma200: Double?,
    val atr14: Double?
)
