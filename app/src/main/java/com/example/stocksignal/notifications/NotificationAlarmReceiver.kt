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
        val windowRunner = entryPoint.notificationWindowRunner()
        val robotsRunner = entryPoint.robotsTxtCheckRunner()
        val premarketRunner = entryPoint.premarketQuoteRunner()

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
                            ContextCompat.startForegroundService(appContext, serviceIntent)
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
                            ContextCompat.startForegroundService(appContext, serviceIntent)
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
        fun notificationWindowRunner(): NotificationWindowRunner
        fun robotsTxtCheckRunner(): RobotsTxtCheckRunner
        fun premarketQuoteRunner(): PremarketQuoteRunner
    }

    companion object {
        private const val TAG = "NotificationAlarmReceiver"
    }
}
