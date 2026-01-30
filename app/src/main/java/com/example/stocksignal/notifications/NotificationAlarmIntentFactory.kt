package com.example.stocksignal.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object NotificationAlarmIntentFactory {
    const val EXTRA_TYPE = "extra_alarm_type"
    const val EXTRA_WINDOW_ID = "extra_window_id"
    const val EXTRA_SAMPLE_INDEX = "extra_sample_index"
    const val EXTRA_RUN_AT_MILLIS = "extra_run_at_millis"

    const val TYPE_WINDOW = "window"
    const val TYPE_PRE_NOTIFY = "pre_notify"
    const val TYPE_ROBOTS = "robots"
    const val TYPE_PREMARKET = "premarket"

    fun windowPendingIntent(context: Context, windowId: String): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_WINDOW)
            putExtra(EXTRA_WINDOW_ID, windowId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor("window:$windowId"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun robotsPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_ROBOTS)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor("robots"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun preNotifyPendingIntent(
        context: Context,
        windowId: String,
        runAtMillis: Long
    ): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_PRE_NOTIFY)
            putExtra(EXTRA_WINDOW_ID, windowId)
            putExtra(EXTRA_RUN_AT_MILLIS, runAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor("pre_notify:$windowId"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun premarketPendingIntent(context: Context, windowId: String, sampleIndex: Int): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, TYPE_PREMARKET)
            putExtra(EXTRA_WINDOW_ID, windowId)
            putExtra(EXTRA_SAMPLE_INDEX, sampleIndex)
        }
        val key = premarketKey(windowId, sampleIndex)
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor("premarket:$key"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun premarketKey(windowId: String, sampleIndex: Int): String = "$windowId:$sampleIndex"

    private fun requestCodeFor(key: String): Int = key.hashCode()
}
