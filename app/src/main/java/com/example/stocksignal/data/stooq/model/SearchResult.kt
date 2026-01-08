package com.example.stocksignal.data.stooq.model

data class SearchResult(
    val symbol: String,
    val companyName: String,
    val exchange: String?
)
