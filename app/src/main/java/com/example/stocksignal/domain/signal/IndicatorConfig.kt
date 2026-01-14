package com.example.stocksignal.domain.signal

import com.example.stocksignal.data.settings.HoldingPeriod

/**
 * Configuration for technical indicators based on the user's holding period.
 * 
 * Different trading timeframes require different indicator parameters:
 * - HOURS (day trading): Short-period indicators for intraday momentum
 * - DAYS (swing trading): Medium-short indicators for multi-day trends
 * - WEEKS (short-term): Balanced indicators for weekly patterns
 * - MONTHS (medium-term): Standard indicators for monthly trends
 * - YEARS (long-term): Long-period indicators for long-term trends
 */
data class IndicatorConfig(
    val holdingPeriod: HoldingPeriod,
    
    // Simple Moving Average periods
    val smaShortPeriod: Int,
    val smaLongPeriod: Int,
    
    // MACD parameters (fast, slow, signal)
    val macdFast: Int,
    val macdSlow: Int,
    val macdSignal: Int,
    
    // RSI period
    val rsiPeriod: Int,
    
    // Bollinger Bands
    val bbPeriod: Int,
    val bbStdDev: Double,
    
    // Volume analysis
    val volumeZscoreWindow: Int,
    val volumeZscoreThreshold: Double,
    
    // ATR (Average True Range) for volatility
    val atrPeriod: Int,
    
    // Breakout detection
    val breakoutWindow: Int,
    
    // Rolling return z-score
    val returnsWindow: Int
) {
    companion object {
        /**
         * Get indicator configuration optimized for the specified holding period.
         */
        fun forHoldingPeriod(period: HoldingPeriod): IndicatorConfig {
            return when (period) {
                HoldingPeriod.HOURS -> IndicatorConfig(
                    holdingPeriod = period,
                    smaShortPeriod = 5,      // Very short SMA for intraday
                    smaLongPeriod = 10,
                    macdFast = 5,             // Faster MACD for intraday
                    macdSlow = 13,
                    macdSignal = 5,
                    rsiPeriod = 9,            // Shorter RSI
                    bbPeriod = 10,
                    bbStdDev = 2.0,
                    volumeZscoreWindow = 10,  // Smaller volume window
                    volumeZscoreThreshold = 2.0,
                    atrPeriod = 7,
                    breakoutWindow = 10,
                    returnsWindow = 10
                )
                
                HoldingPeriod.DAYS -> IndicatorConfig(
                    holdingPeriod = period,
                    smaShortPeriod = 5,       // For intraday 1D/5D
                    smaLongPeriod = 20,
                    macdFast = 8,             // Slightly faster than standard
                    macdSlow = 17,
                    macdSignal = 7,
                    rsiPeriod = 11,
                    bbPeriod = 15,
                    bbStdDev = 2.0,
                    volumeZscoreWindow = 15,
                    volumeZscoreThreshold = 2.0,
                    atrPeriod = 10,
                    breakoutWindow = 15,
                    returnsWindow = 15
                )
                
                HoldingPeriod.WEEKS -> IndicatorConfig(
                    holdingPeriod = period,
                    smaShortPeriod = 20,      // Daily data focus
                    smaLongPeriod = 50,
                    macdFast = 12,            // Standard MACD
                    macdSlow = 26,
                    macdSignal = 9,
                    rsiPeriod = 14,           // Standard RSI
                    bbPeriod = 20,            // Standard BB
                    bbStdDev = 2.0,
                    volumeZscoreWindow = 20,
                    volumeZscoreThreshold = 2.0,
                    atrPeriod = 14,
                    breakoutWindow = 20,
                    returnsWindow = 20
                )
                
                HoldingPeriod.MONTHS -> IndicatorConfig(
                    holdingPeriod = period,
                    smaShortPeriod = 50,      // Medium-term SMAs
                    smaLongPeriod = 100,
                    macdFast = 12,
                    macdSlow = 26,
                    macdSignal = 9,
                    rsiPeriod = 14,
                    bbPeriod = 20,
                    bbStdDev = 2.0,
                    volumeZscoreWindow = 30,  // Longer volume window
                    volumeZscoreThreshold = 2.0,
                    atrPeriod = 14,
                    breakoutWindow = 30,
                    returnsWindow = 30
                )
                
                HoldingPeriod.YEARS -> IndicatorConfig(
                    holdingPeriod = period,
                    smaShortPeriod = 50,      // Long-term SMAs
                    smaLongPeriod = 200,
                    macdFast = 12,
                    macdSlow = 26,
                    macdSignal = 9,
                    rsiPeriod = 14,
                    bbPeriod = 20,
                    bbStdDev = 2.5,           // Wider bands for long-term
                    volumeZscoreWindow = 50,  // Longer volume analysis
                    volumeZscoreThreshold = 2.0,
                    atrPeriod = 20,           // Longer ATR period
                    breakoutWindow = 50,
                    returnsWindow = 50
                )
            }
        }
        
        /**
         * Determine if intraday data should be used based on holding period.
         * HOURS and DAYS use intraday, others use daily data.
         */
        fun useIntradayData(period: HoldingPeriod): Boolean {
            return period == HoldingPeriod.HOURS || period == HoldingPeriod.DAYS
        }
    }
}
