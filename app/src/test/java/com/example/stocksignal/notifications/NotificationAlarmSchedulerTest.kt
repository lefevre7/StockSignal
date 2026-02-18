package com.example.stocksignal.notifications

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import com.example.stocksignal.data.settings.AppSettings
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.QuietHours
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SignalSensitivity
import com.example.stocksignal.data.settings.SnoozeDurationOption
import com.example.stocksignal.data.settings.settingsDataStore
import com.example.stocksignal.domain.model.ChartRange
import java.time.DayOfWeek
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowPendingIntent

@Config(sdk = [Build.VERSION_CODES.M])
@RunWith(RobolectricTestRunner::class)
class NotificationAlarmSchedulerTest {

    @Suppress("DEPRECATION")
    @Test
    fun scheduleEnqueuesWindowAndRobotsAlarms() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val diagnostics = NotificationDiagnosticsRepository(context.settingsDataStore)
        val scheduler = NotificationScheduler(context, diagnostics)
        clearScheduledAlarms()
        
        // Use times that are guaranteed to be at least 2 hours in the future
        // to ensure pre-notify alarms can be scheduled (30 min lead time)
        val now = LocalTime.now()
        val firstWindowTime = now.plusHours(2)
        val secondWindowTime = now.plusHours(5)
        
        val settings = baseSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            windows = listOf(
                ScheduleWindow(
                    id = "local_future_1",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = firstWindowTime.hour,
                    minute = firstWindowTime.minute,
                    zoneId = null,
                    offsetMinutes = null
                ),
                ScheduleWindow(
                    id = "local_future_2",
                    type = ScheduleWindowType.FIXED_LOCAL,
                    hour = secondWindowTime.hour,
                    minute = secondWindowTime.minute,
                    zoneId = null,
                    offsetMinutes = null
                )
            )
        )

        scheduler.schedule(settings, force = true)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        val types = scheduled.mapNotNull {
            val pendingIntent = it.operation ?: return@mapNotNull null
            val shadowIntent = shadowOf(pendingIntent) as ShadowPendingIntent
            shadowIntent.savedIntent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE)
        }

        assertEquals("types=$types", 5, types.size)
        assertEquals(2, types.count { it == NotificationAlarmIntentFactory.TYPE_WINDOW })
        assertEquals(2, types.count { it == NotificationAlarmIntentFactory.TYPE_PRE_NOTIFY })
        assertEquals(1, types.count { it == NotificationAlarmIntentFactory.TYPE_ROBOTS })
    }

    @Suppress("DEPRECATION")
    @Test
    fun scheduleEnqueuesPremarketAlarmsWhenMarketWindowPresent() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val diagnostics = NotificationDiagnosticsRepository(context.settingsDataStore)
        val scheduler = NotificationScheduler(context, diagnostics)
        clearScheduledAlarms()
        val settings = baseSettings(
            frequency = NotificationFrequency.THREE_PER_DAY,
            windows = listOf(
                ScheduleWindow(
                    id = "market_open_minus_10",
                    type = ScheduleWindowType.MARKET_OPEN_MINUS,
                    hour = null,
                    minute = null,
                    zoneId = "America/New_York",
                    offsetMinutes = -10
                )
            )
        )

        scheduler.schedule(settings, force = true)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        val types = scheduled.mapNotNull {
            val pendingIntent = it.operation ?: return@mapNotNull null
            val shadowIntent = shadowOf(pendingIntent) as ShadowPendingIntent
            shadowIntent.savedIntent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE)
        }

        assertEquals(8, types.size)
        assertEquals(1, types.count { it == NotificationAlarmIntentFactory.TYPE_WINDOW })
        assertEquals(1, types.count { it == NotificationAlarmIntentFactory.TYPE_PRE_NOTIFY })
        assertEquals(1, types.count { it == NotificationAlarmIntentFactory.TYPE_ROBOTS })
        assertEquals(5, types.count { it == NotificationAlarmIntentFactory.TYPE_PREMARKET })
    }

    @Suppress("DEPRECATION")
    @Test
    fun premarketAlarmsNotScheduledOnSaturday() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val diagnostics = NotificationDiagnosticsRepository(context.settingsDataStore)
        
        // Set clock to Friday 2025-01-03 14:00 ET - next market open would be Saturday if not skipped
        val friday = ZonedDateTime.of(2025, 1, 3, 14, 0, 0, 0, ZoneId.of("America/New_York"))
        val fixedClock = Clock.fixed(friday.toInstant(), ZoneId.of("America/New_York"))
        val scheduler = NotificationScheduler(context, diagnostics, fixedClock)
        clearScheduledAlarms()
        
        val settings = baseSettings(
            frequency = NotificationFrequency.ONE_PER_DAY,
            windows = listOf(
                ScheduleWindow(
                    id = "market_open_minus_10",
                    type = ScheduleWindowType.MARKET_OPEN_MINUS,
                    hour = null,
                    minute = null,
                    zoneId = "America/New_York",
                    offsetMinutes = -10
                )
            )
        )

        scheduler.schedule(settings, force = true)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        val premarketAlarms = scheduled.filter {
            val pendingIntent = it.operation ?: return@filter false
            val shadowIntent = shadowOf(pendingIntent) as ShadowPendingIntent
            shadowIntent.savedIntent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE) == 
                NotificationAlarmIntentFactory.TYPE_PREMARKET
        }

        // Verify all premarket alarms are scheduled for Monday, not Saturday/Sunday
        premarketAlarms.forEachIndexed { idx, alarm ->
            val scheduledTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(alarm.triggerAtTime),
                ZoneId.of("America/New_York")
            )
            println("Premarket alarm $idx scheduled for: $scheduledTime (${scheduledTime.dayOfWeek})")
            assertTrue(
                "Premarket alarm should not be on weekend, got ${scheduledTime.dayOfWeek}",
                scheduledTime.dayOfWeek != DayOfWeek.SATURDAY && 
                scheduledTime.dayOfWeek != DayOfWeek.SUNDAY
            )
            // Should be Monday
            assertEquals(
                "Premarket alarm $idx should be on Monday when calculated on Friday evening, but was ${scheduledTime.dayOfWeek} at $scheduledTime",
                DayOfWeek.MONDAY,
                scheduledTime.dayOfWeek
            )
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun premarketAlarmsNotScheduledOnSunday() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val diagnostics = NotificationDiagnosticsRepository(context.settingsDataStore)
        
        // Set clock to Saturday 2025-01-04 10:00 ET - next market open would be Sunday if not skipped
        val saturday = ZonedDateTime.of(2025, 1, 4, 10, 0, 0, 0, ZoneId.of("America/New_York"))
        val fixedClock = Clock.fixed(saturday.toInstant(), ZoneId.of("America/New_York"))
        val scheduler = NotificationScheduler(context, diagnostics, fixedClock)
        clearScheduledAlarms()
        
        val settings = baseSettings(
            frequency = NotificationFrequency.ONE_PER_DAY,
            windows = listOf(
                ScheduleWindow(
                    id = "market_open_minus_10",
                    type = ScheduleWindowType.MARKET_OPEN_MINUS,
                    hour = null,
                    minute = null,
                    zoneId = "America/New_York",
                    offsetMinutes = -10
                )
            )
        )

        scheduler.schedule(settings, force = true)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        val premarketAlarms = scheduled.filter {
            val pendingIntent = it.operation ?: return@filter false
            val shadowIntent = shadowOf(pendingIntent) as ShadowPendingIntent
            shadowIntent.savedIntent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE) == 
                NotificationAlarmIntentFactory.TYPE_PREMARKET
        }

        // Verify all premarket alarms are scheduled for Monday, not Saturday/Sunday
        premarketAlarms.forEach { alarm ->
            val scheduledTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(alarm.triggerAtTime),
                ZoneId.of("America/New_York")
            )
            assertTrue(
                "Premarket alarm should not be on weekend, got ${scheduledTime.dayOfWeek}",
                scheduledTime.dayOfWeek != DayOfWeek.SATURDAY && 
                scheduledTime.dayOfWeek != DayOfWeek.SUNDAY
            )
            // Should be Monday
            assertEquals(
                "Premarket alarm should be on Monday when calculated on Saturday",
                DayOfWeek.MONDAY,
                scheduledTime.dayOfWeek
            )
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun premarketAlarmsScheduledNormallyOnWeekdays() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val diagnostics = NotificationDiagnosticsRepository(context.settingsDataStore)
        
        // Set clock to Tuesday 2025-01-07 10:00 ET
        val tuesday = ZonedDateTime.of(2025, 1, 7, 10, 0, 0, 0, ZoneId.of("America/New_York"))
        val fixedClock = Clock.fixed(tuesday.toInstant(), ZoneId.of("America/New_York"))
        val scheduler = NotificationScheduler(context, diagnostics, fixedClock)
        clearScheduledAlarms()
        
        val settings = baseSettings(
            frequency = NotificationFrequency.ONE_PER_DAY,
            windows = listOf(
                ScheduleWindow(
                    id = "market_open_minus_10",
                    type = ScheduleWindowType.MARKET_OPEN_MINUS,
                    hour = null,
                    minute = null,
                    zoneId = "America/New_York",
                    offsetMinutes = -10
                )
            )
        )

        scheduler.schedule(settings, force = true)

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        val premarketAlarms = scheduled.filter {
            val pendingIntent = it.operation ?: return@filter false
            val shadowIntent = shadowOf(pendingIntent) as ShadowPendingIntent
            shadowIntent.savedIntent.getStringExtra(NotificationAlarmIntentFactory.EXTRA_TYPE) == 
                NotificationAlarmIntentFactory.TYPE_PREMARKET
        }

        // Verify premarket alarms are scheduled (should be 5 of them)
        assertEquals("Should have 5 premarket alarms", 5, premarketAlarms.size)
        
        // All should be on Wednesday (next weekday after Tuesday 10:00)
        premarketAlarms.forEach { alarm ->
            val scheduledTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(alarm.triggerAtTime),
                ZoneId.of("America/New_York")
            )
            assertTrue(
                "Premarket alarm should be on a weekday, got ${scheduledTime.dayOfWeek}",
                scheduledTime.dayOfWeek != DayOfWeek.SATURDAY && 
                scheduledTime.dayOfWeek != DayOfWeek.SUNDAY
            )
        }
    }

    private fun baseSettings(
        frequency: NotificationFrequency,
        windows: List<ScheduleWindow>
    ): AppSettings {
        return AppSettings(
            frequency = frequency,
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
            scheduleWindows = windows,
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
    }

    private fun clearScheduledAlarms() {
        ShadowAlarmManager.reset()
    }
}
