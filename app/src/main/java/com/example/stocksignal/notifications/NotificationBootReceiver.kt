package com.example.stocksignal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives BOOT_COMPLETED broadcast and ensures notification workers are scheduled.
 * This is critical because if the app process is killed and never reopened,
 * the Application.onCreate() scheduling won't happen.
 */
class NotificationBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed - scheduling notification bootstrap worker")
        
        // Enqueue a bootstrap worker that will read settings and schedule all notification workers
        val request = OneTimeWorkRequestBuilder<NotificationBootstrapWorker>()
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val TAG = "NotificationBootReceiver"
        private const val WORK_NAME = "notification_bootstrap_on_boot"
        private const val WORK_TAG = "notification_bootstrap"
    }
}
