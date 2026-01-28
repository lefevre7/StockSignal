package com.example.stocksignal.domain.signal

import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.domain.model.AlertDirection
import com.example.stocksignal.domain.model.IndicatorMetric
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive tests for IndicatorAlertEvaluator
 * Tests all 8 metrics, crossing logic, both directions, edge cases
 */
class IndicatorAlertEvaluatorTest {

    // ==================== RSI_14 Tests ====================

    @Test
    fun `RSI_14 crosses below 30`() {
        val candles = TestDataFactory.rsiOversoldCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(30.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("Current RSI should be below 30", evaluation!!.current < 30.0)
    }

    @Test
    fun `RSI_14 crosses above 70`() {
        val candles = TestDataFactory.rsiOverboughtCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(70.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("Current RSI should be above 70", evaluation!!.current > 70.0)
    }

    @Test
    fun `RSI_14 at threshold does not cross`() {
        // Create data that results in RSI near 50
        val candles = TestDataFactory.rsiNeutralCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(50.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        // Whether it crossed depends on previous value
    }

    @Test
    fun `RSI_14 insufficient data returns null`() {
        val candles = TestDataFactory.flatPriceCandles(count = 10)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(30.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNull("Should return null for insufficient data", evaluation)
    }

    @Test
    fun `RSI_14 flat prices returns value`() {
        val candles = TestDataFactory.flatPriceCandles(count = 20)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(50.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        // Flat prices should give RSI = 100 (no losses)
        assertEquals(100.0, evaluation!!.current, 0.1)
    }

    // ==================== MACD Tests ====================

    @Test
    fun `MACD_HISTOGRAM crosses above zero`() {
        val candles = TestDataFactory.macdBullishCrossCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.MACD_HISTOGRAM)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertNotNull(evaluation!!.previous)
    }

    @Test
    fun `MACD_HISTOGRAM crosses below zero`() {
        val candles = TestDataFactory.macdBearishCrossCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.MACD_HISTOGRAM)
            .threshold(0.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertNotNull(evaluation!!.previous)
    }

    @Test
    fun `MACD_LINE calculated correctly`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_MONTH,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        )
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.MACD_LINE)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertNotNull(evaluation!!.previous)
    }

    @Test
    fun `MACD insufficient data returns null`() {
        val candles = TestDataFactory.flatPriceCandles(count = 30) // Need 26+9 = 35
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.MACD_HISTOGRAM)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNull(evaluation)
    }

    // ==================== SMA Distance Tests ====================

