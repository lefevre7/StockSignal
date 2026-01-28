package com.example.stocksignal.data.repository

import com.example.stocksignal.data.ai.AiScoreResult
import com.example.stocksignal.data.ai.AiSignalScorer
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import com.example.stocksignal.data.local.repository.SignalEventsRepository
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.domain.model.AiScoreReason
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.PriceCandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.DayOfWeek

class SignalsRepositoryTest {

    private val signalEventsRepository = mockk<SignalEventsRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val aiSignalScorer = mockk<AiSignalScorer>(relaxed = true)
    private val repository = SignalsRepository(signalEventsRepository, settingsRepository, aiSignalScorer)

    @Test
    fun `cooldown returns true when recent event exists`() = runTest {
        val now = LocalDateTime.now()
        val recent = sampleEvent(now.minusHours(2))
        coEvery { signalEventsRepository.getLatestForTickerAndLabel("AAPL", "Buy") } returns recent

        val result = repository.isInCooldown("AAPL", "Buy", now)

        assertTrue(result)
    }

    @Test
    fun `cooldown returns false when event is old`() = runTest {
        val now = LocalDateTime.now()
        val old = sampleEvent(now.minusHours(30))
        coEvery { signalEventsRepository.getLatestForTickerAndLabel("AAPL", "Buy") } returns old

        val result = repository.isInCooldown("AAPL", "Buy", now)

        assertFalse(result)
    }

    @Test
    fun `cooldown returns false when no prior event`() = runTest {
        val now = LocalDateTime.now()
        coEvery { signalEventsRepository.getLatestForTickerAndLabel("AAPL", "Buy") } returns null

        val result = repository.isInCooldown("AAPL", "Buy", now)

        assertFalse(result)
    }

    @Test
    fun `computeSignal merges ai result`() = runTest {
        val now = LocalDateTime.of(2024, 2, 1, 12, 0)
        val candles = sampleCandles(now, 30)
        everySettings()
        val aiReasons = listOf(
            AiScoreReason(title = "Momentum", detail = "Uptrend across the range.")
        )
        val aiResult = AiScoreResult(
            score = 72,
            confidence = 64,
            summary = "Momentum is positive. Volatility is moderate. News tone is constructive.",
            reasons = aiReasons
        )
        coEvery { aiSignalScorer.score(any(), any(), any(), any(), any(), any()) } returns aiResult

        val result = repository.computeSignal("AAPL", candles, ChartRange.ONE_DAY, null)

        assertNotNull(result)
        assertEquals(72, result!!.aiScore)
        assertEquals(64, result.aiConfidence)
        assertEquals(aiResult.summary, result.aiSummary)
        assertEquals(aiReasons, result.aiReasons)
        assertEquals(72, result.displayScore)
    }

    @Test
    fun `computeSignal falls back to rule-based when ai is null`() = runTest {
        val now = LocalDateTime.of(2024, 3, 1, 12, 0)
        val candles = sampleCandles(now, 30)
        everySettings()
        coEvery { aiSignalScorer.score(any(), any(), any(), any(), any(), any()) } returns null

        val result = repository.computeSignal("MSFT", candles, ChartRange.ONE_DAY, null)

        assertNotNull(result)
        assertNull(result!!.aiScore)
        assertNull(result.aiConfidence)
        assertNull(result.aiSummary)
        assertTrue(result.aiReasons.isEmpty())
    }

    private fun sampleEvent(time: LocalDateTime): GlobalSignalEventEntity {
        return GlobalSignalEventEntity(
            id = "evt_1",
            type = "watchlist_signal",
            ticker = "AAPL",
            score = 50,
            label = "Buy",
            confidence = 70,
            percentChange = null,
            price = null,
            generatedAt = time,
            notifiedAt = null,
            source = "local",
            delivered = false,
            dismissed = false,
            deepLink = "stocksignal://stock/AAPL",
            reasons = emptyList(),
            avgScore = 50,
            modeScore = null,
            modelScores = null
        )
    }

    private fun sampleCandles(start: LocalDateTime, count: Int): List<PriceCandle> {
        return (0 until count).map { index ->
            val time = start.plusMinutes(index * 5L)
            PriceCandle(
                time = time,
                open = 100.0 + index,
                high = 101.5 + index,
                low = 99.5 + index,
                close = 100.5 + index,
                volume = 1_000L + (index * 10L)
            )
        }
    }

    private fun everySettings() {
        every { settingsRepository.settingsFlow } returns flowOf(defaultSettings())
    }

    private fun defaultSettings(): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.ONE_PER_DAY,
            notificationTypes = setOf(NotificationType.WATCHLIST),
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
            scheduleWindows = emptyList<ScheduleWindow>(),
            weeklyDay = DayOfWeek.MONDAY,
            snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.ONE_DAY,
            immediatePostsEnabled = false,
            offlineTranslationEnabled = true,
            onboardingCompleted = true,
            holdingPeriod = HoldingPeriod.DAYS
        )
    }
}
