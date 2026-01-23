package com.example.stocksignal.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.domain.model.AiScoreReason
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiScoreReasonJsonInstrumentedTest {

    @Test
    fun roundTripPreservesReasons() {
        val reasons = listOf(
            AiScoreReason(title = "Momentum", detail = "Trend is positive."),
            AiScoreReason(title = "News", detail = "Headlines are constructive.")
        )

        val json = AiScoreReasonJson.toJson(reasons)
        val parsed = AiScoreReasonJson.fromJson(json)

        assertEquals(reasons, parsed)
    }
}
