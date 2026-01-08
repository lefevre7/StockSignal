package com.example.stocksignal.notifications

import android.app.NotificationManager
import android.content.Context
import com.example.stocksignal.data.local.entity.NotificationStateEntity
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.repository.NotificationStateRepository
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SignalsRepository
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.domain.model.ChartRange
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NotificationQueueProcessorTest {

    private val context = mockk<Context>(relaxed = true)
    private val notificationStateRepository = mockk<NotificationStateRepository>()
    private val signalsRepository = mockk<SignalsRepository>(relaxed = true)
    private val watchlistRepository = mockk<WatchlistRepository>(relaxed = true)
    private val publisher = mockk<NotificationPublisher>()

    private val processor = NotificationQueueProcessor(
        context = context,
        notificationStateRepository = notificationStateRepository,
        signalsRepository = signalsRepository,
        watchlistRepository = watchlistRepository,
        publisher = publisher
    )

    @Test
    fun `queues when active notification present`() = runTest {
        stubNotificationManager()
        val now = LocalDateTime.now()
        val state = NotificationStateEntity(
            lastActiveNotificationId = 42,
            lastActiveAt = now,
            dismissed = false,
            queuedEventIds = emptyList(),
            notificationCounts = emptyMap(),
            lastResetAt = now
        )
        coEvery { notificationStateRepository.getState() } returns state
        val stateSlot = slot<NotificationStateEntity>()
        coEvery { notificationStateRepository.upsert(capture(stateSlot)) } just runs
        coEvery { signalsRepository.eventsByIds(any()) } returns emptyList()
        every { publisher.postDigest(any()) } returns 0

        val event = sampleEvent(id = "evt_1")
        processor.processCandidates(listOf(event), defaultSettings())

        val updated = stateSlot.captured
        assertTrue(updated.queuedEventIds.contains("evt_1"))
        assertEquals(42, updated.lastActiveNotificationId)
        verify(exactly = 0) { publisher.postDigest(any()) }
    }

    @Test
    fun `posts digest when eligible`() = runTest {
        stubNotificationManager()
        val now = LocalDateTime.now()
        val state = NotificationStateEntity(
            lastActiveNotificationId = null,
            lastActiveAt = null,
            dismissed = true,
            queuedEventIds = emptyList(),
            notificationCounts = emptyMap(),
            lastResetAt = now
        )
        coEvery { notificationStateRepository.getState() } returns state
        val stateSlot = slot<NotificationStateEntity>()
        coEvery { notificationStateRepository.upsert(capture(stateSlot)) } just runs
        coEvery { signalsRepository.eventsByIds(any()) } returns emptyList()
        coEvery { signalsRepository.markNotified(any(), any()) } just runs
        every { publisher.postDigest(any()) } returns 777

        val event = sampleEvent(id = "evt_2")
        processor.processCandidates(listOf(event), defaultSettings())

        coVerify { signalsRepository.markNotified(listOf("evt_2"), any()) }
        verify { publisher.postDigest(match { it.size == 1 && it.first().id == "evt_2" }) }
        val updated = stateSlot.captured
        assertEquals(777, updated.lastActiveNotificationId)
        assertEquals(false, updated.dismissed)
        assertTrue(updated.queuedEventIds.isEmpty())
    }

    @Test
    fun `queues when per-stock quiet hours are active`() = runTest {
        stubNotificationManager()
        val now = LocalDateTime.now()
        val state = NotificationStateEntity(
            lastActiveNotificationId = null,
            lastActiveAt = null,
            dismissed = true,
            queuedEventIds = emptyList(),
            notificationCounts = emptyMap(),
            lastResetAt = now
        )
        coEvery { notificationStateRepository.getState() } returns state
        val stateSlot = slot<NotificationStateEntity>()
        coEvery { notificationStateRepository.upsert(capture(stateSlot)) } just runs
        coEvery { signalsRepository.eventsByIds(any()) } returns emptyList()

        val quietStart = LocalTime.now().minusMinutes(5)
        val quietEnd = LocalTime.now().plusMinutes(5)
        coEvery { watchlistRepository.getBySymbol("AAPL") } returns watchlistItem(
            symbol = "AAPL",
            quietStart = quietStart,
            quietEnd = quietEnd
        )

        val event = sampleEvent(id = "evt_3", ticker = "AAPL")
        processor.processCandidates(listOf(event), defaultSettings())

        verify(exactly = 0) { publisher.postDigest(any()) }
        val updated = stateSlot.captured
        assertTrue(updated.queuedEventIds.contains("evt_3"))
    }

    private fun defaultSettings(): AppSettings {
        return AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(
                NotificationType.WATCHLIST,
                NotificationType.MARKET_MOVERS,
                NotificationType.DIGESTS
            ),
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
            scheduleWindows = emptyList(),
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.ONE_DAY,
            selectedMarketMoverRange = MarketMoverRange.ONE_DAY,
            immediatePostsEnabled = false,
            onboardingCompleted = true
        )
    }

    private fun sampleEvent(
        id: String,
        ticker: String = "MSFT"
    ): NotificationEvent {
        return NotificationEvent(
            id = id,
            type = NotificationEventType.WATCHLIST_SIGNAL,
            ticker = ticker,
            companyName = "Test Co",
            score = 65,
            averageScore = 60,
            modeScore = 65,
            confidence = 75,
            price = 123.45,
            percentChange = 2.5,
            generatedAt = LocalDateTime.now(),
            notifiedAt = null,
            deepLink = "stocksignal://stock/$ticker",
            source = "local",
            delivered = false,
            reasons = emptyList()
        )
    }

    private fun watchlistItem(
        symbol: String,
        quietStart: LocalTime,
        quietEnd: LocalTime
    ): WatchlistItemEntity {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return WatchlistItemEntity(
            symbol = symbol,
            companyName = symbol,
            exchange = null,
            addedAt = LocalDateTime.now(),
            alertEnabled = true,
            minScoreForNotify = 60,
            quietHoursStart = quietStart.format(formatter),
            quietHoursEnd = quietEnd.format(formatter),
            snoozedUntil = null,
            lastSignalScore = null,
            lastSignalLabel = null,
            lastSignalConfidence = null,
            lastSignalTime = null,
            notes = null,
            sortOrder = 0,
            tags = emptyList(),
            muteMarketMovers = false,
            lastNotifiedAt = null
        )
    }

    private fun stubNotificationManager() {
        val manager = mockk<NotificationManager>()
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager
        every { manager.activeNotifications } returns emptyArray()
    }
}
