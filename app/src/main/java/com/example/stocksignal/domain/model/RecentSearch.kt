package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class RecentSearch(
    val query: String,
    val lastSearchedAt: LocalDateTime,
    val count: Int
)
