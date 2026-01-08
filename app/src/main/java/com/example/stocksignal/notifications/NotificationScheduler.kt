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
        workManager.cancelAllWorkByTag(WORK_TAG)
        if (!settings.notificationTypes.contains(NotificationType.DIGESTS)) {
            Log.d(TAG, "Background scheduling disabled (digests off)")
            return
        }
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            Log.d(TAG, "Background scheduling disabled (only when open)")
            return
        }

        val windows = windowsForFrequency(settings)
        if (windows.isEmpty()) return
        val interval = when (settings.frequency) {
            NotificationFrequency.ONE_PER_WEEK -> Duration.ofDays(7)
            else -> Duration.ofDays(1)
        }

        windows.forEach { window ->
            val delay = initialDelay(window)
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
        }
    }

    private fun windowsForFrequency(settings: AppSettings): List<ScheduleWindow> {
        val windows = settings.scheduleWindows
        return when (settings.frequency) {
            NotificationFrequency.THREE_PER_DAY -> windows
            NotificationFrequency.ONE_PER_DAY -> windows.take(1)
            NotificationFrequency.ONE_PER_WEEK -> windows.take(1)
            NotificationFrequency.ONLY_WHEN_OPEN -> emptyList()
        }
    }

    private fun initialDelay(window: ScheduleWindow): Duration {
        val now = ZonedDateTime.now()
        val next = nextRunAt(window, now)
        val delay = Duration.between(Instant.now(), next.toInstant())
        return if (delay.isNegative) Duration.ZERO else delay
    }

    private fun nextRunAt(window: ScheduleWindow, now: ZonedDateTime): ZonedDateTime {
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

    private fun workName(windowId: String) = "notification_window_$windowId"

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val WORK_TAG = "notification_window"
        private val CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
