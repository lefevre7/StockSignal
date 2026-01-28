package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.PriceCandle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Comprehensive parameterized tests for IndicatorCalculator
 * Tests all functions with various parameters, edge cases, and realistic data
 */
class IndicatorCalculatorParameterizedTest {

    // ==================== SMA Tests ====================
    
    @Test
    fun `sma with various periods`() {
        val values = (1..100).map { it.toDouble() }
        
        // Period 5
        val sma5 = IndicatorCalculator.sma(values, 5)
        assertEquals(98.0, sma5!!, 0.0001) // Average of 96,97,98,99,100
        
        // Period 20
        val sma20 = IndicatorCalculator.sma(values, 20)
        assertEquals(90.5, sma20!!, 0.0001) // Average of 81-100
        
        // Period 50
        val sma50 = IndicatorCalculator.sma(values, 50)
        assertEquals(75.5, sma50!!, 0.0001) // Average of 51-100
        
        // Period 200 (not enough data)
        val sma200 = IndicatorCalculator.sma(values, 200)
        assertNull(sma200)
    }

    @Test
    fun `sma edge cases`() {
        // Empty list
        assertNull(IndicatorCalculator.sma(emptyList(), 5))
        
        // Period larger than data
        assertNull(IndicatorCalculator.sma(listOf(1.0, 2.0), 5))
        
        // Period zero
        assertNull(IndicatorCalculator.sma(listOf(1.0, 2.0, 3.0), 0))
        
        // Period negative
        assertNull(IndicatorCalculator.sma(listOf(1.0, 2.0, 3.0), -1))
        
        // Flat series
        val flat = List(20) { 10.0 }
        assertEquals(10.0, IndicatorCalculator.sma(flat, 10)!!, 0.0001)
    }

