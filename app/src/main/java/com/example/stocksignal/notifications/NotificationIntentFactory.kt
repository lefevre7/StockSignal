package com.example.stocksignal.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.stocksignal.MainActivity

object NotificationIntentFactory {

    fun contentIntent(
        context: Context,
        ticker: String,
        deepLink: String?,
        eventId: String?
    ): PendingIntent {
        val baseLink = deepLink ?: "stocksignal://stock/$ticker"
        val uri = if (!eventId.isNullOrBlank()) {
            baseLink.toUri().buildUpon().appendQueryParameter("eventId", eventId).build()
        } else {
            baseLink.toUri()
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            putExtra(EXTRA_TICKER, ticker)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            ticker.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun dismissIntent(context: Context, notificationId: Int, eventIds: Array<String>): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_EVENT_IDS, eventIds)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun addToWatchlistIntent(
        context: Context,
        notificationId: Int,
        ticker: String,
        companyName: String?
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_ADD_WATCHLIST
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationActionReceiver.EXTRA_TICKER, ticker)
            putExtra(NotificationActionReceiver.EXTRA_COMPANY, companyName)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val EXTRA_TICKER = "extra_ticker"
}
