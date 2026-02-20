package com.example.stocksignal.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.domain.model.ChartRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class NotificationSchedulerTest {

    @Test
    fun scheduleStoresWindowAndRobotsAlarms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val diagnosticsRepository = NotificationDiagnosticsRepository(context.notificationDiagnosticsDataStore)
        val scheduler = NotificationScheduler(context, diagnosticsRepository)
        val settings = AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(
                NotificationType.WATCHLIST,
                NotificationType.MARKET_MOVERS,
                NotificationType.DIGESTS
            ),
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
            scheduleWindows = listOf(
                ScheduleWindow(
                    id = "local_1100",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = 11,
                    minute = 0,
                    zoneId = null,
                    offsetMinutes = null
                ),
                ScheduleWindow(
                    id = "local_1400",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = 14,
                    minute = 0,
                    zoneId = null,
                    offsetMinutes = null
                )
            ),
            weeklyDay = DayOfWeek.MONDAY,
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

        runBlocking {
            diagnosticsRepository.clearScheduleFingerprint()
            diagnosticsRepository.setScheduledWindowIds(emptySet())
            diagnosticsRepository.setScheduledPremarketKeys(emptySet())
            diagnosticsRepository.setRobotsNextRun(null)
            scheduler.schedule(settings, force = true)
        }

        runBlocking {
            val scheduled = diagnosticsRepository.getScheduledWindowIds()
            assertEquals(setOf("local_1100", "local_1400"), scheduled)
            val nextRuns = diagnosticsRepository.getNextWindowRunTimes(scheduled)
            assertTrue(nextRuns.values.all { it != null })
            val robotsNext = diagnosticsRepository.getRobotsNextRun()
            assertTrue(robotsNext != null)
        }
    }

    @Test
    fun scheduleStoresPremarketAlarms() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val diagnosticsRepository = NotificationDiagnosticsRepository(context.notificationDiagnosticsDataStore)
        val scheduler = NotificationScheduler(context, diagnosticsRepository)
        val settings = AppSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            notificationTypes = setOf(
                NotificationType.WATCHLIST,
                NotificationType.MARKET_MOVERS,
                NotificationType.DIGESTS
            ),
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00"
            ),
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
            weeklyDay = DayOfWeek.MONDAY,
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

        diagnosticsRepository.clearScheduleFingerprint()
        diagnosticsRepository.setScheduledWindowIds(emptySet())
        diagnosticsRepository.setScheduledPremarketKeys(emptySet())
        diagnosticsRepository.setRobotsNextRun(null)
        scheduler.schedule(settings, force = true)

        val expectedKey = NotificationAlarmIntentFactory.premarketKey("market_open_minus_10", 0)
        val scheduledKeys = diagnosticsRepository.getScheduledPremarketKeys()
        assertTrue(scheduledKeys.contains(expectedKey))
        val nextRuns = diagnosticsRepository.getPremarketNextRuns(scheduledKeys)
        assertTrue(nextRuns.values.all { it != null })
    }
}
