package com.example.stocksignal.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationEventType
import com.example.stocksignal.domain.model.NotificationPayloadJson
import com.example.stocksignal.domain.model.SignalReason
import com.example.stocksignal.domain.model.SignalTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class NotificationPublisherInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )

    @Test
    fun postsSingleNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        val event = sampleEvent(
            id = "evt_single",
            ticker = "AAPL",
            score = 80,
            type = NotificationEventType.WATCHLIST_SIGNAL
        )
        val (notificationId, notification) = postAndFetch(context, listOf(event))

        val channel = manager.getNotificationChannel(NotificationPublisher.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        assertSingleNotificationContent(notification, event)
        assertExtras(notification, listOf(event))
        assertGroupKey(notification)

        Thread.sleep(5_000)
        manager.cancel(notificationId)
    }

    @Test
    fun postsSingleMarketMoverAddsWatchlistAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        val event = sampleEvent(
            id = "evt_mover",
            ticker = "NVDA",
            score = 70,
            type = NotificationEventType.MARKET_MOVER
        )
        val (notificationId, notification) = postAndFetch(context, listOf(event))

        val actions = notification.actions?.mapNotNull { it.title?.toString() }.orEmpty()
        assertTrue(actions.contains("Add to Watchlist"))

        Thread.sleep(5_000)
        manager.cancel(notificationId)
    }

    @Test
    fun postsGroupedNotificationWithTwoEvents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        val events = listOf(
            sampleEvent(id = "evt_a", ticker = "AAPL", score = 80),
            sampleEvent(id = "evt_b", ticker = "MSFT", score = 45)
        )
        val (notificationId, notification) = postAndFetch(context, events)

        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val summary = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertEquals("2 new signals", title)
        assertEquals(expectedGroupSummary(events), summary)

        val lines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES).orEmpty()
        assertEquals(2, lines.size)
        assertEquals(expectedLine(events[0]), lines[0].toString())
        assertEquals(expectedLine(events[1]), lines[1].toString())

        assertExtras(notification, events)
        assertGroupKey(notification)

        Thread.sleep(5_000)
        manager.cancel(notificationId)
    }

    @Test
    fun postsGroupedNotificationWithSixEvents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        assumeTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        val events = listOf(
            sampleEvent(id = "evt_1", ticker = "AAPL", score = 80, type = NotificationEventType.WATCHLIST_SIGNAL),
            sampleEvent(id = "evt_2", ticker = "MSFT", score = 45, type = NotificationEventType.WATCHLIST_SIGNAL),
            sampleEvent(id = "evt_3", ticker = "TSLA", score = -40, type = NotificationEventType.MARKET_MOVER),
            sampleEvent(id = "evt_4", ticker = "AMZN", score = 35, type = NotificationEventType.MARKET_MOVER),
            sampleEvent(id = "evt_5", ticker = "GOOG", score = 65, type = NotificationEventType.WATCHLIST_SIGNAL),
            sampleEvent(id = "evt_6", ticker = "NVDA", score = 75, type = NotificationEventType.WATCHLIST_SIGNAL)
        )
        val (notificationId, notification) = postAndFetch(context, events)

        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val summary = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        assertEquals("6 new signals", title)
        assertEquals(expectedGroupSummary(events), summary)

        val lines = notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES).orEmpty()
        assertEquals(5, lines.size)
        val summaryText = notification.extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT)?.toString()
        assertEquals("+1 more", summaryText)

        assertExtras(notification, events)
        assertGroupKey(notification)

        Thread.sleep(5_000)
        manager.cancel(notificationId)
    }

    private fun postAndFetch(
        context: android.content.Context,
        events: List<NotificationEvent>
    ): Pair<Int, Notification> {
        val publisher = NotificationPublisher(context)
        val notificationId = publisher.postDigest(events)
        assertTrue(notificationId != 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = waitForNotification(manager, notificationId)
            assertNotNull(notification)
            return notificationId to requireNotNull(notification)
        }
        error("Active notifications not supported on this API level.")
    }

    private fun waitForNotification(
        manager: NotificationManager,
        notificationId: Int
    ): Notification? {
        repeat(20) {
            val active = manager.activeNotifications
            val notification = active.firstOrNull { it.id == notificationId }?.notification
            if (notification != null) {
                return notification
            }
            Thread.sleep(100)
        }
        return null
    }

    private fun assertSingleNotificationContent(notification: Notification, event: NotificationEvent) {
        val title = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
        val summary = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()
        val tier = SignalTier.fromScore(event.score)
        val expectedSummary = "${tier.summary} • +${event.score}"
        assertEquals("${event.ticker} ${tier.label}", title)
        assertEquals(expectedSummary, summary)
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
