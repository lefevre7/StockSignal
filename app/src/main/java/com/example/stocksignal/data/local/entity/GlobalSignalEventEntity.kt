package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "signal_events")
data class GlobalSignalEventEntity(
    @PrimaryKey val id: String,
    val type: String,
    val ticker: String,
    val score: Int,
    val label: String,
    val confidence: Int,
    val percentChange: Double?,
    val price: Double?,
    val generatedAt: LocalDateTime,
    val notifiedAt: LocalDateTime?,
    val source: String,
    val delivered: Boolean,
    val deepLink: String?,
    val reasons: List<String> = emptyList(),
    val avgScore: Int?,
    val modeScore: Int?,
    val modelScores: Map<String, Int>?
)
