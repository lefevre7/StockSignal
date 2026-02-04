package com.example.stocksignal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
        val robotsRunner = entryPoint.robotsTxtCheckRunner()
        val premarketRunner = entryPoint.premarketQuoteRunner()
        val diagnosticsRepository = entryPoint.notificationDiagnosticsRepository()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val type = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE)
                val settings = settingsRepository.settingsFlow.first()
                when (type) {
                    NotificationAlarmIntentFactory.TYPE_WINDOW -> {
                        val windowId = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
                        if (windowId.isNullOrBlank()) {
                            Log.w(TAG, "Alarm missing window ID")
                        } else {
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
                            val serviceIntent = Intent(appContext, WindowPreNotifyService::class.java).apply {
                                putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, windowId)
                                putExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, runAtMillis)
                            }
                            try {
                                ContextCompat.startForegroundService(appContext, serviceIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to start pre-notify service for $windowId", e)
                                diagnosticsRepository.recordWindowPreNotifyRun(
                                    windowId,
                                    "start_failed",
                                    "fgs_start_failed:${e::class.java.simpleName}:${e.message}"
                                )
                                scheduler.scheduleNextWindow(settings, windowId)
                            }
                        }
                    }
                    NotificationAlarmIntentFactory.TYPE_ROBOTS -> {
                        robotsRunner.run()
                        scheduler.scheduleRobotsTxtCheck(settings)
                    }
                    NotificationAlarmIntentFactory.TYPE_PREMARKET -> {
                        val windowId = intent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID)
                        val sampleIndex = intent.getIntExtra(NotificationAlarmIntentFactory.EXTRA_SAMPLE_INDEX, -1)
                        if (windowId.isNullOrBlank() || sampleIndex < 0) {
                            Log.w(TAG, "Premarket alarm missing window/sample info")
                        } else {
                            premarketRunner.run(windowId, sampleIndex)
                            scheduler.schedulePremarketSample(settings, windowId, sampleIndex)
                        }
                    }
                    else -> Log.w(TAG, "Unknown alarm type: $type")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Alarm receiver failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationAlarmEntryPoint {
        fun settingsRepository(): com.example.stocksignal.data.settings.SettingsRepository
        fun notificationScheduler(): NotificationScheduler
        fun robotsTxtCheckRunner(): RobotsTxtCheckRunner
        fun premarketQuoteRunner(): PremarketQuoteRunner
        fun notificationDiagnosticsRepository(): NotificationDiagnosticsRepository
    }

    companion object {
        private const val TAG = "NotificationAlarmReceiver"
    }
}
