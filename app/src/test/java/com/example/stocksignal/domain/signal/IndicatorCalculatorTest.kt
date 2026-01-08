package com.example.stocksignal.domain.signal

import com.example.stocksignal.domain.model.PriceCandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val zScore = IndicatorCalculator.returnZScore(closes, 20)
        assertEquals(0.0, requireNotNull(zScore), 0.0001)
    }
}
