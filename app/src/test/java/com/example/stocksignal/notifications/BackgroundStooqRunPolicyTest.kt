package com.example.stocksignal.notifications

import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.stooq.network.StooqRequestBlocker
import com.example.stocksignal.domain.model.ChartRange
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundStooqRunPolicyTest {

    @Test
    fun `window and robots skip while stooq is blocked`() {
        val diagnostics = mockDiagnostics()
        val blocker = StooqRequestBlocker(diagnostics)
        blocker.blockFor(Duration.ofHours(24), "Stooq blocked.")
        val policy = BackgroundStooqRunPolicy(blocker, Clock.systemDefaultZone())

        val reason = policy.windowSkipReason(defaultSettings(), "market_open_minus_10")

        assertTrue(reason?.contains("stooq blocked:") == true)
        assertTrue(policy.robotsSkipReason()?.contains("stooq blocked:") == true)
    }

    @Test
    fun `daily market windows skip on weekend`() {
        val blocker = StooqRequestBlocker(mockDiagnostics())
        val saturday = ZonedDateTime.of(2026, 4, 4, 9, 0, 0, 0, ZoneId.of("America/New_York"))
        val policy = BackgroundStooqRunPolicy(
            blocker,
            Clock.fixed(saturday.toInstant(), ZoneId.of("America/New_York"))
        )

        val settings = defaultSettings(frequency = NotificationFrequency.THREE_PER_DAY)

        assertEquals("weekend", policy.windowSkipReason(settings, "market_open_minus_10"))
        assertEquals("weekend", policy.premarketSkipReason(settings, "market_open_minus_10"))
    }

    @Test
    fun `weekly market windows remain allowed on weekend`() {
        val blocker = StooqRequestBlocker(mockDiagnostics())
        val saturday = ZonedDateTime.of(2026, 4, 4, 9, 0, 0, 0, ZoneId.of("America/New_York"))
        val policy = BackgroundStooqRunPolicy(
            blocker,
            Clock.fixed(saturday.toInstant(), ZoneId.of("America/New_York"))
        )

        val settings = defaultSettings(
            frequency = NotificationFrequency.ONE_PER_WEEK,
            weeklyDay = DayOfWeek.SATURDAY
        )

        assertNull(policy.windowSkipReason(settings, "market_open_minus_10"))
        assertNull(policy.premarketSkipReason(settings, "market_open_minus_10"))
    }

    private fun mockDiagnostics(): NotificationDiagnosticsRepository {
        return mockk(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
        }
    }

    private fun defaultSettings(
        frequency: NotificationFrequency = NotificationFrequency.THREE_PER_DAY,
        weeklyDay: DayOfWeek = DayOfWeek.MONDAY
    ): AppSettings {
        return AppSettings(
            frequency = frequency,
            notificationTypes = setOf(NotificationType.WATCHLIST, NotificationType.MARKET_MOVERS),
            quietHours = QuietHours(enabled = false, start = "22:00", end = "07:00"),
            scheduleWindows = listOf(
                ScheduleWindow(
                    id = "market_open_minus_10",
                    type = ScheduleWindowType.MARKET_OPEN_MINUS,
                    hour = null,
                    minute = null,
                    zoneId = "America/New_York",
                    offsetMinutes = -10
                )
            ),
            weeklyDay = weeklyDay,
            snoozeDuration = SnoozeDurationOption.TWENTY_FOUR_HOURS,
            signalSensitivity = SignalSensitivity(
                minScoreForNotify = 60,
                strongBuyThreshold = 60,
                strongSellThreshold = -60
            ),
            selectedChartRange = ChartRange.ONE_DAY,
            immediatePostsEnabled = false,
            offlineTranslationEnabled = false,
            onboardingCompleted = true,
            holdingPeriod = HoldingPeriod.DAYS
        )
    }
}
