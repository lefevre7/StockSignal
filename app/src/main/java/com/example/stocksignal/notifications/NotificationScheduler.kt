package com.example.stocksignal.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.util.DebugConfig
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) {

    private val workManager = WorkManager.getInstance(context)

    suspend fun schedule(settings: AppSettings, force: Boolean = false) {
        Log.d(TAG, "=== Notification Scheduler Start ===")
        Log.d(TAG, "Frequency: ${settings.frequency}, Types: ${settings.notificationTypes}")
        
        if (!hasNotificationSources(settings)) {
            workManager.cancelAllWorkByTag(WORK_TAG)
            diagnosticsRepository.clearScheduleFingerprint()
            Log.w(TAG, "❌ Background scheduling DISABLED - no notification sources enabled")
            Log.w(TAG, "   Enable Watchlist or Market Movers in Settings to receive background notifications")
            return
        }
        
        // Auto-fallback if DEV mode is set but debug flag is disabled
        val effectiveFrequency = if (settings.frequency == NotificationFrequency.DEV_ONE_MINUTE && !DebugConfig.ENABLE_DEV_MODE) {
            Log.w(TAG, "⚠️ DEV_ONE_MINUTE frequency set but debug mode disabled - falling back to THREE_PER_DAY")
            NotificationFrequency.THREE_PER_DAY
        } else {
            settings.frequency
        }
        
        if (effectiveFrequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            workManager.cancelAllWorkByTag(WORK_TAG)
            diagnosticsRepository.clearScheduleFingerprint()
            Log.d(TAG, "Background scheduling disabled (only when open)")
            return
        }

        val windows = windowsForFrequency(settings.copy(frequency = effectiveFrequency))
        if (windows.isEmpty()) {
            Log.w(TAG, "❌ No notification windows configured for frequency: $effectiveFrequency")
            return
        }
        
        val devInterval = Duration.ofMinutes(2)
        val interval = when (effectiveFrequency) {
            NotificationFrequency.ONE_PER_WEEK -> Duration.ofDays(7)
            NotificationFrequency.DEV_ONE_MINUTE -> devInterval
            else -> Duration.ofDays(1)
        }
        
        // For DEV mode, use one-time work to run immediately, then repeat via chained one-time work
        val isDevMode = effectiveFrequency == NotificationFrequency.DEV_ONE_MINUTE && DebugConfig.ENABLE_DEV_MODE

        val fingerprint = scheduleFingerprint(settings, effectiveFrequency, windows, isDevMode)
        if (!force && !shouldReschedule(fingerprint, windows)) {
            Log.d(TAG, "Schedule unchanged and workers present; skipping reschedule")
            return
        }

        workManager.cancelAllWorkByTag(WORK_TAG)
        Log.d(TAG, "Scheduling ${windows.size} notification window(s) with ${interval.toMinutes()}min interval (devMode=$isDevMode)")
        windows.forEach { window ->
            if (isDevMode) {
                // DEV MODE: Schedule immediate work and then repeat via chained one-time work
                Log.d(TAG, "🔧 DEV MODE: Scheduling immediate + repeat work for ${window.id}")
                
                // Immediate one-time execution
                val immediateRequest = OneTimeWorkRequestBuilder<NotificationWindowWorker>()
                    .setConstraints(CONSTRAINTS)
                    .addTag(WORK_TAG)
                    .addTag(DEV_IMMEDIATE_TAG)
                    .addTag(windowTag(window.id))
                    .setInputData(workDataOf(NotificationWindowWorker.KEY_WINDOW_ID to window.id))
                    .build()
                
                workManager.enqueueUniqueWork(
                    "dev_immediate_${window.id}",
                    ExistingWorkPolicy.REPLACE,
                    immediateRequest
                )
                Log.d(TAG, "⚡ DEV: Queued immediate execution for ${window.id}")
                
                val repeatRequest = OneTimeWorkRequestBuilder<NotificationWindowWorker>()
                    .setConstraints(CONSTRAINTS)
                    .setInitialDelay(devInterval.toMinutes(), TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .addTag(DEV_REPEAT_TAG)
                    .addTag(windowTag(window.id))
                    .setInputData(workDataOf(NotificationWindowWorker.KEY_WINDOW_ID to window.id))
                    .build()

                workManager.enqueueUniqueWork(
                    devRepeatWorkName(window.id),
                    ExistingWorkPolicy.REPLACE,
                    repeatRequest
                )
                Log.d(TAG, "✓ DEV: Scheduled repeat (${devInterval.toMinutes()}min) for ${window.id}")
            } else {
                // Normal mode: Schedule with calculated delay
                val delay = initialDelay(window, settings)
                val request = PeriodicWorkRequestBuilder<NotificationWindowWorker>(
                    interval.toHours(),
                    TimeUnit.HOURS
                )
                    .setConstraints(CONSTRAINTS)
                    .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                    .addTag(WORK_TAG)
                    .addTag(windowTag(window.id))
                    .setInputData(workDataOf(NotificationWindowWorker.KEY_WINDOW_ID to window.id))
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    workName(window.id),
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )
                Log.d(TAG, "✓ Scheduled window ${window.id} with initial delay ${delay.toMinutes()}m (${delay.toHours()}h)")
            }
        }

        diagnosticsRepository.setLastScheduleFingerprint(fingerprint)
        
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
            NotificationFrequency.DEV_ONE_MINUTE -> windows.take(1) // Just use first window for dev testing
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
    private fun devRepeatWorkName(windowId: String) = "notification_dev_repeat_$windowId"

    private fun windowTag(windowId: String) = "$WINDOW_TAG_PREFIX$windowId"

    private fun hasNotificationSources(settings: AppSettings): Boolean {
        val types = settings.notificationTypes
        return types.contains(NotificationType.WATCHLIST) || types.contains(NotificationType.MARKET_MOVERS)
    }

    private fun scheduleFingerprint(
        settings: AppSettings,
        effectiveFrequency: NotificationFrequency,
        windows: List<ScheduleWindow>,
        isDevMode: Boolean
    ): String {
        val sources = settings.notificationTypes
            .filter { it == NotificationType.WATCHLIST || it == NotificationType.MARKET_MOVERS }
            .sortedBy { it.name }
            .joinToString(",")
        val windowsFingerprint = windows.joinToString(";") { window ->
            listOf(
                window.id,
                window.type.name,
                window.hour?.toString() ?: "",
                window.minute?.toString() ?: "",
                window.zoneId ?: "",
                window.offsetMinutes?.toString() ?: ""
            ).joinToString("|")
        }
        return listOf(
            "freq=${effectiveFrequency.name}",
            "sources=$sources",
            "weekly=${settings.weeklyDay.name}",
            "dev=$isDevMode",
            "windows=$windowsFingerprint"
        ).joinToString("::")
    }

    private suspend fun shouldReschedule(
        fingerprint: String,
        windows: List<ScheduleWindow>
    ): Boolean {
        val lastFingerprint = diagnosticsRepository.getLastScheduleFingerprint()
        if (lastFingerprint != fingerprint) {
            Log.d(TAG, "Schedule fingerprint changed or missing; rescheduling")
            return true
        }

        val infos = withContext(Dispatchers.IO) {
            runCatching { workManager.getWorkInfosByTag(WORK_TAG).get() }.getOrDefault(emptyList())
        }
        val activeInfos = infos.filter {
            it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.BLOCKED
        }
        if (activeInfos.isEmpty()) {
            Log.d(TAG, "No active notification window workers found; rescheduling")
            return true
        }

        val scheduledWindowIds = activeInfos
            .flatMap { it.tags }
            .filter { it.startsWith(WINDOW_TAG_PREFIX) }
            .map { it.removePrefix(WINDOW_TAG_PREFIX) }
            .toSet()
        val desiredWindowIds = windows.map { it.id }.toSet()
        if (scheduledWindowIds != desiredWindowIds) {
            Log.d(TAG, "Scheduled windows $scheduledWindowIds != desired $desiredWindowIds; rescheduling")
            return true
        }

        return false
    }

    companion object {
        private const val TAG = "NotificationScheduler"
        const val WORK_TAG = "notification_window"
        const val WINDOW_TAG_PREFIX = "window_"
        const val DEV_IMMEDIATE_TAG = "dev_immediate"
        const val DEV_REPEAT_TAG = "dev_repeat"
        private const val ROBOTS_TXT_CHECK_TAG = "robots_txt_check"
        private val CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
