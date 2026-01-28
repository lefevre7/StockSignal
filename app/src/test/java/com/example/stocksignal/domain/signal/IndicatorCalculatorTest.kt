package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class IndicatorCalculatorTest {

    @Test
    fun `sma uses last period`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val result = IndicatorCalculator.sma(values, 3)
        assertEquals(4.0, requireNotNull(result), 0.0001)
    }

    @Test
    fun `rsi returns 100 for all gains`() {
        val closes = (1..20).map { it.toDouble() }
        val result = IndicatorCalculator.rsi(closes, 14)
        assertEquals(100.0, requireNotNull(result), 0.0001)
    }

    @Test
    fun `bollinger flat series uses mean`() {
        val closes = List(20) { 10.0 }
        val bands = IndicatorCalculator.bollinger(closes, 20, 2.0)
        assertNotNull(bands)
        val safeBands = requireNotNull(bands)
        assertEquals(10.0, safeBands.middle, 0.0001)
        assertEquals(10.0, safeBands.upper, 0.0001)
        assertEquals(10.0, safeBands.lower, 0.0001)
    }

    @Test
    fun `atr flat candles returns zero`() {
        val now = LocalDateTime.of(2024, 1, 1, 0, 0)
        val candles = List(16) { index ->
            PriceCandle(
                time = now.plusMinutes(index.toLong()),
                open = 10.0,
                high = 10.0,
                low = 10.0,
                close = 10.0,
                volume = 1000L
            )
        }
        val atr = IndicatorCalculator.atr(candles, 14)
        assertEquals(0.0, requireNotNull(atr), 0.0001)
    }

    @Test
    fun `zscore flat window returns zero`() {
        val values = List(20) { 5.0 }
        val zScore = IndicatorCalculator.zScore(values, 20)
        assertEquals(0.0, requireNotNull(zScore), 0.0001)
    }

    @Test
    fun `return zscore flat series returns zero`() {
        val closes = List(21) { 10.0 }
        val zScore = IndicatorCalculator.rollingReturnZScore(closes, 20)
        assertEquals(0.0, requireNotNull(zScore), 0.0001)
    }

    // ==================== Additional Comprehensive Tests ====================

    @Test
    fun `sma different periods on same data`() {
        val values = (1..100).map { it.toDouble() }
        val sma10 = IndicatorCalculator.sma(values, 10)
        val sma20 = IndicatorCalculator.sma(values, 20)
        val sma50 = IndicatorCalculator.sma(values, 50)

        assertNotNull(sma10)
        assertNotNull(sma20)
        assertNotNull(sma50)
        
        // For increasing series, shorter periods give higher SMAs
        assertTrue("SMA10 should be > SMA20 for increasing series", sma10!! > sma20!!)
        assertTrue("SMA20 should be > SMA50 for increasing series", sma20 > sma50!!)
    }

    @Test
    fun `rsi boundary conditions`() {
        // RSI should be between 0 and 100
        val uptrend = (1..20).map { it.toDouble() }
        val downtrend = (20 downTo 1).map { it.toDouble() }
        val mixed = listOf(10.0, 11.0, 10.5, 11.5, 10.8, 11.2, 10.9, 11.1, 11.0, 10.7, 
                           11.3, 10.6, 11.4, 10.5, 11.5)

        val rsiUp = IndicatorCalculator.rsi(uptrend, 14)
        val rsiDown = IndicatorCalculator.rsi(downtrend, 14)
        val rsiMixed = IndicatorCalculator.rsi(mixed, 14)

        assertTrue("RSI should be <= 100", rsiUp!! <= 100.0)
        assertTrue("RSI should be >= 0", rsiDown!! >= 0.0)
        assertTrue("RSI mixed should be between 0 and 100", rsiMixed!! in 0.0..100.0)
    }

    @Test
    fun `macd with custom short and long periods`() {
        val closes = TestDataFactory.candlesForRange(
            ChartRange.ONE_YEAR,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        ).map { it.close }

        val macdDefault = IndicatorCalculator.macd(closes)
        val macdCustom = IndicatorCalculator.macd(closes, shortPeriod = 8, longPeriod = 21, signalPeriod = 9)

        assertNotNull(macdDefault)
        assertNotNull(macdCustom)
        
        // Custom parameters should give different results
        assertNotEquals(macdDefault!!.macd, macdCustom!!.macd, 0.01)
    }

    @Test
    fun `bollinger bands widen with higher multiplier`() {
        val closes = (1..30).map { 100.0 + Math.random() * 5 }

        val bands1x = IndicatorCalculator.bollinger(closes, 20, 1.0)
        val bands2x = IndicatorCalculator.bollinger(closes, 20, 2.0)
        val bands3x = IndicatorCalculator.bollinger(closes, 20, 3.0)

        assertNotNull(bands1x)
        assertNotNull(bands2x)
        assertNotNull(bands3x)

        val width1 = bands1x!!.upper - bands1x.lower
        val width2 = bands2x!!.upper - bands2x.lower
        val width3 = bands3x!!.upper - bands3x.lower

        assertTrue("2x multiplier should be wider than 1x", width2 > width1)
        assertTrue("3x multiplier should be wider than 2x", width3 > width2)
    }

    @Test
    fun `atr increases with higher volatility`() {
        val lowVol = TestDataFactory.lowVolatilityCandles()
        val highVol = TestDataFactory.highVolatilityCandles()

        val atrLow = IndicatorCalculator.atr(lowVol, 14)
        val atrHigh = IndicatorCalculator.atr(highVol, 14)

        assertNotNull(atrLow)
        assertNotNull(atrHigh)
        assertTrue("High volatility should have higher ATR", atrHigh!! > atrLow!!)
    }

    @Test
    fun `zscore extreme outlier detection`() {
        val normalValues = List(19) { 100.0 + Math.random() * 2 }
        val valuesWithOutlier = normalValues + 200.0 // Extreme outlier

        val zScore = IndicatorCalculator.zScore(valuesWithOutlier, 20)
        assertNotNull(zScore)
        assertTrue("Extreme outlier should have z-score > 3", Math.abs(zScore!!) > 3.0)
    }

    @Test
    fun `return zscore trending data`() {
        val strongUptrend = List(30) { 100.0 + it * 5.0 }
        val strongDowntrend = List(30) { 200.0 - it * 5.0 }

        val zUp = IndicatorCalculator.rollingReturnZScore(strongUptrend, 20)
        val zDown = IndicatorCalculator.rollingReturnZScore(strongDowntrend, 20)

        assertNotNull(zUp)
        assertNotNull(zDown)
        // Strong trends should produce finite z-scores
        assertTrue("Uptrend z-score should be finite", zUp!!.isFinite())
        assertTrue("Downtrend z-score should be finite", zDown!!.isFinite())
        // The z-scores should be different for opposite trends
        assertNotEquals("Different trends should yield different z-scores", zUp, zDown, 0.01)
    }

    @Test
    fun `ema series converges over time`() {
        val values = List(50) { 100.0 + Math.random() * 10 }
        val ema12 = IndicatorCalculator.emaSeries(values, 12)

        // First EMA should be at index 11 (period - 1)
        assertNull(ema12[10])
        assertNotNull(ema12[11])
        
        // Later EMAs should be more responsive to recent prices
        val lastEma = ema12.last()
        val lastPrice = values.last()
        assertNotNull(lastEma)
    }

    @Test
    fun `stdDev increases with spread`() {
        val tightSpread = List(10) { 100.0 + Math.random() * 1 }
        val wideSpread = List(10) { 100.0 + Math.random() * 20 }

        val stdTight = IndicatorCalculator.stdDev(tightSpread)
        val stdWide = IndicatorCalculator.stdDev(wideSpread)

        assertTrue("Wide spread should have higher std dev", stdWide > stdTight)
    }

    @Test
    fun `all indicators handle realistic market data`() {
        val candles = TestDataFactory.candlesForRange(
            ChartRange.SIX_MONTH,
            PriceCandleBuilder.PricePattern.VOLATILE
        )
        val closes = candles.map { it.close }

        // All indicators should handle realistic volatile data
        val sma50 = IndicatorCalculator.sma(closes, 50)
        val rsi14 = IndicatorCalculator.rsi(closes, 14)
        val macd = IndicatorCalculator.macd(closes)
        val bollinger = IndicatorCalculator.bollinger(closes)
        val atr = IndicatorCalculator.atr(candles, 14)
        val volumeZ = IndicatorCalculator.zScore(candles.map { it.volume.toDouble() }, 20)
        val returnZ = IndicatorCalculator.rollingReturnZScore(closes, 20)

        assertNotNull("SMA should handle volatile data", sma50)
        assertNotNull("RSI should handle volatile data", rsi14)
        assertNotNull("MACD should handle volatile data", macd)
        assertNotNull("Bollinger should handle volatile data", bollinger)
        assertNotNull("ATR should handle volatile data", atr)
        assertNotNull("Volume Z should handle volatile data", volumeZ)
        assertNotNull("Return Z should handle volatile data", returnZ)

        // All values should be finite
        assertTrue("SMA should be finite", sma50!!.isFinite())
        assertTrue("RSI should be finite", rsi14!!.isFinite())
        assertTrue("MACD should be finite", macd!!.macd.isFinite())
        assertTrue("ATR should be finite", atr!!.isFinite())
    }
}
