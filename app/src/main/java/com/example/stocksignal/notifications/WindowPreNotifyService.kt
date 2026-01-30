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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.stocksignal.R
import com.example.stocksignal.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WindowPreNotifyService : Service() {

    @Inject lateinit var windowRunner: NotificationWindowRunner
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var scheduler: NotificationScheduler
    @Inject lateinit var diagnosticsRepository: NotificationDiagnosticsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentJobActive = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val windowId = intent?.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
        val runAtMillis = intent?.getLongExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, -1L) ?: -1L
        if (windowId.isNullOrBlank() || runAtMillis <= 0L) {
            Log.w(TAG, "Missing window/run time for pre-notify service")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startInForeground(windowId, runAtMillis)
        val delayMs = (runAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        acquireWakeLock(delayMs)
        recordPreNotify(
            windowId,
            "started",
            "runs at ${formatRunTime(runAtMillis)} (delay=${delayMs / 1000}s)"
        )

        if (!currentJobActive) {
            currentJobActive = true
            serviceScope.launch {
                try {
                    if (delayMs > 0L) {
                        delay(delayMs)
                    }
                    val lastRunAt = diagnosticsRepository
                        .getWindowRunInfo(setOf(windowId))[windowId]
                        ?.lastRunAtMillis ?: 0L
                    if (lastRunAt >= runAtMillis - DUPLICATE_RUN_GRACE_MILLIS) {
                        Log.d(TAG, "Skipping pre-notify run for $windowId; already ran")
                        recordPreNotify(windowId, "skipped", "already ran")
                    } else {
                        val settings = settingsRepository.settingsFlow.first()
                        val outcome = windowRunner.run(windowId)
                        val result = when (outcome) {
                            NotificationWindowRunner.RunOutcome.SUCCESS -> "ran"
                            NotificationWindowRunner.RunOutcome.RETRY -> "retry"
                            NotificationWindowRunner.RunOutcome.FAILURE -> "failed"
                        }
                        recordPreNotify(windowId, result, null)
                        scheduler.scheduleNextWindow(settings, windowId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Pre-notify service failed for $windowId", e)
                    recordPreNotify(windowId, "error", e.message)
                } finally {
                    currentJobActive = false
                    releaseWakeLock()
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

    private fun startInForeground(windowId: String, runAtMillis: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Window pre-notify",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val runAt = Instant.ofEpochMilli(runAtMillis).atZone(ZoneId.systemDefault())
        val minutes = Duration.between(Instant.now(), runAt.toInstant()).toMinutes().coerceAtLeast(0)
        val timeText = runAt.format(TIME_FORMATTER)
        val contentText = "Runs in ${minutes}m at $timeText"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Window ${windowId} scheduled")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
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

    private fun acquireWakeLock(delayMs: Long) {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG:pre_notify"
        )
        val timeout = (delayMs + WAKELOCK_GRACE_MILLIS).coerceAtLeast(WAKELOCK_GRACE_MILLIS)
        lock.acquire(timeout)
        wakeLock = lock
        Log.d(TAG, "Wake lock acquired for ${timeout}ms")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    private fun recordPreNotify(windowId: String, result: String, reason: String?) {
        serviceScope.launch {
            runCatching { diagnosticsRepository.recordWindowPreNotifyRun(windowId, result, reason) }
        }
    }

    private fun formatRunTime(runAtMillis: Long): String {
        return try {
            Instant.ofEpochMilli(runAtMillis)
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMATTER)
        } catch (_: Exception) {
            runAtMillis.toString()
        }
    }

    companion object {
        private const val TAG = "WindowPreNotifyService"
        private const val CHANNEL_ID = "window_pre_notify"
        private const val NOTIFICATION_ID = 9200
        private const val DUPLICATE_RUN_GRACE_MILLIS = 60_000L
        private const val WAKELOCK_GRACE_MILLIS = 30_000L
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
    }
}
