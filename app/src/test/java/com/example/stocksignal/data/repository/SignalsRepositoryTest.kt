package com.example.stocksignal.data.repository

import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import com.example.stocksignal.data.local.repository.SignalEventsRepository
import com.example.stocksignal.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SignalsRepositoryTest {

    private val signalEventsRepository = mockk<SignalEventsRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val repository = SignalsRepository(signalEventsRepository, settingsRepository)

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
            deepLink = "stocksignal://stock/AAPL",
            reasons = emptyList(),
            avgScore = 50,
            modeScore = null,
            modelScores = null
        )
    }
}
