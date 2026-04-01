package com.example.stocksignal.data.local.db

import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.domain.model.PriceCandle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `date and instant converters round trip nullable values`() {
        val date = LocalDate.of(2026, 3, 31)
        val dateTime = LocalDateTime.of(2026, 3, 31, 9, 45, 12)
        val instant = Instant.parse("2026-03-31T15:45:12Z")

        assertEquals("2026-03-31", converters.localDateToString(date))
        assertEquals(date, converters.stringToLocalDate("2026-03-31"))
        assertEquals("2026-03-31T09:45:12", converters.localDateTimeToString(dateTime))
        assertEquals(dateTime, converters.stringToLocalDateTime("2026-03-31T09:45:12"))
        assertEquals("2026-03-31T15:45:12Z", converters.instantToString(instant))
        assertEquals(instant, converters.stringToInstant("2026-03-31T15:45:12Z"))

        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
        assertNull(converters.localDateTimeToString(null))
        assertNull(converters.stringToLocalDateTime(null))
        assertNull(converters.instantToString(null))
        assertNull(converters.stringToInstant(null))
    }

    @Test
    fun `string list and map converters handle blank null and populated input`() {
        assertNull(converters.stringListToJson(null))
        assertEquals(emptyList<String>(), converters.jsonToStringList(null))
        assertEquals(emptyList<String>(), converters.jsonToStringList(""))
        assertEquals(listOf("core", "swing"), converters.jsonToStringList("[\"core\",\"swing\"]"))

        val mapJson = converters.mapToJson(mapOf("watchlist" to 2, "movers" to 5))
        assertTrue(mapJson!!.contains("\"watchlist\":2"))
        assertTrue(mapJson.contains("\"movers\":5"))
        assertNull(converters.mapToJson(null))
        assertEquals(emptyMap<String, Int>(), converters.jsonToMap(null))
        assertEquals(emptyMap<String, Int>(), converters.jsonToMap(""))
        assertEquals(
            mapOf("watchlist" to 2, "movers" to 5),
            converters.jsonToMap("""{"watchlist":2,"movers":5}""")
        )
    }

    @Test
    fun `market movers converters round trip optional fields and series`() {
        val movers = listOf(
            MarketMoverItem(
                ticker = "AAPL",
                companyName = "Apple Inc.",
                exchange = "NASDAQ",
                price = 186.42,
                percentChange = 2.31,
                rank = 1,
                signalScore = 74,
                signalLabel = "Strong Buy",
                series = listOf(
                    PriceCandle(
                        time = LocalDateTime.of(2026, 3, 31, 9, 30),
                        open = 185.0,
                        high = 187.0,
                        low = 184.5,
                        close = 186.42,
                        volume = 1_250_000L
                    )
                )
            ),
            MarketMoverItem(
                ticker = "MSFT",
                companyName = "Microsoft",
                exchange = null,
                price = null,
                percentChange = null,
                rank = null,
                signalScore = null,
                signalLabel = null,
                series = emptyList()
            )
        )

        val json = converters.marketMoversToJson(movers)
        val restored = converters.jsonToMarketMovers(json)

        assertEquals(movers, restored)
        assertNull(converters.marketMoversToJson(null))
        assertEquals(emptyList<MarketMoverItem>(), converters.jsonToMarketMovers(null))
        assertEquals(emptyList<MarketMoverItem>(), converters.jsonToMarketMovers(""))
    }
}
