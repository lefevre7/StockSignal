package com.example.stocksignal.data.repository

import com.example.stocksignal.domain.model.StockNewsItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class StockNewsJsonTest {

    @Test
    fun `round trip preserves translated fields`() {
        val item = StockNewsItem(
            title = "Tytul",
            publishedAtText = "16 sty, 15:54",
            publishedAt = LocalDateTime.of(2025, 1, 16, 15, 54),
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
    }
}
