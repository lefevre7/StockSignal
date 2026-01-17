package com.example.stocksignal.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext context: Context
) {

    private val workManager = WorkManager.getInstance(context)

    fun schedule(settings: AppSettings) {
        Log.d(TAG, "=== Notification Scheduler Start ===")
        Log.d(TAG, "Frequency: ${settings.frequency}, Types: ${settings.notificationTypes}")
        
        workManager.cancelAllWorkByTag(WORK_TAG)
        if (!hasNotificationSources(settings)) {
            Log.w(TAG, "❌ Background scheduling DISABLED - no notification sources enabled")
            Log.w(TAG, "   Enable Watchlist or Market Movers in Settings to receive background notifications")
            return
        }
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            Log.d(TAG, "Background scheduling disabled (only when open)")
            return
        }

        val windows = windowsForFrequency(settings)
        if (windows.isEmpty()) {
            Log.w(TAG, "❌ No notification windows configured for frequency: ${settings.frequency}")
            return
        }
        
        val interval = when (settings.frequency) {
            NotificationFrequency.ONE_PER_WEEK -> Duration.ofDays(7)
            else -> Duration.ofDays(1)
        }

        Log.d(TAG, "Scheduling ${windows.size} notification window(s) with ${interval.toHours()}h interval")
        windows.forEach { window ->
            val delay = initialDelay(window, settings)
            val request = PeriodicWorkRequestBuilder<NotificationWindowWorker>(
                interval.toHours(),
                TimeUnit.HOURS
            )
                .setConstraints(CONSTRAINTS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .setInputData(workDataOf(NotificationWindowWorker.KEY_WINDOW_ID to window.id))
                .build()

            workManager.enqueueUniquePeriodicWork(
                workName(window.id),
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "✓ Scheduled window ${window.id} with initial delay ${delay.toMinutes()}m (${delay.toHours()}h)")
        }
        
        Log.d(TAG, "=== Notification Scheduler Complete ===")
        
        // Schedule daily robots.txt check at first notification window
        scheduleRobotsTxtCheck(settings)

        // Schedule premarket quote snapshots relative to the first window
        schedulePremarketQuotes(settings)
    }
    
    private fun scheduleRobotsTxtCheck(settings: AppSettings) {
        val windows = windowsForFrequency(settings)
        if (windows.isEmpty()) return
        
        // Use the first window for the daily check
        val firstWindow = windows.first()
        val delay = initialDelay(firstWindow, settings)
        
        val request = PeriodicWorkRequestBuilder<RobotsTxtCheckWorker>(
            24, // Daily
            TimeUnit.HOURS
        )
            .setConstraints(CONSTRAINTS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .addTag(ROBOTS_TXT_CHECK_TAG)
            .build()
            
        workManager.enqueueUniquePeriodicWork(
            RobotsTxtCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing to preserve check state
            request
        )
        
        Log.d(TAG, "Scheduled daily robots.txt check at first notification window")
    }

    private fun schedulePremarketQuotes(settings: AppSettings) {
        workManager.cancelAllWorkByTag(PremarketQuoteWorker.WORK_TAG)
        if (!settings.notificationTypes.contains(NotificationType.WATCHLIST)) return
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) return

        val now = ZonedDateTime.now()
        val marketWindow = settings.scheduleWindows.firstOrNull {
            it.type == ScheduleWindowType.MARKET_OPEN_MINUS
        } ?: return
        val offset = marketWindow.offsetMinutes ?: -10
        if (offset >= 0) return
        val windowRunAt = nextRunAt(marketWindow, now, settings)
        val firstWindow = PremarketWindowUtils.firstWindowForReference(settings, windowRunAt) ?: return
        if (firstWindow.id != marketWindow.id) return
        val start = windowRunAt.minusMinutes(60)
        val interval = if (settings.frequency == NotificationFrequency.ONE_PER_WEEK) {
            Duration.ofDays(7)
        } else {
            Duration.ofDays(1)
        }

        (0..4).forEach { index ->
            val runAt = start.plusMinutes(index * 10L)
            var delay = Duration.between(Instant.now(), runAt.toInstant())
            if (delay.isNegative) {
                delay = delay.plus(interval)
            }

            val request = PeriodicWorkRequestBuilder<PremarketQuoteWorker>(
                interval.toHours(),
                TimeUnit.HOURS
            )
                .setConstraints(CONSTRAINTS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .addTag(PremarketQuoteWorker.WORK_TAG)
                .setInputData(
                    workDataOf(
                        PremarketQuoteWorker.KEY_WINDOW_ID to firstWindow.id,
                        PremarketQuoteWorker.KEY_SAMPLE_INDEX to index
                    )
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                "premarket_${firstWindow.id}_$index",
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "Scheduled premarket sample #$index for ${firstWindow.id} with delay ${delay.toMinutes()}m")
        }
    }

    private fun windowsForFrequency(settings: AppSettings): List<ScheduleWindow> {
        val windows = settings.scheduleWindows
        return when (settings.frequency) {
            NotificationFrequency.THREE_PER_DAY -> windows
            NotificationFrequency.ONE_PER_DAY ->
                windows.filter { it.type == ScheduleWindowType.MARKET_OPEN_MINUS }
            NotificationFrequency.ONE_PER_WEEK ->
                windows.filter { it.type == ScheduleWindowType.MARKET_OPEN_MINUS }.take(1)
            NotificationFrequency.ONLY_WHEN_OPEN -> emptyList()
        }
    }

    private fun initialDelay(window: ScheduleWindow, settings: AppSettings): Duration {
        val now = ZonedDateTime.now()
        val next = nextRunAt(window, now, settings)
        val delay = Duration.between(Instant.now(), next.toInstant())
        return if (delay.isNegative) Duration.ZERO else delay
    }

    private fun nextRunAt(
        window: ScheduleWindow,
        now: ZonedDateTime,
        settings: AppSettings
    ): ZonedDateTime {
        if (settings.frequency == NotificationFrequency.ONE_PER_WEEK) {
            return nextWeeklyWindow(window, now, settings.weeklyDay)
        }
        return when (window.type) {
            ScheduleWindowType.FIXED_LOCAL -> nextLocalWindow(window, now)
            ScheduleWindowType.MARKET_OPEN_MINUS -> nextMarketOpenWindow(window, now)
        }
    }

    private fun nextLocalWindow(window: ScheduleWindow, now: ZonedDateTime): ZonedDateTime {
        val hour = window.hour ?: 9
        val minute = window.minute ?: 0
        val zone = ZoneId.systemDefault()
        val localNow = now.withZoneSameInstant(zone)
        var candidate = localNow.toLocalDate().atTime(hour, minute).atZone(zone)
        if (!candidate.isAfter(localNow)) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    private fun nextMarketOpenWindow(window: ScheduleWindow, now: ZonedDateTime): ZonedDateTime {
        val zone = ZoneId.of(window.zoneId ?: "America/New_York")
        val offset = window.offsetMinutes?.toLong() ?: -10L
        val marketOpen = LocalTime.of(9, 30).plusMinutes(offset)
        var candidateDate = now.withZoneSameInstant(zone).toLocalDate()
        var candidate = candidateDate.atTime(marketOpen).atZone(zone)
        if (!candidate.isAfter(now.withZoneSameInstant(zone))) {
            candidateDate = candidateDate.plusDays(1)
            candidate = candidateDate.atTime(marketOpen).atZone(zone)
        }
        if (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) {
            candidate = candidate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        }
        return candidate
    }

    private fun nextWeeklyWindow(
        window: ScheduleWindow,
        now: ZonedDateTime,
        weeklyDay: DayOfWeek
    ): ZonedDateTime {
        val zone = ZoneId.of(window.zoneId ?: "America/New_York")
        val offset = window.offsetMinutes?.toLong() ?: -10L
        val marketOpen = LocalTime.of(9, 30).plusMinutes(offset)
        val nowInZone = now.withZoneSameInstant(zone)
        var candidateDate = nowInZone.toLocalDate().with(TemporalAdjusters.nextOrSame(weeklyDay))
        var candidate = candidateDate.atTime(marketOpen).atZone(zone)
        if (!candidate.isAfter(nowInZone)) {
            candidateDate = candidateDate.with(TemporalAdjusters.next(weeklyDay))
            candidate = candidateDate.atTime(marketOpen).atZone(zone)
        }
        return candidate
    }

    private fun workName(windowId: String) = "notification_window_$windowId"

    private fun hasNotificationSources(settings: AppSettings): Boolean {
        val types = settings.notificationTypes
        return types.contains(NotificationType.WATCHLIST) || types.contains(NotificationType.MARKET_MOVERS)
    }

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val WORK_TAG = "notification_window"
        private const val ROBOTS_TXT_CHECK_TAG = "robots_txt_check"
        private val CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
