package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class PriceCandle(
    val time: LocalDateTime,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
