package com.example.stocksignal.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.stocksignal.R
import com.example.stocksignal.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WindowRunService : Service() {

    @Inject lateinit var windowRunner: NotificationWindowRunner
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var scheduler: NotificationScheduler
    @Inject lateinit var diagnosticsRepository: NotificationDiagnosticsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJobActive = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val windowId = intent?.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
        val runAtMillis = intent?.getLongExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, -1L) ?: -1L
        if (windowId.isNullOrBlank()) {
            Log.w(TAG, "Missing window ID for run service")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startInForeground(windowId)

        if (!currentJobActive) {
            currentJobActive = true
            serviceScope.launch {
                try {
                    val lastRunAt = diagnosticsRepository
                        .getWindowRunInfo(setOf(windowId))[windowId]
                        ?.lastRunAtMillis ?: 0L
                    if (runAtMillis > 0L && lastRunAt >= runAtMillis - DUPLICATE_RUN_GRACE_MILLIS) {
                        Log.d(TAG, "Skipping window run for $windowId; already ran")
                    } else {
                        val settings = settingsRepository.settingsFlow.first()
                        windowRunner.run(windowId, allowAiGeneration = false)
                        scheduler.scheduleNextWindow(settings, windowId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Window run service failed for $windowId", e)
                } finally {
                    currentJobActive = false
                    stopForegroundSafe()
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(windowId: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Window run",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Running window")
            .setContentText("Window $windowId is running")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Window $windowId is running"))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val TAG = "WindowRunService"
        private const val CHANNEL_ID = "window_run"
        private const val NOTIFICATION_ID = 9210
        private const val DUPLICATE_RUN_GRACE_MILLIS = 60_000L
    }
}
