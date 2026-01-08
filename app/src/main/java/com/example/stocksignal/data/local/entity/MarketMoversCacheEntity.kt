package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import com.example.stocksignal.data.local.model.MarketMoverItem
import java.time.LocalDateTime

@Entity(
    tableName = "market_movers_cache",
    primaryKeys = ["range", "direction"]
)
data class MarketMoversCacheEntity(
    val range: String,
    val direction: String,
    val fetchedAt: LocalDateTime,
    val items: List<MarketMoverItem>
)
