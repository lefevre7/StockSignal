package com.example.stocksignal.data.stooq.model

import java.time.LocalDate

/**
 * Represents stock market data for a single ticker on a specific date.
 *
 * @property date The date of the stock data
 * @property open Opening price
 * @property high Highest price during the day
 * @property low Lowest price during the day
 * @property close Closing price
 * @property volume Trading volume
 */
data class StockData(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

/**
 * Type alias for the structured stock data result.
 * Outer map key: ticker symbol
 * Inner map key: date
 * Inner map value: StockData for that ticker on that date
 */
typealias StockDataMap = Map<String, Map<LocalDate, StockData>>
