package com.example.stocksignal.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            logW("Notifications are disabled at the system level; posting anyway for audit.")
        }

        val notificationId = generateNotificationId()
        val title = notificationTitle(events)
        val summary = notificationSummary(events)
        val summaryWithHint = "$summary (swipe to dismiss)"
        val inboxStyle = NotificationCompat.InboxStyle()
        events.take(MAX_LINES).forEach { event ->
            inboxStyle.addLine("${event.ticker} ${event.tier.label} (${event.displayScore})")
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summaryWithHint)
            .setStyle(inboxStyle)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setDeleteIntent(dismissIntent)
            .setGroup(groupKey())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_EVENT)

        val payload = NotificationPayload(
            type = events.first().type,
            ticker = ticker,
            company = events.first().companyName,
            signal = events.first().tier.label,
            score = events.first().displayScore,
            confidence = events.first().displayConfidence ?: events.first().confidence,
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
        logD("Posted notification id=$notificationId with ${events.size} event(s).")
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
            val score = event.displayScore
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
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            runCatching { description = "Scheduled signal alerts and digests" }
            runCatching { enableVibration(true) }
            runCatching { vibrationPattern = longArrayOf(0, 200, 100, 200) }
            val soundUri = runCatching {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }.getOrNull()
            val attributes = runCatching {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }.getOrNull()
            if (soundUri != null && attributes != null) {
                runCatching { setSound(soundUri, attributes) }
            }
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
        const val CHANNEL_ID = "stock_signal_alerts_v2"
        const val EXTRA_EVENT_IDS = "extra_event_ids"
        const val EXTRA_PAYLOAD_JSON = "extra_payload_json"
        private const val MAX_LINES = 5
        private const val TAG = "NotificationPublisher"
    }

    private fun logD(message: String) {
        runCatching { android.util.Log.d(TAG, message) }
    }

    private fun logW(message: String) {
        runCatching { android.util.Log.w(TAG, message) }
    }
}
