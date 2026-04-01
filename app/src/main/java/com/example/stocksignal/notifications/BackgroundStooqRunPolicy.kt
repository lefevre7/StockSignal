package com.example.stocksignal.notifications

import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.stooq.network.StooqRequestBlocker
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundStooqRunPolicy @Inject constructor(
    private val blocker: StooqRequestBlocker,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    fun windowSkipReason(settings: AppSettings, windowId: String): String? {
        blockedReason()?.let { return it }
        return weekendReason(settings, windowId)
    }

    fun premarketSkipReason(settings: AppSettings, windowId: String): String? {
        blockedReason()?.let { return it }
        return weekendReason(settings, windowId)
    }

    fun robotsSkipReason(): String? = blockedReason()

    private fun blockedReason(): String? {
        if (!blocker.isBlocked(clock.millis())) return null
        return "stooq blocked: ${blocker.buildBlockedMessage()}"
    }

    private fun weekendReason(settings: AppSettings, windowId: String): String? {
        if (settings.frequency != NotificationFrequency.THREE_PER_DAY &&
            settings.frequency != NotificationFrequency.ONE_PER_DAY
        ) {
            return null
        }
        val window = settings.scheduleWindows.firstOrNull { it.id == windowId } ?: return null
        val zone = when (window.type) {
            ScheduleWindowType.FIXED_LOCAL -> ZoneId.systemDefault()
            ScheduleWindowType.MARKET_OPEN_MINUS -> ZoneId.of(window.zoneId ?: "America/New_York")
        }
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zone)
        return if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) {
            "weekend"
        } else {
            null
        }
    }
}
