package com.example.stocksignal.domain.model

import java.time.Instant

data class StockNewsItem(
    val title: String,
    val publishedAtText: String,
    val publishedAt: Instant? = null,
    val source: String? = null,
    val url: String? = null,
    val translatedTitle: String? = null,
    val translatedPublishedAtText: String? = null
)
