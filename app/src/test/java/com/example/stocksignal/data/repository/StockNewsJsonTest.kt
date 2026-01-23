package com.example.stocksignal.data.repository

import com.example.stocksignal.domain.model.StockNewsItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class StockNewsJsonTest {

    @Test
    fun `round trip preserves translated fields`() {
        val item = StockNewsItem(
            title = "Tytul",
            publishedAtText = "16 sty, 15:54",
            publishedAt = Instant.parse("2025-01-16T14:54:00Z"),
            source = "Reuters",
            url = "https://example.com",
            translatedTitle = "Title. Jan 16, 15:54 * Reuters",
            translatedPublishedAtText = null
        )

        val json = StockNewsJson.toJson(listOf(item))
        val parsed = StockNewsJson.fromJson(json)

        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals(item.translatedTitle, result.translatedTitle)
        assertEquals(item.translatedPublishedAtText, result.translatedPublishedAtText)
        assertEquals(item.publishedAt, result.publishedAt)
    }

    @Test
    fun `handles legacy LocalDateTime format gracefully`() {
        // Test that old cached data with LocalDateTime format doesn't crash
        val legacyJson = """
            [{
                "title": "Old News",
                "publishedAtText": "15 sty, 12:00",
                "publishedAt": "2025-01-15T12:00:00",
                "source": "Reuters",
                "url": "https://example.com"
            }]
        """.trimIndent()

        val parsed = StockNewsJson.fromJson(legacyJson)
        
        assertEquals(1, parsed.size)
        val result = parsed.first()
        assertEquals("Old News", result.title)
        // publishedAt should be null for unparseable old format
        // This is graceful degradation per requirement 6A
    }
}
