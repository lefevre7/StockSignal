package com.example.stocksignal.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.stocksignal.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            NotificationAlarmEntryPoint::class.java
        )
        val settingsRepository = entryPoint.settingsRepository()
        val scheduler = entryPoint.notificationScheduler()
        val diagnosticsRepository = entryPoint.notificationDiagnosticsRepository()
        val backgroundRunPolicy = entryPoint.backgroundRunPolicy()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val type = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE)
                when (type) {
                    NotificationAlarmIntentFactory.TYPE_WINDOW -> {
                        val windowId = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
                        if (windowId.isNullOrBlank()) {
                            Log.w(TAG, "Alarm missing window ID")
                        } else {
                            cancelPreNotifyNotification(appContext, windowId)
                            val settings = settingsRepository.settingsFlow.first()
                            val skipReason = backgroundRunPolicy.windowSkipReason(settings, windowId)
                            if (skipReason != null) {
                                diagnosticsRepository.recordWindowRun(windowId, "skipped", skipReason)
                                scheduler.scheduleNextWindow(settings, windowId)
                                return@launch
                            }
                            val serviceIntent = Intent(appContext, WindowRunService::class.java).apply {
                                putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, windowId)
                                putExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, System.currentTimeMillis())
                            }
                            try {
                                ContextCompat.startForegroundService(appContext, serviceIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to start window service for $windowId", e)
                                diagnosticsRepository.recordWindowRun(
                                    windowId,
                                    "start_failed",
                                    "fgs_start_failed:${e::class.java.simpleName}:${e.message}"
                                )
                                scheduler.scheduleNextWindow(settings, windowId)
                            }
                        }
                    }
                    NotificationAlarmIntentFactory.TYPE_PRE_NOTIFY -> {
                        val windowId = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
                        val runAtMillis = intent.getLongExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, -1L)
                        if (windowId.isNullOrBlank() || runAtMillis <= 0L) {
                            Log.w(TAG, "Pre-notify alarm missing window/run time")
                        } else {
                            postPreNotifyNotification(appContext, windowId, runAtMillis)
                            diagnosticsRepository.recordWindowPreNotifyRun(
                                windowId,
                                "ran",
                                "pre-notify posted for ${formatRunTime(runAtMillis)}"
                            )
                        }
                    }
                    NotificationAlarmIntentFactory.TYPE_ROBOTS -> {
                        val skipReason = backgroundRunPolicy.robotsSkipReason()
                        if (skipReason != null) {
                            val settings = settingsRepository.settingsFlow.first()
                            diagnosticsRepository.recordBackgroundWorkerEvent(
                                worker = "robots_txt",
                                phase = "skipped",
                                key = ROBOTS_WORK_NAME,
                                note = skipReason
                            )
                            scheduler.scheduleRobotsTxtCheck(settings)
                            return@launch
                        }
                        enqueueRobotsWork(appContext, diagnosticsRepository)
                    }
                    NotificationAlarmIntentFactory.TYPE_PREMARKET -> {
                        val windowId = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
                        val sampleIndex = intent.getIntExtra(NotificationAlarmIntentFactory.EXTRA_SAMPLE_INDEX, -1)
                        if (windowId.isNullOrBlank() || sampleIndex < 0) {
                            Log.w(TAG, "Premarket alarm missing window/sample info")
                        } else {
                            val settings = settingsRepository.settingsFlow.first()
                            val skipReason = backgroundRunPolicy.premarketSkipReason(settings, windowId)
                            if (skipReason != null) {
                                diagnosticsRepository.recordBackgroundWorkerEvent(
                                    worker = "premarket_quote",
                                    phase = "skipped",
                                    key = NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex),
                                    note = skipReason
                                )
                                scheduler.schedulePremarketSample(settings, windowId, sampleIndex)
                                return@launch
                            }
                            enqueuePremarketWork(appContext, diagnosticsRepository, windowId, sampleIndex)
                        }
                    }
                    else -> Log.w(TAG, "Unknown alarm type: $type")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Alarm receiver failed", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private suspend fun enqueueRobotsWork(
        context: Context,
        diagnosticsRepository: NotificationDiagnosticsRepository
    ) {
        val request = OneTimeWorkRequestBuilder<RobotsTxtCheckWorker>().build()
        try {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ROBOTS_WORK_NAME, ExistingWorkPolicy.KEEP, request)
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "robots_txt",
                phase = "enqueued",
                key = ROBOTS_WORK_NAME,
                note = "requestId=${request.id}"
            )
        } catch (e: Exception) {
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "robots_txt",
                phase = "enqueue_failed",
                key = ROBOTS_WORK_NAME,
                note = "${e::class.java.simpleName}:${e.message}"
            )
            throw e
        }
    }

    private suspend fun enqueuePremarketWork(
        context: Context,
        diagnosticsRepository: NotificationDiagnosticsRepository,
        windowId: String,
        sampleIndex: Int
    ) {
        val premarketKey = NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex)
        val uniqueWorkName = "$PREMARKET_WORK_PREFIX:$windowId:$sampleIndex"
        val request = OneTimeWorkRequestBuilder<PremarketQuoteWorker>()
            .setInputData(
                workDataOf(
                    PremarketQuoteWorker.KEY_WINDOW_ID to windowId,
                    PremarketQuoteWorker.KEY_SAMPLE_INDEX to sampleIndex
                )
            )
            .addTag(PremarketQuoteWorker.WORK_TAG)
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                request
            )
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "premarket_quote",
                phase = "enqueued",
                key = premarketKey,
                note = "requestId=${request.id}"
            )
        } catch (e: Exception) {
            diagnosticsRepository.recordBackgroundWorkerEvent(
                worker = "premarket_quote",
                phase = "enqueue_failed",
                key = premarketKey,
                note = "${e::class.java.simpleName}:${e.message}"
            )
            throw e
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationAlarmEntryPoint {
        fun settingsRepository(): com.example.stocksignal.data.settings.SettingsRepository
        fun notificationScheduler(): NotificationScheduler
        fun notificationDiagnosticsRepository(): NotificationDiagnosticsRepository
        fun backgroundRunPolicy(): BackgroundStooqRunPolicy
    }

    companion object {
        private const val TAG = "NotificationAlarmReceiver"
        private const val ROBOTS_WORK_NAME = "alarm_robots_check"
        private const val PREMARKET_WORK_PREFIX = "alarm_premarket_quote"
        private const val PRE_NOTIFY_CHANNEL_ID = "window_run"
        private const val PRE_NOTIFY_ID_OFFSET = 9200

        private fun formatRunTime(runAtMillis: Long): String {
            return runCatching {
                java.time.Instant.ofEpochMilli(runAtMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            }.getOrDefault(runAtMillis.toString())
        }

        internal fun preNotifyNotificationId(windowId: String): Int =
            PRE_NOTIFY_ID_OFFSET + windowId.hashCode()

        internal fun postPreNotifyNotification(
            context: Context,
            windowId: String,
            runAtMillis: Long
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                PRE_NOTIFY_CHANNEL_ID,
                "Window run",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)

            val runAt = java.time.Instant.ofEpochMilli(runAtMillis)
                .atZone(java.time.ZoneId.systemDefault())
            val minutes = java.time.Duration.between(
                java.time.Instant.now(), runAt.toInstant()
            ).toMinutes().coerceAtLeast(0)
            val timeText = formatRunTime(runAtMillis)
            val contentText = "Runs in ${minutes}m at $timeText"

            val notification = NotificationCompat.Builder(context, PRE_NOTIFY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Window $windowId scheduled")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build()

            manager.notify(preNotifyNotificationId(windowId), notification)
        }

        internal fun cancelPreNotifyNotification(context: Context, windowId: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(preNotifyNotificationId(windowId))
        }
    }
}
