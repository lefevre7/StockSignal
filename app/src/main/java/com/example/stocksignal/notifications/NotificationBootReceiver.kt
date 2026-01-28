package com.example.stocksignal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.stocksignal.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Receives BOOT_COMPLETED broadcast and ensures notification alarms are scheduled.
 */
class NotificationBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed - scheduling notification alarms")

        val pendingResult = runCatching { goAsync() }.getOrNull()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootReceiverEntryPoint::class.java
        )
        val settingsRepository = entryPoint.settingsRepository()
        val scheduler = entryPoint.notificationScheduler()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                scheduler.schedule(settings, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule alarms on boot", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun notificationScheduler(): NotificationScheduler
    }

    companion object {
        private const val TAG = "NotificationBootReceiver"
    }
}
