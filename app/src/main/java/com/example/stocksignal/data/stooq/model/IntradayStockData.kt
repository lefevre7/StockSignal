package com.example.stocksignal.data.stooq.model

import java.time.LocalDateTime

/**
 * Represents intraday stock market data for a single ticker at a specific timestamp.
 *
 * @property dateTime The timestamp of the stock data (no timezone assumptions)
 * @property open Opening price
 * @property high Highest price during the interval
 * @property low Lowest price during the interval
 * @property close Closing price
 * @property volume Trading volume (Stooq column: Vol)
 * @property openInterest Open interest (Stooq column: OI), if available
 * @property annotation Annotation (Stooq column: Annotation), if available
 */
data class IntradayStockData(
    val dateTime: LocalDateTime,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val openInterest: Long?,
    val annotation: String?
)

/**
 * Type alias for the structured intraday stock data result.
 * Outer map key: ticker symbol
 * Inner map key: timestamp (LocalDateTime)
 * Inner map value: IntradayStockData for that ticker at that timestamp
 */
typealias IntradayStockDataMap = Map<String, Map<LocalDateTime, IntradayStockData>>

/**
 * Enriched intraday response that includes market/exchange information
 * parsed from the HTML header in addition to the stock data.
 */
data class EnrichedIntradayResponse(
    val data: Map<LocalDateTime, IntradayStockData>,
    val exchange: String?
)

