package com.example.stocksignal.data.settings

import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.domain.model.ChartRange

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
    val signalSensitivity: SignalSensitivity,
    val selectedChartRange: ChartRange,
    val selectedMarketMoverRange: MarketMoverRange,
    val immediatePostsEnabled: Boolean,
    val onboardingCompleted: Boolean
)
