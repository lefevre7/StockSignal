package com.example.stocksignal.domain.model

import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
import com.example.stocksignal.data.local.entity.StockOverviewCacheEntity
import com.example.stocksignal.data.stooq.model.EnrichedIntradayResponse
import com.example.stocksignal.data.stooq.model.IntradayStockData
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ModelCoverageTest {

    @Test
    fun `data classes preserve fields and derived tier`() {
        val generatedAt = LocalDateTime.of(2026, 3, 31, 10, 15)
        val reasons = listOf(SignalReason("macd", "MACD", "Bullish crossover", 14, "macd"))
        val historyEntry = SignalHistoryEntry(
            generatedAt = generatedAt,
            score = 72,
            averageScore = 68,
            modeScore = 70,
            confidence = 81,
            reasons = reasons
        )
        val detailCache = StockDetailCacheEntity(
            symbol = "AAPL",
            range = "ONE_DAY",
            fetchedAt = generatedAt,
            seriesJson = "[1,2,3]",
            latestPrice = 186.42,
            indicatorsJson = "{\"rsi\":54.2}",
            signalHistoryJson = "[]"
        )
        val overviewCache = StockOverviewCacheEntity(
            symbol = "AAPL",
            marketCap = 2_900_000_000_000.0,
            peRatio = 28.4,
            dividend = 0.52,
            week52High = 199.62,
            week52Low = 142.10,
            newsJson = "[{\"title\":\"headline\"}]",
            fetchedAt = "2026-03-31T10:15:00"
        )
        val stockDetail = StockDetail(
            symbol = "AAPL",
            companyName = "Apple Inc.",
            exchange = "NASDAQ",
            latestPrice = 186.42,
            percentChange = 2.31,
            lastUpdated = generatedAt,
            seriesByRange = mapOf(
                ChartRange.ONE_DAY to listOf(
                    PriceCandle(
                        time = generatedAt,
                        open = 185.0,
                        high = 187.0,
                        low = 184.5,
                        close = 186.42,
                        volume = 1_250_000L
                    )
                )
            ),
            indicators = TechnicalIndicators(
                rsi14 = 54.2,
                macd = 1.6,
                macdSignal = 1.2,
                macdHistogram = 0.4,
                sma5 = 184.0,
                sma20 = 180.0,
                sma50 = 176.0,
                sma200 = 165.0,
                atr14 = 3.5
            ),
            signal = null,
            signalHistory = listOf(historyEntry)
        )

        assertEquals(SignalTier.STRONG_BUY, historyEntry.tier)
        assertEquals("AAPL", detailCache.symbol)
        assertEquals(186.42, detailCache.latestPrice!!, 0.0)
        assertEquals(28.4, overviewCache.peRatio!!, 0.0)
        assertEquals("Apple Inc.", stockDetail.companyName)
        assertSame(historyEntry, stockDetail.signalHistory.single())
    }

    @Test
    fun `market mover and enriched intraday helpers resolve text variants`() {
        val timestamp = LocalDateTime.of(2026, 3, 31, 9, 30)
        val candle = IntradayStockData(
            dateTime = timestamp,
            open = 100.0,
            high = 101.5,
            low = 99.5,
            close = 100.75,
            volume = 1_000L,
            openInterest = 12L,
            annotation = "A"
        )
        val response = EnrichedIntradayResponse(
            data = mapOf(timestamp to candle),
            exchange = "NASDAQ"
        )

        assertEquals(MarketMoverRange.ONE_DAY, MarketMoverRange.fromText("Daily movers"))
        assertEquals(MarketMoverRange.FIVE_DAY, MarketMoverRange.fromText("5 day leaders"))
        assertEquals(MarketMoverRange.ONE_MONTH, MarketMoverRange.fromText("1 month"))
        assertEquals(MarketMoverRange.SIX_MONTH, MarketMoverRange.fromText("6M"))
        assertEquals(MarketMoverRange.ONE_YEAR, MarketMoverRange.fromText("1 year"))
        assertEquals(MarketMoverRange.FIVE_YEAR, MarketMoverRange.fromText("5Y"))
        assertNull(MarketMoverRange.fromText("unknown"))

        assertEquals(MarketMoverDirection.MOST_ACTIVE, MarketMoverDirection.fromText("Najbardziej aktywne"))
        assertEquals(MarketMoverDirection.INCREASERS, MarketMoverDirection.fromText("top gainers"))
        assertEquals(MarketMoverDirection.DECREASERS, MarketMoverDirection.fromText("decliners"))
        assertNull(MarketMoverDirection.fromText("sideways"))

        assertEquals("NASDAQ", response.exchange)
        assertSame(candle, response.data[timestamp])
    }
}
