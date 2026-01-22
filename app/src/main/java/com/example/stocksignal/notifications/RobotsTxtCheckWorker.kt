package com.example.stocksignal.notifications

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.stocksignal.R
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.network.StooqApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class RobotsTxtCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val stooqApi: StooqApi,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            // Check if we already successfully checked today
            val lastCheckDate = settingsRepository.getLastRobotsTxtCheckDate()
            val today = LocalDate.now()
            
            if (lastCheckDate == today) {
                Log.d(TAG, "Robots.txt already checked successfully today, skipping")
                return Result.success()
            }

            // Fetch robots.txt from Stooq
            val robotsTxt = try {
                stooqApi.getRobotsTxt()
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching robots.txt", e)
                // Don't update last check date on network failure
                // This allows retry on next scheduled run
                return Result.failure()
            }

            // Compare with expected content (exact match including whitespace)
            if (robotsTxt != EXPECTED_ROBOTS_TXT) {
                Log.w(TAG, "Stooq's robots.txt has changed!")
                Log.w(TAG, "Expected: <<<$EXPECTED_ROBOTS_TXT>>>")
                Log.w(TAG, "Actual: <<<$robotsTxt>>>")
                
                // Show toast if app is in foreground, notification if in background
                // This ensures the user is always notified regardless of app state
                if (isAppInForeground()) {
                    showToast()
                } else {
                    showNotification()
                }
                
                // Note: We still mark as successfully checked even on mismatch
                // to avoid spamming the user with notifications on every scheduled run
            } else {
                Log.d(TAG, "Robots.txt check passed")
            }

            // Mark as successfully checked today
            settingsRepository.setLastRobotsTxtCheckDate(today)
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during robots.txt check", e)
            return Result.failure()
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName
        
        return appProcesses.any { processInfo ->
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    processInfo.processName == packageName
        }
    }

    private fun showToast() {
        // Toast must be shown on the main thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Stooq's robots.txt has changed, please tell the developer!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "System Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Important system alerts and warnings"
        }
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Stooq Robots.txt Changed")
            .setContentText("Stooq's robots.txt has changed, please tell the developer!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "RobotsTxtCheckWorker"
        private const val CHANNEL_ID = "system_alerts"
        private const val NOTIFICATION_ID = 9001
        const val WORK_NAME = "robots_txt_check"
        
        // Expected robots.txt content - exact match including whitespace
        // Note: Stooq uses Windows-style CRLF line endings (\r\n)
        private const val EXPECTED_ROBOTS_TXT = "User-agent: *\r\nDisallow:\r\n"
    }
}
