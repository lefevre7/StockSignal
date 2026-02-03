package com.example.stocksignal.notifications

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.NotificationPayloadJson
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class NotificationPublisherTest {

    @Test
    fun `posts single watchlist notification with expected content`() {
        val event = sampleEvent(
            id = "evt_single",
            ticker = "AAPL",
            score = 80,
            type = NotificationEventType.WATCHLIST_SIGNAL
        )
        val (notificationId, notification) = postAndFetch(listOf(event))

        assertSingleNotificationContent(notification, event)
        assertExtras(notification, listOf(event))

        val actions = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
        assertTrue(actions.contains("View"))
        assertTrue(actions.contains("Dismiss").not())
    }

    @Test
    fun `single market mover adds watchlist action`() {
        val event = sampleEvent(
            id = "evt_mover",
            ticker = "NVDA",
            score = 70,
            type = NotificationEventType.MARKET_MOVER
        )
        val (_, notification) = postAndFetch(listOf(event))

        val actions = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
        assertTrue(actions.contains("Add to Watchlist"))
    }

    @Test
    fun `group notification with two events shows summary lines`() {
        val events = listOf(
            sampleEvent(id = "evt_a", ticker = "AAPL", score = 80),
            sampleEvent(id = "evt_b", ticker = "MSFT", score = 45)
        )
        val (_, notification) = postAndFetch(events)

        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val summary = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertEquals("2 new signals", title)
        assertEquals(summaryWithHint(expectedGroupSummary(events)), summary)

        val lines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES).orEmpty()
        assertEquals(2, lines.size)
        assertEquals(expectedLine(events[0]), lines[0].toString())
        assertEquals(expectedLine(events[1]), lines[1].toString())

        assertGroupKey(notification)
        assertExtras(notification, events)

        val actions = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
        assertTrue(actions.contains("View"))
        assertTrue(actions.contains("Dismiss").not())
    }

    @Test
    fun `group notification with six events shows more summary`() {
        val events = listOf(
            sampleEvent(id = "evt_1", ticker = "AAPL", score = 80, type = NotificationEventType.WATCHLIST_SIGNAL),
            sampleEvent(id = "evt_2", ticker = "MSFT", score = 45, type = NotificationEventType.WATCHLIST_SIGNAL),
            sampleEvent(id = "evt_3", ticker = "TSLA", score = -40, type = NotificationEventType.MARKET_MOVER),
            sampleEvent(id = "evt_4", ticker = "AMZN", score = 35, type = NotificationEventType.MARKET_MOVER),
            sampleEvent(id = "evt_5", ticker = "GOOG", score = 65, type = NotificationEventType.WATCHLIST_SIGNAL),
            sampleEvent(id = "evt_6", ticker = "NVDA", score = 75, type = NotificationEventType.WATCHLIST_SIGNAL)
        )
        val (_, notification) = postAndFetch(events)

        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val summary = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertEquals("6 new signals", title)
        assertEquals(summaryWithHint(expectedGroupSummary(events)), summary)

        val lines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES).orEmpty()
        assertEquals(5, lines.size)
        assertEquals(expectedLine(events[0]), lines[0].toString())
        assertEquals(expectedLine(events[4]), lines[4].toString())

        val summaryText = notification.extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT)?.toString()
        assertEquals("+1 more", summaryText)

        assertGroupKey(notification)
        assertExtras(notification, events)
    }

    @Test
    fun `posts notification with high importance channel`() {
        val event = sampleEvent(id = "evt_channel", ticker = "TEST", score = 80)
        val (notificationId, notification) = postAndFetch(listOf(event))

        val manager = RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(NotificationPublisher.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertNotNull(channel.vibrationPattern)
        channel.sound?.let { sound ->
            assertTrue(sound.toString().isNotBlank())
        }

        val shadowManager = Shadows.shadowOf(manager)
        assertNotNull(shadowManager.getNotification(notificationId))
        assertGroupKey(notification)
    }

    private fun postAndFetch(events: List<NotificationEvent>): Pair<Int, Notification> {
        val context = RuntimeEnvironment.getApplication()
        val publisher = NotificationPublisher(context)
        val notificationId = publisher.postDigest(events)
        assertTrue(notificationId != 0)

        val manager = context.getSystemService(NotificationManager::class.java)
        val shadowManager = Shadows.shadowOf(manager)
        val notification = shadowManager.getNotification(notificationId)
        assertNotNull(notification)
        return notificationId to notification
    }

    private fun assertSingleNotificationContent(notification: Notification, event: NotificationEvent) {
        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val summary = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        val tier = SignalTier.fromScore(event.score)
        val expectedSummary = "${tier.summary} • +${event.score}"
        assertEquals("${event.ticker} ${tier.label}", title)
        assertEquals(summaryWithHint(expectedSummary), summary)
        assertGroupKey(notification)
    }

    private fun assertExtras(notification: Notification, events: List<NotificationEvent>) {
        val payloadJson = notification.extras.getString(NotificationPublisher.EXTRA_PAYLOAD_JSON)
        assertNotNull(payloadJson)
        val payload = NotificationPayloadJson.fromJson(payloadJson ?: "")
        assertNotNull(payload)
        assertEquals(events.first().ticker, payload?.ticker)
        assertEquals(events.first().type, payload?.type)

        val eventIds = notification.extras.getStringArray(NotificationPublisher.EXTRA_EVENT_IDS).orEmpty()
        assertEquals(events.size, eventIds.size)
        assertTrue(events.map { it.id }.all { eventIds.contains(it) })
    }

    private fun assertGroupKey(notification: Notification) {
        val group = notification.group ?: ""
        assertTrue(group.startsWith("signals_"))
    }

    private fun expectedGroupSummary(events: List<NotificationEvent>): String {
        return events.take(3).joinToString(", ") { "${it.ticker} ${it.tier.label}" }
    }

    private fun summaryWithHint(summary: String): String {
        return "$summary (swipe to dismiss)"
    }

    private fun expectedLine(event: NotificationEvent): String {
        return "${event.ticker} ${event.tier.label} (${event.score})"
    }

    private fun sampleEvent(
        id: String,
        ticker: String,
        score: Int,
        type: NotificationEventType = NotificationEventType.WATCHLIST_SIGNAL
    ): NotificationEvent {
        val now = LocalDateTime.now()
        return NotificationEvent(
            id = id,
            type = type,
            ticker = ticker,
            companyName = "Test Co",
            score = score,
            averageScore = score,
            modeScore = score,
            confidence = 85,
            price = 123.45,
            percentChange = 1.23,
            generatedAt = now,
            notifiedAt = null,
            deepLink = "stocksignal://stock/$ticker",
            source = "test",
            delivered = false,
            reasons = listOf(
                SignalReason(
                    id = "reason_test",
                    title = "Test reason",
                    explanation = "Test reason",
                    impactScore = 0,
                    model = "test"
                )
            )
        )
    }
}
