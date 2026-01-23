package com.example.stocksignal.data.ai

import com.example.stocksignal.domain.model.AiScoreReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiScoreReasonJsonTest {

    @Test
    fun `round trip preserves reasons`() {
        val reasons = listOf(
            AiScoreReason(title = "Momentum", detail = "Trend is positive."),
            AiScoreReason(title = "News", detail = "Headlines are constructive.")
        )

        val json = AiScoreReasonJson.toJson(reasons)
        val parsed = AiScoreReasonJson.fromJson(json)

        assertEquals(reasons, parsed)
    }

    @Test
    fun `fromJson trims fields`() {
        val raw = "[{\"title\":\"  Title  \",\"detail\":\"  Detail  \"}]"
        val parsed = AiScoreReasonJson.fromJson(raw)

        assertEquals(1, parsed.size)
        assertEquals("Title", parsed.first().title)
        assertEquals("Detail", parsed.first().detail)
        assertTrue(parsed.first().title.isNotBlank())
    }
}
