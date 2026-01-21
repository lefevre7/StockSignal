package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class StockNewsItem(
    val title: String,
    val publishedAtText: String,
    val publishedAt: LocalDateTime? = null,
    val source: String? = null,
    val url: String? = null,
    val translatedTitle: String? = null,
    val translatedPublishedAtText: String? = null
)
