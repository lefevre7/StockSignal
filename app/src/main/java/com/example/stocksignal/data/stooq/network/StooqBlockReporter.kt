package com.example.stocksignal.data.stooq.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.stocksignal.R
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StooqBlockReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastNotifyAtMillis: Long = 0L

    fun reportBlocked(message: String, blockedUntilMillis: Long?) {
        scope.launch {
            diagnosticsRepository.recordStooqBlocked(message, blockedUntilMillis)
        }
        maybeNotify(message)
    }

    private fun maybeNotify(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAtMillis < NOTIFY_THROTTLE_MS) return
        lastNotifyAtMillis = now
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Network alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Connectivity and data source alerts"
        }
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Stock Signal: Data source blocked")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "stock_signal_network_alerts"
        private const val NOTIFICATION_ID = 4201
        private const val NOTIFY_THROTTLE_MS = 60_000L
    }
}
