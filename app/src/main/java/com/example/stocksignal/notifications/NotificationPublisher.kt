package com.example.stocksignal.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.stocksignal.MainActivity
import com.example.stocksignal.R
import com.example.stocksignal.domain.model.NotificationEvent
import com.example.stocksignal.domain.model.NotificationPayload
import com.example.stocksignal.domain.model.NotificationPayloadJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun postDigest(events: List<NotificationEvent>): Int {
        if (events.isEmpty()) return 0
        ensureChannel()

        val notificationId = generateNotificationId()
        val title = notificationTitle(events)
        val summary = notificationSummary(events)
        val inboxStyle = NotificationCompat.InboxStyle()
        events.take(MAX_LINES).forEach { event ->
            inboxStyle.addLine("${event.ticker} ${event.tier.label} (${event.score})")
        }
        if (events.size > MAX_LINES) {
            inboxStyle.setSummaryText("+${events.size - MAX_LINES} more")
        }

        val ticker = events.first().ticker
        val deepLink = events.first().deepLink
        val contentIntent = NotificationIntentFactory.contentIntent(
            context,
            ticker,
            deepLink,
            events.first().id
        )
        val eventIds = events.map { it.id }.toTypedArray()
        val dismissIntent = NotificationIntentFactory.dismissIntent(context, notificationId, eventIds)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(inboxStyle)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setDeleteIntent(dismissIntent)
            .setGroup(groupKey())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val payload = NotificationPayload(
            type = events.first().type,
            ticker = ticker,
            company = events.first().companyName,
            signal = events.first().tier.label,
            score = events.first().score,
            confidence = events.first().confidence,
            price = events.first().price,
            percentChange = events.first().percentChange,
            time = events.first().generatedAt,
            deepLink = deepLink,
            source = events.first().source
        )
        builder.addExtras(
            android.os.Bundle().apply {
                putString(EXTRA_PAYLOAD_JSON, NotificationPayloadJson.toJson(payload))
                putStringArray(EXTRA_EVENT_IDS, eventIds)
            }
        )

        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "View",
            contentIntent
        )
        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "Dismiss",
            NotificationIntentFactory.dismissIntent(context, notificationId, eventIds)
        )

        if (events.size == 1 && events.first().type == com.example.stocksignal.domain.model.NotificationEventType.MARKET_MOVER) {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "Add to Watchlist",
                NotificationIntentFactory.addToWatchlistIntent(
                    context,
                    notificationId,
                    events.first().ticker,
                    events.first().companyName
                )
            )
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        return notificationId
    }

    private fun notificationTitle(events: List<NotificationEvent>): String {
        return if (events.size == 1) {
            "${events.first().ticker} ${events.first().tier.label}"
        } else {
            "${events.size} new signals"
        }
    }

    private fun notificationSummary(events: List<NotificationEvent>): String {
        if (events.size == 1) {
            val event = events.first()
            val score = event.score
            val sign = if (score > 0) "+" else if (score < 0) "-" else ""
            return "${event.tier.summary} • $sign${abs(score)}"
        }
        val labels = events.take(3).joinToString(", ") { "${it.ticker} ${it.tier.label}" }
        return labels
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Signal alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Scheduled signal alerts and digests"
        }
        manager.createNotificationChannel(channel)
    }

    private fun generateNotificationId(): Int {
        return (System.currentTimeMillis() % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }

    private fun groupKey(): String {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return "signals_$date"
    }

    companion object {
        const val CHANNEL_ID = "stock_signal_alerts"
        const val EXTRA_EVENT_IDS = "extra_event_ids"
        const val EXTRA_PAYLOAD_JSON = "extra_payload_json"
        private const val MAX_LINES = 5
    }
}
