package com.example.stocksignal.data.local.model

import com.example.stocksignal.domain.model.PriceCandle

data class MarketMoverItem(
    val ticker: String,
    val companyName: String,
    val exchange: String?,
    val price: Double?,
    val percentChange: Double?,
    val rank: Int?,
    val signalScore: Int?,
    val signalLabel: String?,
    val series: List<PriceCandle> = emptyList()
)
