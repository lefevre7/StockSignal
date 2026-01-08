package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import java.time.LocalDateTime

@Entity(
    tableName = "stock_detail_cache",
    primaryKeys = ["symbol", "range"]
)
data class StockDetailCacheEntity(
    val symbol: String,
    val range: String,
    val fetchedAt: LocalDateTime,
    val seriesJson: String,
    val latestPrice: Double?,
    val indicatorsJson: String?,
    val signalHistoryJson: String?
)
