package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for caching stock overview/fundamental data.
 * TTL: 24 hours (fundamentals change infrequently).
 */
@Entity(tableName = "stock_overview_cache")
data class StockOverviewCacheEntity(
    @PrimaryKey val symbol: String,
    val marketCap: Double?,
    val peRatio: Double?,
    val dividend: Double?,
    val week52High: Double?,
    val week52Low: Double?,
    val fetchedAt: String // ISO-8601 LocalDateTime string
)
