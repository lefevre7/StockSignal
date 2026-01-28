package com.example.stocksignal.notifications

import android.app.AlarmManager
import android.content.Context
import android.util.Log
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsRepository: NotificationDiagnosticsRepository
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun schedule(settings: AppSettings, force: Boolean = false) {
        Log.d(TAG, "=== Notification Scheduler Start ===")
        Log.d(TAG, "Frequency: ${settings.frequency}, Types: ${settings.notificationTypes}")

        if (!hasNotificationSources(settings)) {
            cancelAllAlarms()
            diagnosticsRepository.clearScheduleFingerprint()
            Log.w(TAG, "❌ Background scheduling DISABLED - no notification sources enabled")
            return
        }

        val effectiveFrequency = resolveEffectiveFrequency(settings.frequency)
        val effectiveSettings = settings.copy(frequency = effectiveFrequency)
        if (effectiveFrequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            cancelAllAlarms()
            diagnosticsRepository.clearScheduleFingerprint()
            Log.d(TAG, "Background scheduling disabled (only when open)")
            return
        }

        val windows = windowsForFrequency(effectiveSettings)
        if (windows.isEmpty()) {
            Log.w(TAG, "❌ No notification windows configured for frequency: $effectiveFrequency")
            return
        }

        val isDevMode = isDevMode(effectiveFrequency)
        val exactAllowed = canScheduleExactAlarms()
        diagnosticsRepository.setLastExactAlarmAllowed(exactAllowed)
        val fingerprint = scheduleFingerprint(effectiveSettings, windows, isDevMode, exactAllowed)
        if (!force && !shouldReschedule(fingerprint, windows)) {
            Log.d(TAG, "Schedule unchanged and alarms present; skipping reschedule")
            return
        }

        Log.d(TAG, "Scheduling ${windows.size} window(s) (devMode=$isDevMode, exactAllowed=$exactAllowed)")
        cancelObsoleteWindowAlarms(windows)
        windows.forEach { window ->
            scheduleWindowAlarm(effectiveSettings, window, exactAllowed)
        }
        diagnosticsRepository.setScheduledWindowIds(windows.map { it.id }.toSet())
        diagnosticsRepository.setLastScheduleFingerprint(fingerprint)

        scheduleRobotsTxtCheck(effectiveSettings)
        schedulePremarketQuotes(effectiveSettings)

        Log.d(TAG, "=== Notification Scheduler Complete ===")
    }

    suspend fun scheduleNextWindow(settings: AppSettings, windowId: String) {
        val effectiveFrequency = resolveEffectiveFrequency(settings.frequency)
        val effectiveSettings = settings.copy(frequency = effectiveFrequency)
        if (!hasNotificationSources(settings) || effectiveFrequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            cancelWindowAlarm(windowId)
            diagnosticsRepository.setNextWindowRun(windowId, null)
            return
        }
        val windows = windowsForFrequency(effectiveSettings)
        val window = windows.firstOrNull { it.id == windowId }
        if (window == null) {
            cancelWindowAlarm(windowId)
            diagnosticsRepository.setNextWindowRun(windowId, null)
            return
        }
        scheduleWindowAlarm(effectiveSettings, window, canScheduleExactAlarms())
        val scheduled = diagnosticsRepository.getScheduledWindowIds() + windowId
        diagnosticsRepository.setScheduledWindowIds(scheduled)
    }

    suspend fun scheduleRobotsTxtCheck(settings: AppSettings) {
        val windows = windowsForFrequency(settings)
        if (windows.isEmpty() || settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) {
            cancelRobotsAlarm()
            diagnosticsRepository.setRobotsNextRun(null)
            return
        }
        val firstWindow = windows.first()
        val nextRunAt = nextRunAt(firstWindow, ZonedDateTime.now(), settings)
        scheduleAlarm(
            triggerAtMillis = nextRunAt.toInstant().toEpochMilli(),
            pendingIntent = NotificationAlarmIntentFactory.robotsPendingIntent(context),
            exactAllowed = canScheduleExactAlarms()
        )
        diagnosticsRepository.setRobotsNextRun(nextRunAt.toInstant().toEpochMilli())
    }

    suspend fun schedulePremarketSample(settings: AppSettings, windowId: String, sampleIndex: Int) {
        if (!shouldSchedulePremarket(settings)) {
            cancelPremarketAlarm(windowId, sampleIndex)
            diagnosticsRepository.setPremarketNextRun(NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex), null)
            return
        }
        val nextRunAt = nextPremarketSampleAt(settings, windowId, sampleIndex) ?: return
        scheduleAlarm(
            triggerAtMillis = nextRunAt.toInstant().toEpochMilli(),
            pendingIntent = NotificationAlarmIntentFactory.premarketPendingIntent(context, windowId, sampleIndex),
            exactAllowed = canScheduleExactAlarms()
        )
        diagnosticsRepository.setPremarketNextRun(
            NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex),
            nextRunAt.toInstant().toEpochMilli()
        )
        val scheduled = diagnosticsRepository.getScheduledPremarketKeys() +
            NotificationAlarmIntentFactory.premarketKey(windowId, sampleIndex)
        diagnosticsRepository.setScheduledPremarketKeys(scheduled)
    }

    private suspend fun schedulePremarketQuotes(settings: AppSettings) {
        if (!shouldSchedulePremarket(settings)) {
            cancelPremarketAlarms()
            return
        }
        val windowId = resolvePremarketWindowId(settings) ?: run {
            cancelPremarketAlarms()
            return
        }
        val expectedKeys = (0..4).map { index ->
            NotificationAlarmIntentFactory.premarketKey(windowId, index)
        }.toSet()
        cancelObsoletePremarketAlarms(expectedKeys)
        (0..4).forEach { index ->
            val nextRunAt = nextPremarketSampleAt(settings, windowId, index) ?: return@forEach
            scheduleAlarm(
                triggerAtMillis = nextRunAt.toInstant().toEpochMilli(),
                pendingIntent = NotificationAlarmIntentFactory.premarketPendingIntent(context, windowId, index),
                exactAllowed = canScheduleExactAlarms()
            )
            diagnosticsRepository.setPremarketNextRun(
                NotificationAlarmIntentFactory.premarketKey(windowId, index),
                nextRunAt.toInstant().toEpochMilli()
            )
        }
        diagnosticsRepository.setScheduledPremarketKeys(expectedKeys)
    }

    private fun shouldSchedulePremarket(settings: AppSettings): Boolean {
        if (!settings.notificationTypes.contains(NotificationType.WATCHLIST)) return false
        if (settings.frequency == NotificationFrequency.ONLY_WHEN_OPEN) return false
        return true
    }

    private fun resolvePremarketWindowId(settings: AppSettings): String? {
        val now = ZonedDateTime.now()
        val marketWindow = settings.scheduleWindows.firstOrNull {
            it.type == ScheduleWindowType.MARKET_OPEN_MINUS
        } ?: return null
        val offset = marketWindow.offsetMinutes ?: -10
        if (offset >= 0) return null
        val windowRunAt = nextRunAt(marketWindow, now, settings)
        val firstWindow = PremarketWindowUtils.firstWindowForReference(settings, windowRunAt) ?: return null
        if (firstWindow.id != marketWindow.id) return null
        return firstWindow.id
    }

    private fun nextPremarketSampleAt(
        settings: AppSettings,
        windowId: String,
        sampleIndex: Int
    ): ZonedDateTime? {
        val window = settings.scheduleWindows.firstOrNull { it.id == windowId } ?: return null
        if (window.type != ScheduleWindowType.MARKET_OPEN_MINUS) return null
        val offset = window.offsetMinutes ?: -10
        if (offset >= 0) return null
        val now = ZonedDateTime.now()
        val windowRunAt = nextRunAt(window, now, settings)
        val firstWindow = PremarketWindowUtils.firstWindowForReference(settings, windowRunAt) ?: return null
        if (firstWindow.id != windowId) return null
        val start = windowRunAt.minusMinutes(60)
        var runAt = start.plusMinutes(sampleIndex * 10L)
        val interval = if (settings.frequency == NotificationFrequency.ONE_PER_WEEK) {
            Duration.ofDays(7)
        } else {
            Duration.ofDays(1)
        }
        if (runAt.toInstant().isBefore(Instant.now())) {
            runAt = runAt.plus(interval)
        }
        return runAt
    }

    private suspend fun cancelAllAlarms() {
        val scheduledWindowIds = diagnosticsRepository.getScheduledWindowIds()
        scheduledWindowIds.forEach { cancelWindowAlarm(it) }
        diagnosticsRepository.setScheduledWindowIds(emptySet())
        diagnosticsRepository.getScheduledPremarketKeys().forEach { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val windowId = parts[0]
                val sampleIndex = parts[1].toIntOrNull() ?: return@forEach
                cancelPremarketAlarm(windowId, sampleIndex)
            }
            diagnosticsRepository.setPremarketNextRun(key, null)
        }
        diagnosticsRepository.setScheduledPremarketKeys(emptySet())
        cancelRobotsAlarm()
        diagnosticsRepository.setRobotsNextRun(null)
    }

    private suspend fun cancelObsoleteWindowAlarms(desiredWindows: List<ScheduleWindow>) {
        val desiredIds = desiredWindows.map { it.id }.toSet()
        val scheduledIds = diagnosticsRepository.getScheduledWindowIds()
        val obsolete = scheduledIds - desiredIds
        obsolete.forEach {
            cancelWindowAlarm(it)
            diagnosticsRepository.setNextWindowRun(it, null)
        }
        if (scheduledIds.isNotEmpty() && scheduledIds != desiredIds) {
            desiredIds.forEach {
                cancelWindowAlarm(it)
                diagnosticsRepository.setNextWindowRun(it, null)
            }
        }
    }

    private suspend fun cancelPremarketAlarms() {
        val scheduled = diagnosticsRepository.getScheduledPremarketKeys()
        scheduled.forEach { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val windowId = parts[0]
                val sampleIndex = parts[1].toIntOrNull() ?: return@forEach
                cancelPremarketAlarm(windowId, sampleIndex)
            }
            diagnosticsRepository.setPremarketNextRun(key, null)
        }
        diagnosticsRepository.setScheduledPremarketKeys(emptySet())
    }

    private suspend fun cancelObsoletePremarketAlarms(expectedKeys: Set<String>) {
        val scheduled = diagnosticsRepository.getScheduledPremarketKeys()
        val obsolete = scheduled - expectedKeys
        obsolete.forEach { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val windowId = parts[0]
                val sampleIndex = parts[1].toIntOrNull() ?: return@forEach
                cancelPremarketAlarm(windowId, sampleIndex)
            }
            diagnosticsRepository.setPremarketNextRun(key, null)
        }
        if (scheduled.isNotEmpty() && scheduled != expectedKeys) {
            expectedKeys.forEach { key ->
                val parts = key.split(":")
                if (parts.size == 2) {
                    val windowId = parts[0]
                    val sampleIndex = parts[1].toIntOrNull() ?: return@forEach
                    cancelPremarketAlarm(windowId, sampleIndex)
                }
                diagnosticsRepository.setPremarketNextRun(key, null)
            }
        }
    }

    private fun cancelWindowAlarm(windowId: String) {
        val pendingIntent = NotificationAlarmIntentFactory.windowPendingIntent(context, windowId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancelPremarketAlarm(windowId: String, sampleIndex: Int) {
        val pendingIntent = NotificationAlarmIntentFactory.premarketPendingIntent(context, windowId, sampleIndex)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancelRobotsAlarm() {
        val pendingIntent = NotificationAlarmIntentFactory.robotsPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private suspend fun scheduleWindowAlarm(
        settings: AppSettings,
        window: ScheduleWindow,
        exactAllowed: Boolean
    ) {
        val now = ZonedDateTime.now()
        val triggerAt = if (isDevMode(settings.frequency)) {
            now.plusMinutes(DEV_REPEAT_DELAY_MINUTES)
        } else {
            nextRunAt(window, now, settings)
        }
        val triggerAtMillis = triggerAt.toInstant().toEpochMilli()
        scheduleAlarm(
            triggerAtMillis = triggerAtMillis,
            pendingIntent = NotificationAlarmIntentFactory.windowPendingIntent(context, window.id),
            exactAllowed = exactAllowed
        )
        diagnosticsRepository.setNextWindowRun(window.id, triggerAtMillis)
    }

    private fun scheduleAlarm(
        triggerAtMillis: Long,
        pendingIntent: android.app.PendingIntent,
        exactAllowed: Boolean
    ) {
        val safeTriggerAt = maxOf(triggerAtMillis, System.currentTimeMillis() + MIN_DELAY_MILLIS)
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTriggerAt, pendingIntent)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTriggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, safeTriggerAt, pendingIntent)
            }
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
            NotificationFrequency.DEV_FIVE_MINUTES -> windows.take(1)
        }
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
            ScheduleWindowType.FIXED_LOCAL -> nextLocalWindowWeekday(window, now)
            ScheduleWindowType.MARKET_OPEN_MINUS -> nextMarketOpenWindow(window, now)
        }
    }

    private fun nextLocalWindowWeekday(window: ScheduleWindow, now: ZonedDateTime): ZonedDateTime {
        val hour = window.hour ?: 9
        val minute = window.minute ?: 0
        val zone = ZoneId.systemDefault()
        val localNow = now.withZoneSameInstant(zone)
        var candidate = localNow.toLocalDate().atTime(hour, minute).atZone(zone)
        if (!candidate.isAfter(localNow)) {
            candidate = candidate.plusDays(1)
        }
        if (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) {
            candidate = candidate.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
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

    private fun hasNotificationSources(settings: AppSettings): Boolean {
        val types = settings.notificationTypes
        return types.contains(NotificationType.WATCHLIST) || types.contains(NotificationType.MARKET_MOVERS)
    }

    private fun resolveEffectiveFrequency(frequency: NotificationFrequency): NotificationFrequency {
        return if (frequency == NotificationFrequency.DEV_FIVE_MINUTES && !DebugConfig.ENABLE_DEV_MODE) {
            Log.w(TAG, "⚠️ DEV_FIVE_MINUTES frequency set but debug mode disabled - falling back to THREE_PER_DAY")
            NotificationFrequency.THREE_PER_DAY
        } else {
            frequency
        }
    }

    private fun isDevMode(frequency: NotificationFrequency): Boolean {
        return DebugConfig.ENABLE_DEV_MODE && frequency == NotificationFrequency.DEV_FIVE_MINUTES
    }

    private fun scheduleFingerprint(
        settings: AppSettings,
        windows: List<ScheduleWindow>,
        isDevMode: Boolean,
        exactAllowed: Boolean
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
            "freq=${settings.frequency.name}",
            "sources=$sources",
            "weekly=${settings.weeklyDay.name}",
            "dev=$isDevMode",
            "exact=$exactAllowed",
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

        val nowMillis = System.currentTimeMillis()
        val windowIds = windows.map { it.id }.toSet()
        val scheduledIds = diagnosticsRepository.getScheduledWindowIds()
        if (scheduledIds != windowIds) {
            Log.d(TAG, "Scheduled windows $scheduledIds != desired $windowIds; rescheduling")
            return true
        }
        val nextRuns = diagnosticsRepository.getNextWindowRunTimes(windowIds)
        if (windowIds.any { nextRuns[it] == null || nextRuns[it]!! <= nowMillis }) {
            Log.d(TAG, "Missing or stale next run times; rescheduling")
            return true
        }

        val robotsNext = diagnosticsRepository.getRobotsNextRun()
        if (robotsNext == null || robotsNext <= nowMillis) {
            Log.d(TAG, "Robots check not scheduled or stale; rescheduling")
            return true
        }

        val premarketKeys = diagnosticsRepository.getScheduledPremarketKeys()
        if (premarketKeys.isNotEmpty()) {
            val nextPremarket = diagnosticsRepository.getPremarketNextRuns(premarketKeys)
            if (premarketKeys.any { nextPremarket[it] == null || nextPremarket[it]!! <= nowMillis }) {
                Log.d(TAG, "Premarket alarms not scheduled or stale; rescheduling")
                return true
            }
        }

        return false
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    companion object {
        private const val TAG = "NotificationScheduler"
        private const val DEV_REPEAT_DELAY_MINUTES = 5L
        private const val MIN_DELAY_MILLIS = 5_000L
    }
}