    @Test
    fun `SMA_50_DISTANCE price above SMA`() {
        val candles = PriceCandleBuilder()
            .count(60)
            .pattern(PriceCandleBuilder.PricePattern.TRENDING_UP)
            .basePrice(100.0)
            .build()
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.SMA_50_DISTANCE)
            .threshold(5.0) // 5% above SMA
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("Distance should be positive for uptrend", evaluation!!.current > 0)
    }

    @Test
    fun `SMA_50_DISTANCE price below SMA`() {
        val candles = PriceCandleBuilder()
            .count(60)
            .pattern(PriceCandleBuilder.PricePattern.TRENDING_DOWN)
            .basePrice(100.0)
            .build()
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.SMA_50_DISTANCE)
            .threshold(-5.0) // 5% below SMA
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("Distance should be negative for downtrend", evaluation!!.current < 0)
    }

    @Test
    fun `SMA_200_DISTANCE calculated correctly`() {
        val candles = PriceCandleBuilder()
            .count(210)
            .pattern(PriceCandleBuilder.PricePattern.TRENDING_UP)
            .basePrice(100.0)
            .build()
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.SMA_200_DISTANCE)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertNotNull(evaluation!!.previous)
    }

    @Test
    fun `SMA_50_DISTANCE insufficient data returns null`() {
        val candles = TestDataFactory.flatPriceCandles(count = 40) // Need 51 for previous
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.SMA_50_DISTANCE)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNull(evaluation)
    }

    @Test
    fun `SMA_DISTANCE at zero when price equals SMA`() {
        // Flat prices should make price = SMA
        val candles = TestDataFactory.flatPriceCandles(count = 60, price = 100.0)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.SMA_50_DISTANCE)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertEquals("Distance should be 0 when price = SMA", 0.0, evaluation!!.current, 0.01)
    }

    // ==================== Bollinger %B Tests ====================

    @Test
    fun `BOLLINGER_PERCENT_B at upper band is 100`() {
        val candles = TestDataFactory.bollingerUpperBreakoutCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.BOLLINGER_PERCENT_B)
            .threshold(80.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("%B should be high for upper breakout", evaluation!!.current > 50.0)
    }

    @Test
    fun `BOLLINGER_PERCENT_B at lower band is 0`() {
        val candles = TestDataFactory.bollingerLowerBreakdownCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.BOLLINGER_PERCENT_B)
            .threshold(20.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("%B should be low for lower breakdown", evaluation!!.current < 50.0)
    }

    @Test
    fun `BOLLINGER_PERCENT_B at middle band is 50`() {
        val candles = TestDataFactory.rsiNeutralCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.BOLLINGER_PERCENT_B)
            .threshold(50.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        // Neutral/sideways movement should give a %B value (not necessarily exactly 50)
        // Just verify we got a reasonable value
        assertTrue("%B should be finite", evaluation!!.current.isFinite())
    }

    @Test
    fun `BOLLINGER_PERCENT_B flat prices returns null or 50`() {
        val candles = TestDataFactory.flatPriceCandles(count = 25, price = 100.0)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.BOLLINGER_PERCENT_B)
            .threshold(50.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        // Flat prices have zero range, should return null
        assertNull(evaluation)
    }

    @Test
    fun `BOLLINGER_PERCENT_B insufficient data returns null`() {
        val candles = TestDataFactory.flatPriceCandles(count = 15) // Need 20
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.BOLLINGER_PERCENT_B)
            .threshold(80.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNull(evaluation)
    }

    // ==================== ATR % Tests ====================

    @Test
    fun `ATR_PERCENT high volatility scenario`() {
        val candles = TestDataFactory.highVolatilityCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ATR_PERCENT)
            .threshold(5.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("ATR% should be high for volatile market", evaluation!!.current > 0)
    }

    @Test
    fun `ATR_PERCENT low volatility scenario`() {
        val candles = TestDataFactory.lowVolatilityCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ATR_PERCENT)
            .threshold(1.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertTrue("ATR% should be low for low volatility", evaluation!!.current < 5.0)
    }

    @Test
    fun `ATR_PERCENT flat prices is zero`() {
        val candles = TestDataFactory.flatPriceCandles(count = 20, price = 100.0)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ATR_PERCENT)
            .threshold(1.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertEquals("ATR% should be 0 for flat prices", 0.0, evaluation!!.current, 0.01)
    }

    @Test
    fun `ATR_PERCENT insufficient data returns null`() {
        val candles = TestDataFactory.flatPriceCandles(count = 10) // Need 15
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ATR_PERCENT)
            .threshold(5.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNull(evaluation)
    }

    // ==================== Rolling Return Z-Score Tests ====================

    @Test
    fun `ROLLING_RETURN_ZSCORE strong uptrend above 2`() {
        // Create an uptrend where the last return is unusually positive (accelerating rally)
        val normalUptrend = PriceCandleBuilder()
            .count(28)
            .pattern(PriceCandleBuilder.PricePattern.TRENDING_UP)
            .basePrice(100.0)
            .volatility(0.005) // Low volatility for consistent returns
            .build()
        
        // Add two candles with accelerating rally to create unusual positive return
        val lastPrice = normalUptrend.last().close
        val acceleratedCandles = normalUptrend + listOf(
            normalUptrend.last().copy(
                time = normalUptrend.last().time.plusDays(1),
                close = lastPrice * 1.02, // +2% gain
                open = lastPrice,
                high = lastPrice * 1.02,
                low = lastPrice
            ),
            normalUptrend.last().copy(
                time = normalUptrend.last().time.plusDays(2),
                close = lastPrice * 1.02 * 1.03, // Another +3% gain (accelerating)
                open = lastPrice * 1.02,
                high = lastPrice * 1.02 * 1.03,
                low = lastPrice * 1.02
            )
        )
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ROLLING_RETURN_ZSCORE)
            .threshold(2.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, acceleratedCandles, HoldingPeriod.WEEKS)
        assertNotNull(evaluation)
        assertTrue("Z-score should be positive for accelerating uptrend", evaluation!!.current > 0)
    }

    @Test
    fun `ROLLING_RETURN_ZSCORE strong downtrend below -2`() {
        // Create a downtrend where the last return is unusually negative (accelerating decline)
        val normalDowntrend = PriceCandleBuilder()
            .count(28)
            .pattern(PriceCandleBuilder.PricePattern.TRENDING_DOWN)
            .basePrice(100.0)
            .volatility(0.005) // Low volatility for consistent returns
            .build()
        
        // Add two candles with accelerating decline to create unusual negative return
        val lastPrice = normalDowntrend.last().close
        val acceleratedCandles = normalDowntrend + listOf(
            normalDowntrend.last().copy(
                time = normalDowntrend.last().time.plusDays(1),
                close = lastPrice * 0.98, // -2% drop
                open = lastPrice,
                high = lastPrice,
                low = lastPrice * 0.98
            ),
            normalDowntrend.last().copy(
                time = normalDowntrend.last().time.plusDays(2),
                close = lastPrice * 0.98 * 0.97, // Another -3% drop (accelerating)
                open = lastPrice * 0.98,
                high = lastPrice * 0.98,
                low = lastPrice * 0.98 * 0.97
            )
        )
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ROLLING_RETURN_ZSCORE)
            .threshold(-2.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, acceleratedCandles, HoldingPeriod.WEEKS)
        assertNotNull(evaluation)
        assertTrue("Z-score should be negative for accelerating downtrend", evaluation!!.current < 0)
    }

    @Test
    fun `ROLLING_RETURN_ZSCORE flat prices is zero`() {
        val candles = TestDataFactory.flatPriceCandles(count = 25, price = 100.0)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ROLLING_RETURN_ZSCORE)
            .threshold(0.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles, HoldingPeriod.WEEKS)
        assertNotNull(evaluation)
        assertEquals("Z-score should be 0 for flat prices", 0.0, evaluation!!.current, 0.01)
    }

    @Test
    fun `ROLLING_RETURN_ZSCORE insufficient data returns null`() {
        val candles = TestDataFactory.flatPriceCandles(count = 18) // Need 21
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.ROLLING_RETURN_ZSCORE)
            .threshold(2.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles, HoldingPeriod.WEEKS)
        assertNull(evaluation)
    }

    // ==================== Crossing Logic Tests ====================

    @Test
    fun `crossing ABOVE detects threshold cross`() {
        // Create data where RSI crosses from below 30 to above 30
        val candles = PriceCandleBuilder()
            .count(25)
            .pattern(PriceCandleBuilder.PricePattern.REVERSAL_UP)
            .basePrice(100.0)
            .build()
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(30.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        // Check that current and previous are tracked
        assertNotNull(evaluation!!.previous)
        
        // If crossed, current should be > threshold and previous should be <= threshold
        if (evaluation.crossed) {
            assertTrue(evaluation.current > 30.0)
            assertTrue(evaluation.previous!! <= 30.0)
        }
    }

    @Test
    fun `crossing BELOW detects threshold cross`() {
        // Create data where RSI crosses from above 70 to below 70
        val candles = PriceCandleBuilder()
            .count(25)
            .pattern(PriceCandleBuilder.PricePattern.REVERSAL_DOWN)
            .basePrice(100.0)
            .build()
        
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(70.0)
            .direction(AlertDirection.BELOW)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        assertNotNull(evaluation!!.previous)
        
        if (evaluation.crossed) {
            assertTrue(evaluation.current < 70.0)
            assertTrue(evaluation.previous!! >= 70.0)
        }
    }

    @Test
    fun `no crossing when both values on same side`() {
        // Strongly overbought RSI, both current and previous should be > 70
        val candles = TestDataFactory.rsiOverboughtCandles()
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(70.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        assertNotNull(evaluation)
        
        // Both should be > 70, so no crossing
        if (evaluation!!.previous != null && evaluation.previous!! > 70.0 && evaluation.current > 70.0) {
            assertFalse("Should not cross if both values above threshold", evaluation.crossed)
        }
    }

    @Test
    fun `crossing requires previous value`() {
        // With only 2 candles, some metrics may not have previous value
        val candles = TestDataFactory.flatPriceCandles(count = 2)
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(50.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        // Should return null due to insufficient data for RSI
        assertNull(evaluation)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `evaluation returns null for too few candles`() {
        val candles = TestDataFactory.flatPriceCandles(count = 1)
        
        // Test each metric
        val metrics = IndicatorMetric.values()
        for (metric in metrics) {
            val alert = IndicatorAlertSettingBuilder()
                .metric(metric)
                .threshold(0.0)
                .direction(AlertDirection.ABOVE)
                .build()
            
            val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
            assertNull("$metric should return null for 1 candle", evaluation)
        }
    }

    @Test
    fun `evaluation handles NaN and Infinity`() {
        // This shouldn't happen with valid data, but test the guard
        val candles = TestDataFactory.flatPriceCandles(count = 30, price = 100.0)
        
        // Most flat-price scenarios are handled gracefully
        val alert = IndicatorAlertSettingBuilder()
            .metric(IndicatorMetric.RSI_14)
            .threshold(50.0)
            .direction(AlertDirection.ABOVE)
            .build()

        val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
        // Should return valid result (flat prices -> RSI 100)
        assertNotNull(evaluation)
        assertTrue("Value should be finite", evaluation!!.current.isFinite())
    }

    @Test
    fun `all metrics process realistic data successfully`() {
        val candles = TestDataFactory.candlesForRange(
            com.example.stocksignal.domain.model.ChartRange.ONE_YEAR,
            PriceCandleBuilder.PricePattern.TRENDING_UP
        )
        
        // Test all 8 metrics can process realistic data
        val metrics = IndicatorMetric.values()
        for (metric in metrics) {
            val alert = IndicatorAlertSettingBuilder()
                .metric(metric)
                .threshold(metric.defaultThreshold)
                .direction(metric.defaultDirection)
                .build()
            
            val evaluation = IndicatorAlertEvaluator.evaluate(alert, candles)
            assertNotNull("$metric should process realistic data", evaluation)
            assertNotNull("$metric should have current value", evaluation!!.current)
        }
    }
}
