package com.example.stocksignal.data.local.model

data class MarketMoverItem(
    val ticker: String,
    val companyName: String,
    val exchange: String?,
    val price: Double?,
    val percentChange: Double?,
    val rank: Int?,
    val signalScore: Int?,
    val signalLabel: String?
)
