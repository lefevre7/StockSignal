package com.example.stocksignal.notifications

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.stocksignal.R
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.network.StooqApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobotsTxtCheckRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stooqApi: StooqApi,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsRepository: NotificationDiagnosticsRepository,
    private val backgroundRunPolicy: BackgroundStooqRunPolicy
) {

    enum class RunOutcome {
        SUCCESS,
        FAILURE
    }

    suspend fun run(): RunOutcome {
        return try {
            backgroundRunPolicy.robotsSkipReason()?.let { reason ->
                Log.d(TAG, "Robots.txt check skipped: $reason")
                runCatching {
                    diagnosticsRepository.recordRobotsRun("skipped", reason)
                }
                return RunOutcome.SUCCESS
            }
            // Check if we already successfully checked today
            val lastCheckDate = settingsRepository.getLastRobotsTxtCheckDate()
            val today = LocalDate.now()

            if (lastCheckDate == today) {
                Log.d(TAG, "Robots.txt already checked successfully today, skipping")
                return RunOutcome.SUCCESS
            }

            // Fetch robots.txt from Stooq
            val robotsTxt = try {
                stooqApi.getRobotsTxt()
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching robots.txt", e)
                runCatching {
                    diagnosticsRepository.recordRobotsRun("failed", "network error: ${e.message}")
                }
                // Don't update last check date on network failure
                return RunOutcome.FAILURE
            }

            // Compare with expected content (exact match including whitespace)
            if (robotsTxt != EXPECTED_ROBOTS_TXT) {
                Log.w(TAG, "Stooq's robots.txt has changed!")
                Log.w(TAG, "Expected: <<<$EXPECTED_ROBOTS_TXT>>>")
                Log.w(TAG, "Actual: <<<$robotsTxt>>>")
                runCatching {
                    diagnosticsRepository.recordRobotsRun("changed", "robots.txt changed")
                }

                // Show toast if app is in foreground, notification if in background
                if (isAppInForeground()) {
                    showToast()
                } else {
                    showNotification()
                }
            } else {
                Log.d(TAG, "Robots.txt check passed")
                runCatching {
                    diagnosticsRepository.recordRobotsRun("passed", null)
                }
            }

            // Mark as successfully checked today
            settingsRepository.setLastRobotsTxtCheckDate(today)

            RunOutcome.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "Error during robots.txt check", e)
            runCatching {
                diagnosticsRepository.recordRobotsRun("failed", "error: ${e.message}")
            }
            RunOutcome.FAILURE
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
        private const val TAG = "RobotsTxtCheckRunner"
        private const val CHANNEL_ID = "system_alerts"
        private const val NOTIFICATION_ID = 9001

        // Expected robots.txt content - exact match including whitespace
        // Note: Stooq uses Windows-style CRLF line endings (\r\n)
        private const val EXPECTED_ROBOTS_TXT = "User-agent: *\r\nDisallow:\r\n"
    }
}
