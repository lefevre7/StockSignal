package com.example.stocksignal.notifications

import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object PremarketWindowUtils {

    private val MARKET_OPEN = LocalTime.of(9, 30)
    private val MARKET_CLOSE = LocalTime.of(16, 0)

    fun windowsForFrequency(settings: AppSettings): List<ScheduleWindow> {
        val windows = settings.scheduleWindows
        return when (settings.frequency) {
            NotificationFrequency.THREE_PER_DAY -> windows
            NotificationFrequency.ONE_PER_DAY ->
                windows.filter { it.type == ScheduleWindowType.MARKET_OPEN_MINUS }
            NotificationFrequency.ONE_PER_WEEK ->
                windows.filter { it.type == ScheduleWindowType.MARKET_OPEN_MINUS }.take(1)
            NotificationFrequency.ONLY_WHEN_OPEN -> emptyList()
            NotificationFrequency.DEV_FIVE_MINUTES -> windows.take(1) // Dev mode: just first window
        }
    }

    fun firstWindow(settings: AppSettings, now: ZonedDateTime): ScheduleWindow? {
        return firstWindowForReference(settings, now)
    }

    fun resolvePremarketWindow(
        settings: AppSettings,
        windowId: String?,
        now: ZonedDateTime
    ): ScheduleWindow? {
        if (windowId.isNullOrBlank()) return null
        val window = settings.scheduleWindows.firstOrNull { it.id == windowId } ?: return null
        if (window.type != ScheduleWindowType.MARKET_OPEN_MINUS) return null
        val offset = window.offsetMinutes ?: -10
        if (offset >= 0) return null
        val windowRunAt = nextRunAt(window, now, settings)
        val first = firstWindowForReference(settings, windowRunAt) ?: return null
        if (window.id != first.id) return null
        return window
    }

    fun firstWindowForReference(
        settings: AppSettings,
        reference: ZonedDateTime
    ): ScheduleWindow? {
        val windows = windowsForFrequency(settings)
        if (windows.isEmpty()) return null
        return windows.minByOrNull { window ->
            scheduledAtForReference(window, reference).toInstant()
        }
    }

    fun marketZone(window: ScheduleWindow): ZoneId {
        return ZoneId.of(window.zoneId ?: "America/New_York")
    }

    fun isDuringMarketHours(now: ZonedDateTime): Boolean {
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return false
        val time = now.toLocalTime()
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE)
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
        val zone = marketZone(window)
        val offset = window.offsetMinutes?.toLong() ?: -10L
        val marketOpen = MARKET_OPEN.plusMinutes(offset)
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
        val zone = marketZone(window)
        val offset = window.offsetMinutes?.toLong() ?: -10L
        val marketOpen = MARKET_OPEN.plusMinutes(offset)
        val nowInZone = now.withZoneSameInstant(zone)
        var candidateDate = nowInZone.toLocalDate().with(TemporalAdjusters.nextOrSame(weeklyDay))
        var candidate = candidateDate.atTime(marketOpen).atZone(zone)
        if (!candidate.isAfter(nowInZone)) {
            candidateDate = candidateDate.with(TemporalAdjusters.next(weeklyDay))
            candidate = candidateDate.atTime(marketOpen).atZone(zone)
        }
        return candidate
    }

    private fun scheduledAtForReference(
        window: ScheduleWindow,
        reference: ZonedDateTime
    ): ZonedDateTime {
        val zone = if (window.type == ScheduleWindowType.FIXED_LOCAL) {
            ZoneId.systemDefault()
        } else {
            marketZone(window)
        }
        val date = reference.withZoneSameInstant(zone).toLocalDate()
        val time = if (window.type == ScheduleWindowType.FIXED_LOCAL) {
            LocalTime.of(window.hour ?: 9, window.minute ?: 0)
        } else {
            MARKET_OPEN.plusMinutes(window.offsetMinutes?.toLong() ?: -10L)
        }
        return date.atTime(time).atZone(zone)
    }
}
