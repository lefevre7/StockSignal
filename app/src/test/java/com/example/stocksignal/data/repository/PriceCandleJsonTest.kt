package com.example.stocksignal.data.repository

import com.example.stocksignal.domain.model.PriceCandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PriceCandleJsonTest {

    @Test
    fun `fromJson returns empty list for null`() {
        assertTrue(PriceCandleJson.fromJson(null).isEmpty())
    }

    @Test
    fun `fromJson returns empty list for empty string`() {
        assertTrue(PriceCandleJson.fromJson("").isEmpty())
    }

    @Test
    fun `fromJson returns empty list for blank string`() {
        assertTrue(PriceCandleJson.fromJson("   ").isEmpty())
    }

    @Test
    fun `roundtrip preserves all candle fields`() {
        val candles = listOf(
            PriceCandle(
                time = LocalDateTime.of(2026, 3, 31, 9, 30),
                open = 185.0,
                high = 188.5,
                low = 184.0,
                close = 187.0,
                volume = 12_345_678L
            ),
            PriceCandle(
                time = LocalDateTime.of(2026, 3, 31, 10, 0),
                open = 187.0,
                high = 190.0,
                low = 186.5,
                close = 189.25,
                volume = 5_000_000L
            )
        )
        val json = PriceCandleJson.toJson(candles)
        val decoded = PriceCandleJson.fromJson(json)
        assertEquals(candles.size, decoded.size)
        candles.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.time, actual.time)
            assertEquals(expected.open, actual.open, 0.0001)
            assertEquals(expected.high, actual.high, 0.0001)
            assertEquals(expected.low, actual.low, 0.0001)
            assertEquals(expected.close, actual.close, 0.0001)
            assertEquals(expected.volume, actual.volume)
        }
    }

    @Test
    fun `fromJson defaults volume to 0 when field is missing`() {
        val json = """[{"time":"2026-03-31T09:30:00","open":100.0,"high":101.0,"low":99.0,"close":100.5}]"""
        val candles = PriceCandleJson.fromJson(json)
        assertEquals(1, candles.size)
        assertEquals(0L, candles[0].volume)
    }

    @Test
    fun `toJson returns valid JSON array for empty list`() {
        val json = PriceCandleJson.toJson(emptyList())
        assertEquals("[]", json)
    }

    @Test
    fun `roundtrip preserves fractional prices`() {
        val candle = PriceCandle(
            time = LocalDateTime.of(2026, 1, 15, 15, 59),
            open = 432.7500,
            high = 435.1200,
            low = 431.0000,
            close = 433.9800,
            volume = 0L
        )
        val decoded = PriceCandleJson.fromJson(PriceCandleJson.toJson(listOf(candle))).single()
        assertEquals(candle.open, decoded.open, 0.0001)
        assertEquals(candle.high, decoded.high, 0.0001)
        assertEquals(candle.close, decoded.close, 0.0001)
    }
}