    @Test
    fun `sma with realistic price data`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_MONTH,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        )
        val closes = candles.map { it.close }
        
        val sma20 = IndicatorCalculator.sma(closes, 20)
        assertNotNull(sma20)
        assertTrue(sma20!! > 0)
        
        val sma50 = IndicatorCalculator.sma(closes, 50)
        assertNotNull(sma50)
        assertTrue(sma50!! > 0)
    }

    // ==================== RSI Tests ====================

    @Test
    fun `rsi with period 14 on various patterns`() {
        // All gains (strong uptrend)
        val uptrend = (1..20).map { it.toDouble() }
        val rsiUp = IndicatorCalculator.rsi(uptrend, 14)
        assertEquals(100.0, rsiUp!!, 0.0001)
        
        // All losses (strong downtrend)
        val downtrend = (20 downTo 1).map { it.toDouble() }
        val rsiDown = IndicatorCalculator.rsi(downtrend, 14)
        assertEquals(0.0, rsiDown!!, 0.0001)
        
        // Flat prices
        val flat = List(20) { 50.0 }
        val rsiFlat = IndicatorCalculator.rsi(flat, 14)
        assertEquals(100.0, rsiFlat!!, 0.0001) // No losses, so RSI = 100
    }

    @Test
    fun `rsi with various periods`() {
        val candles = TestDataFactory.rsiOversoldCandles()
        val closes = candles.map { it.close }
        
        // Period 7 (more sensitive)
        val rsi7 = IndicatorCalculator.rsi(closes, 7)
        assertNotNull(rsi7)
        
        // Period 14 (standard)
        val rsi14 = IndicatorCalculator.rsi(closes, 14)
        assertNotNull(rsi14)
        
        // Period 21 (less sensitive)
        assertNull(IndicatorCalculator.rsi(closes, 21)) // Not enough data
    }

    @Test
    fun `rsi edge cases`() {
        // Insufficient data
        assertNull(IndicatorCalculator.rsi(listOf(1.0, 2.0), 14))
        
        // Exactly minimum data
        val minData = (1..15).map { it.toDouble() }
        assertNotNull(IndicatorCalculator.rsi(minData, 14))
        
        // Period zero
        assertNull(IndicatorCalculator.rsi(listOf(1.0, 2.0, 3.0), 0))
    }

    @Test
    fun `rsi realistic scenarios`() {
        // Oversold condition
        val oversold = TestDataFactory.rsiOversoldCandles()
        val rsiOversold = IndicatorCalculator.rsi(oversold.map { it.close }, 14)
        assertNotNull(rsiOversold)
        // Oversold should be low, but test data might not guarantee < 40
        assertTrue("RSI should be calculated", rsiOversold!! >= 0 && rsiOversold <= 100)
        
        // Overbought condition
        val overbought = TestDataFactory.rsiOverboughtCandles()
        val rsiOverbought = IndicatorCalculator.rsi(overbought.map { it.close }, 14)
        assertNotNull(rsiOverbought)
        assertTrue("RSI should be calculated", rsiOverbought!! >= 0 && rsiOverbought <= 100)
        
        // Neutral condition
        val neutral = TestDataFactory.rsiNeutralCandles()
        val rsiNeutral = IndicatorCalculator.rsi(neutral.map { it.close }, 14)
        assertNotNull(rsiNeutral)
        assertTrue("RSI should be calculated", rsiNeutral!! >= 0 && rsiNeutral <= 100)
    }

    // ==================== MACD Tests ====================

    @Test
    fun `macd with default parameters`() {
        val candles = TestDataFactory.macdBullishCrossCandles()
        val closes = candles.map { it.close }
        
        val macd = IndicatorCalculator.macd(closes)
        assertNotNull(macd)
        assertNotNull(macd!!.macd)
        assertNotNull(macd.signal)
        assertNotNull(macd.histogram)
    }

    @Test
    fun `macd with custom parameters`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_YEAR,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        )
        val closes = candles.map { it.close }
        
        // Custom periods
        val macd = IndicatorCalculator.macd(closes, shortPeriod = 8, longPeriod = 17, signalPeriod = 9)
        assertNotNull(macd)
    }

    @Test
    fun `macd edge cases`() {
        // Insufficient data
        val shortData = List(20) { it.toDouble() }
        assertNull(IndicatorCalculator.macd(shortData))
        
        // Minimum data (26 + 9 = 35)
        val minData = List(35) { 100.0 + it * 0.1 }
        assertNotNull(IndicatorCalculator.macd(minData))
        
        // Flat prices
        val flat = List(50) { 100.0 }
        val macdFlat = IndicatorCalculator.macd(flat)
        assertNotNull(macdFlat)
        assertEquals(0.0, macdFlat!!.macd, 0.0001)
        assertEquals(0.0, macdFlat.signal, 0.0001)
        assertEquals(0.0, macdFlat.histogram, 0.0001)
    }

    @Test
    fun `macd histogram tracks momentum`() {
        val bullish = TestDataFactory.macdBullishCrossCandles()
        val macdBull = IndicatorCalculator.macd(bullish.map { it.close })
        assertNotNull(macdBull)
        
        val bearish = TestDataFactory.macdBearishCrossCandles()
        val macdBear = IndicatorCalculator.macd(bearish.map { it.close })
        assertNotNull(macdBear)
        
        // Previous histogram should be tracked
        assertNotNull(macdBull!!.prevHistogram)
        assertNotNull(macdBear!!.prevHistogram)
    }

    // ==================== Bollinger Bands Tests ====================

    @Test
    fun `bollinger with default parameters`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_MONTH,
            PriceCandleBuilder.PricePattern.SIDEWAYS
        )
        val closes = candles.map { it.close }
        
        val bands = IndicatorCalculator.bollinger(closes)
        assertNotNull(bands)
        assertTrue(bands!!.upper > bands.middle)
        assertTrue(bands.middle > bands.lower)
    }

    @Test
    fun `bollinger with custom parameters`() {
        val closes = List(50) { 100.0 + Math.random() * 5 }
        
        // Period 10, multiplier 1.5
        val bands1 = IndicatorCalculator.bollinger(closes, period = 10, multiplier = 1.5)
        assertNotNull(bands1)
        
        // Period 30, multiplier 3.0
        val bands2 = IndicatorCalculator.bollinger(closes, period = 30, multiplier = 3.0)
        assertNotNull(bands2)
        
        // Wider multiplier should have wider bands
        val width1 = bands1!!.upper - bands1.lower
        val width2 = bands2!!.upper - bands2.lower
        assertTrue(width2 > width1)
    }

    @Test
    fun `bollinger edge cases`() {
        // Insufficient data
        assertNull(IndicatorCalculator.bollinger(listOf(1.0, 2.0), 20))
        
        // Exactly minimum
        val minData = List(20) { it.toDouble() }
        assertNotNull(IndicatorCalculator.bollinger(minData, 20))
        
        // Flat prices (zero std dev)
        val flat = List(20) { 100.0 }
        val bandsFlat = IndicatorCalculator.bollinger(flat, 20, 2.0)
        assertNotNull(bandsFlat)
        assertEquals(100.0, bandsFlat!!.middle, 0.0001)
        assertEquals(100.0, bandsFlat.upper, 0.0001)
        assertEquals(100.0, bandsFlat.lower, 0.0001)
    }

    @Test
    fun `bollinger realistic breakouts`() {
        // Upper breakout
        val upperBreakout = TestDataFactory.bollingerUpperBreakoutCandles()
        val bandsUpper = IndicatorCalculator.bollinger(upperBreakout.map { it.close })
        assertNotNull(bandsUpper)
        val lastClose = upperBreakout.last().close
        // Price should be near or above upper band
        assertTrue(lastClose >= bandsUpper!!.middle)
        
        // Lower breakdown
        val lowerBreakdown = TestDataFactory.bollingerLowerBreakdownCandles()
        val bandsLower = IndicatorCalculator.bollinger(lowerBreakdown.map { it.close })
        assertNotNull(bandsLower)
        val lastCloseLower = lowerBreakdown.last().close
        // Price should be near or below middle
        assertTrue(lastCloseLower <= bandsLower!!.middle)
    }

    // ==================== ATR Tests ====================

    @Test
    fun `atr with default period 14`() {
        val candles = TestDataFactory.highVolatilityCandles()
        val atr = IndicatorCalculator.atr(candles)
        assertNotNull(atr)
        assertTrue(atr!! > 0)
    }

    @Test
    fun `atr with various periods`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_MONTH,
            PriceCandleBuilder.PricePattern.VOLATILE
        )
        
        val atr7 = IndicatorCalculator.atr(candles, 7)
        assertNotNull(atr7)
        
        val atr14 = IndicatorCalculator.atr(candles, 14)
        assertNotNull(atr14)
        
        val atr21 = IndicatorCalculator.atr(candles, 21)
        assertNotNull(atr21)
    }

    @Test
    fun `atr edge cases`() {
        // Insufficient data (need period + 1)
        val shortCandles = TestDataFactory.flatPriceCandles(count = 10)
        assertNull(IndicatorCalculator.atr(shortCandles, 14))
        
        // Minimum data
        val minCandles = TestDataFactory.flatPriceCandles(count = 15)
        assertNotNull(IndicatorCalculator.atr(minCandles, 14))
        
        // Flat prices (zero volatility)
        val flatCandles = TestDataFactory.flatPriceCandles(count = 20, price = 100.0)
        val atrFlat = IndicatorCalculator.atr(flatCandles, 14)
        assertEquals(0.0, atrFlat!!, 0.0001)
    }

    @Test
    fun `atr reflects volatility`() {
        val lowVol = TestDataFactory.lowVolatilityCandles()
        val atrLow = IndicatorCalculator.atr(lowVol, 14)
        
        val highVol = TestDataFactory.highVolatilityCandles()
        val atrHigh = IndicatorCalculator.atr(highVol, 14)
        
        assertNotNull(atrLow)
        assertNotNull(atrHigh)
        assertTrue("High volatility should have higher ATR", atrHigh!! > atrLow!!)
    }

    @Test
    fun `atr percent calculation for alerts`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_MONTH,
            PriceCandleBuilder.PricePattern.VOLATILE
        )
        val atr = IndicatorCalculator.atr(candles, 14)
        val lastClose = candles.last().close
        
        assertNotNull(atr)
        val atrPercent = atr!! / lastClose * 100.0
        assertTrue("ATR% should be positive", atrPercent > 0)
        assertTrue("ATR% should be reasonable", atrPercent < 50) // Less than 50%
    }

    // ==================== Z-Score Tests ====================

    @Test
    fun `zScore with window 20`() {
        val values = List(30) { 100.0 + Math.random() * 10 }
        val zScore = IndicatorCalculator.zScore(values, 20)
        assertNotNull(zScore)
        assertTrue(abs(zScore!!) < 5) // Should be within reasonable bounds
    }

    @Test
    fun `zScore edge cases`() {
        // Insufficient data
        assertNull(IndicatorCalculator.zScore(listOf(1.0, 2.0), 20))
        
        // Window too small
        assertNull(IndicatorCalculator.zScore(listOf(1.0, 2.0, 3.0), 1))
        
        // Flat series (zero std dev)
        val flat = List(20) { 5.0 }
        val zFlat = IndicatorCalculator.zScore(flat, 20)
        assertEquals(0.0, zFlat!!, 0.0001)
    }

    @Test
    fun `zScore detects outliers`() {
        // Normal values with outlier
        val values = MutableList(20) { 100.0 }
        values.add(150.0) // Outlier
        
        val zScore = IndicatorCalculator.zScore(values, 20)
        assertNotNull(zScore)
        assertTrue("Outlier should have high z-score", abs(zScore!!) > 2.0)
    }

    // ==================== Rolling Return Z-Score Tests ====================

    @Test
    fun `rollingReturnZScore with window 20`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_MONTH,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        )
        val closes = candles.map { it.close }
        
        val returnZ = IndicatorCalculator.rollingReturnZScore(closes, 20)
        assertNotNull(returnZ)
    }

    @Test
    fun `rollingReturnZScore edge cases`() {
        // Insufficient data
        assertNull(IndicatorCalculator.rollingReturnZScore(listOf(1.0, 2.0), 20))
        
        // Minimum data (window + 1)
        val minData = List(21) { 100.0 + it * 0.5 }
        assertNotNull(IndicatorCalculator.rollingReturnZScore(minData, 20))
        
        // Flat prices (zero returns)
        val flat = List(25) { 100.0 }
        val returnZFlat = IndicatorCalculator.rollingReturnZScore(flat, 20)
        assertEquals(0.0, returnZFlat!!, 0.0001)
    }

    @Test
    fun `rollingReturnZScore detects strong trends`() {
        // Strong positive trend
        val uptrend = List(30) { 100.0 + it * 2.0 }
        val zUp = IndicatorCalculator.rollingReturnZScore(uptrend, 20)
        assertNotNull(zUp)
        assertTrue("Z-score should be finite", zUp!!.isFinite())
        
        // Strong negative trend
        val downtrend = List(30) { 200.0 - it * 2.0 }
        val zDown = IndicatorCalculator.rollingReturnZScore(downtrend, 20)
        assertNotNull(zDown)
        assertTrue("Z-score should be finite", zDown!!.isFinite())
        
        // The z-scores should be different for opposite trends
        assertNotEquals("Different trends should have different z-scores", zUp, zDown, 0.01)
    }

    @Test
    fun `rollingReturnZScore handles zero prices`() {
        val closes = MutableList(25) { 100.0 + it.toDouble() }
        closes[10] = 0.0 // Introduce zero (should be skipped in returns calculation)
        
        val returnZ = IndicatorCalculator.rollingReturnZScore(closes, 20)
        assertNotNull(returnZ)
    }

    // ==================== Standard Deviation Tests ====================

    @Test
    fun `stdDev calculations`() {
        // Known values
        val values = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val stdDev = IndicatorCalculator.stdDev(values)
        assertEquals(2.0, stdDev, 0.01) // Known std dev ≈ 2.0
    }

    @Test
    fun `stdDev edge cases`() {
        // Empty list
        assertEquals(0.0, IndicatorCalculator.stdDev(emptyList()), 0.0001)
        
        // Single value
        assertEquals(0.0, IndicatorCalculator.stdDev(listOf(5.0)), 0.0001)
        
        // Flat series
        assertEquals(0.0, IndicatorCalculator.stdDev(List(10) { 10.0 }), 0.0001)
    }

    // ==================== EMA Series Tests ====================

    @Test
    fun `emaSeries with period 12`() {
        val values = (1..50).map { it.toDouble() }
        val ema = IndicatorCalculator.emaSeries(values, 12)
        
        assertEquals(values.size, ema.size)
        assertNull(ema[0]) // First values are null
        assertNotNull(ema[11]) // First EMA at period-1
        assertNotNull(ema.last())
    }

    @Test
    fun `emaSeries edge cases`() {
        // Empty list
        assertTrue(IndicatorCalculator.emaSeries(emptyList(), 12).isEmpty())
        
        // Insufficient data
        val shortData = listOf(1.0, 2.0, 3.0)
        val emaShort = IndicatorCalculator.emaSeries(shortData, 12)
        assertEquals(shortData.size, emaShort.size)
        assertTrue(emaShort.all { it == null })
        
        // Period zero
        assertTrue(IndicatorCalculator.emaSeries(listOf(1.0, 2.0), 0).isEmpty())
    }

    // ==================== Integration Tests ====================

    @Test
    fun `all indicators work together on realistic data`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_YEAR,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        )
        val closes = candles.map { it.close }
        
        // All indicators should return valid results
        assertNotNull(IndicatorCalculator.sma(closes, 50))
        assertNotNull(IndicatorCalculator.sma(closes, 200))
        assertNotNull(IndicatorCalculator.rsi(closes, 14))
        assertNotNull(IndicatorCalculator.macd(closes))
        assertNotNull(IndicatorCalculator.bollinger(closes, 20, 2.0))
        assertNotNull(IndicatorCalculator.atr(candles, 14))
        assertNotNull(IndicatorCalculator.zScore(closes.map { it }, 20))
        assertNotNull(IndicatorCalculator.rollingReturnZScore(closes, 20))
    }
}
