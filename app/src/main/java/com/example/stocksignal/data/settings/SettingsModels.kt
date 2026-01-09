package com.example.stocksignal.data.settings

import com.example.stocksignal.domain.model.ChartRange
import java.time.DayOfWeek

enum class NotificationFrequency {
    THREE_PER_DAY,
    ONE_PER_DAY,
    ONE_PER_WEEK,
    ONLY_WHEN_OPEN
}

enum class NotificationType {
    WATCHLIST,
    MARKET_MOVERS,
    DIGESTS
}

enum class ScheduleWindowType {
    FIXED_LOCAL,
    MARKET_OPEN_MINUS
}

enum class SnoozeDurationOption(val minutes: Long, val label: String) {
    ONE_HOUR(60, "1h"),
    FIVE_HOURS(300, "5h"),
    TEN_HOURS(600, "10h"),
    FIFTEEN_HOURS(900, "15h"),
    TWENTY_FOUR_HOURS(1440, "24h"),
    TWO_DAYS(2880, "2d"),
    THREE_DAYS(4320, "3d"),
    ONE_WEEK(10080, "1w")
}

data class QuietHours(
    val enabled: Boolean,
    val start: String,
    val end: String
)

data class ScheduleWindow(
    val id: String,
    val type: ScheduleWindowType,
    val hour: Int?,
    val minute: Int?,
    val zoneId: String?,
    val offsetMinutes: Int?
)

data class SignalSensitivity(
    val minScoreForNotify: Int,
    val strongBuyThreshold: Int,
    val strongSellThreshold: Int
)

data class AppSettings(
    val frequency: NotificationFrequency,
    val notificationTypes: Set<NotificationType>,
    val quietHours: QuietHours,
    val scheduleWindows: List<ScheduleWindow>,
    val weeklyDay: DayOfWeek,
    val snoozeDuration: SnoozeDurationOption,
    val signalSensitivity: SignalSensitivity,
    val selectedChartRange: ChartRange,
    val immediatePostsEnabled: Boolean,
    val onboardingCompleted: Boolean
)
