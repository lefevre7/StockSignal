package com.example.stocksignal.ui.signals

import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.domain.model.AiScoreReason
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignalsFeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val signalsRepository = mockk<SignalsRepository>(relaxed = true)

    @Test
    fun `ui state sorts latest events first and dismiss actions delegate`() = runTest(mainDispatcherRule.dispatcher) {
        val older = sampleEvent("evt-old", LocalDateTime.of(2026, 3, 31, 9, 0))
        val newer = sampleEvent("evt-new", LocalDateTime.of(2026, 3, 31, 10, 0))
        every { signalsRepository.eventsFlow } returns MutableStateFlow(listOf(older, newer))

        val viewModel = SignalsFeedViewModel(signalsRepository)
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("evt-new", "evt-old"), viewModel.uiState.value.events.map { it.id })

        viewModel.dismissEvent("evt-new")
        viewModel.undoDismissEvent("evt-new")
        advanceUntilIdle()

        coVerify { signalsRepository.dismissEvent("evt-new") }
        coVerify { signalsRepository.undoDismissEvent("evt-new") }

        collector.cancel()
    }

    private fun sampleEvent(id: String, generatedAt: LocalDateTime) = NotificationEvent(
        id = id,
        type = NotificationEventType.WATCHLIST_SIGNAL,
        ticker = "AAPL",
        companyName = "Apple Inc.",
        score = 61,
        averageScore = 58,
        modeScore = 60,
        confidence = 74,
        aiScore = 76,
        aiConfidence = 85,
        aiSummary = "AI favors continuation",
        aiReasons = listOf(AiScoreReason("Trend", "Trend still slopes upward.")),
        price = 186.42,
        percentChange = 2.31,
        generatedAt = generatedAt,
        notifiedAt = null,
        deepLink = "stocksignal://stock/AAPL?eventId=$id",
        source = "watchlist",
        delivered = true,
        reasons = listOf(
            SignalReason(
                id = "macd",
                title = "MACD bullish",
                explanation = "MACD stayed above signal.",
                impactScore = 14,
                model = "macd"
            )
        )
    )
}
