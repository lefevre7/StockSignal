package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "watchlist_items")
data class WatchlistItemEntity(
    @PrimaryKey val symbol: String,
    val companyName: String,
    val exchange: String?,
    val addedAt: LocalDateTime,
    val alertEnabled: Boolean,
    val minScoreForNotify: Int?,
    val quietHoursStart: String?,
    val quietHoursEnd: String?,
    val snoozedUntil: LocalDateTime?,
    val lastSignalScore: Int?,
    val lastSignalLabel: String?,
    val lastSignalConfidence: Int?,
    val lastSignalTime: LocalDateTime?,
    val notes: String?,
    val sortOrder: Int?,
    val tags: List<String> = emptyList(),
    val muteMarketMovers: Boolean,
    val lastNotifiedAt: LocalDateTime?
)
