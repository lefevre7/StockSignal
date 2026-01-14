package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Entity for storing historical intraday (10-minute) price data.
 * Accumulates up to 1 year of data per symbol for improved signal accuracy.
 * Data is chunked by date for efficient storage and retrieval.
 *
 * Historical data (older than today) is immutable. Only today's data refreshes with TTL.
 */
@Entity(
    tableName = "intraday_data_cache",
    primaryKeys = ["symbol", "date"]
)
data class IntradayDataCacheEntity(
    /** Stock ticker symbol (e.g., "AAPL.US") */
    val symbol: String,
    
    /** Date of the intraday data (used for chunking) */
    val date: LocalDate,
    
    /** Timestamp when this chunk was first created */
    val createdAt: LocalDateTime,
    
    /** Timestamp of last update (for today's data only) */
    val updatedAt: LocalDateTime,
    
    /** JSON array of intraday candles for this date 
     *  Format: [{"time":"2024-01-01T09:30:00","open":150.0,"high":151.0,"low":149.5,"close":150.5,"volume":1000000},...]
     */
    val candlesJson: String
)
