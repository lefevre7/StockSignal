package com.example.stocksignal.data.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.stocksignal.domain.model.ChartRange
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        tempDir = createTempDir(prefix = "settings-repo-test")
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun `defaults are returned for a new store`() = runTest {
        val repository = SettingsRepository(createStore("defaults"))

        val settings = repository.settingsFlow.first()

        assertEquals(NotificationFrequency.THREE_PER_DAY, settings.frequency)
        assertEquals(
            setOf(NotificationType.WATCHLIST, NotificationType.MARKET_MOVERS, NotificationType.DIGESTS),
            settings.notificationTypes
        )
        assertEquals(false, settings.quietHours.enabled)
        assertEquals("22:00", settings.quietHours.start)
        assertEquals("07:00", settings.quietHours.end)
        assertEquals(DayOfWeek.MONDAY, settings.weeklyDay)
        assertEquals(SnoozeDurationOption.TWENTY_FOUR_HOURS, settings.snoozeDuration)
        assertEquals(60, settings.signalSensitivity.minScoreForNotify)
        assertEquals(ChartRange.SIX_MONTH, settings.selectedChartRange)
        assertEquals(false, settings.immediatePostsEnabled)
        assertEquals(true, settings.offlineTranslationEnabled)
        assertEquals(false, settings.onboardingCompleted)
        assertEquals(HoldingPeriod.MONTHS, settings.holdingPeriod)
        assertFalse(repository.isOfflineTranslationPreferenceSet())
        assertEquals(null, repository.getLastRobotsTxtCheckDate())
    }

    @Test
    fun `all setters persist values and holding period updates chart range`() = runTest {
        val repository = SettingsRepository(createStore("setters"))
        val windows = listOf(
            ScheduleWindow(
                id = "weekly_0900",
                type = ScheduleWindowType.FIXED_LOCAL,
                hour = 9,
                minute = 0,
                zoneId = null,
                offsetMinutes = null
            )
        )
        val quietHours = QuietHours(enabled = true, start = "21:30", end = "06:15")
        val sensitivity = SignalSensitivity(minScoreForNotify = 55, strongBuyThreshold = 70, strongSellThreshold = -75)
        val robotsDate = LocalDate.of(2026, 3, 31)

        repository.setFrequency(NotificationFrequency.ONE_PER_WEEK)
        repository.setNotificationTypes(setOf(NotificationType.WATCHLIST))
        repository.setQuietHours(quietHours)
        repository.setScheduleWindows(windows)
        repository.setWeeklyDay(DayOfWeek.FRIDAY)
        repository.setSnoozeDuration(SnoozeDurationOption.TWO_DAYS)
        repository.setSignalSensitivity(sensitivity)
        repository.setSelectedChartRange(ChartRange.ONE_MONTH)
        repository.setImmediatePostsEnabled(true)
        repository.setOfflineTranslationEnabled(false)
        repository.setOnboardingCompleted(true)
        repository.setHoldingPeriod(HoldingPeriod.YEARS)
        repository.setLastRobotsTxtCheckDate(robotsDate)

        val settings = repository.settingsFlow.first()

        assertEquals(NotificationFrequency.ONE_PER_WEEK, settings.frequency)
        assertEquals(setOf(NotificationType.WATCHLIST), settings.notificationTypes)
        assertEquals(quietHours, settings.quietHours)
        assertEquals(windows, settings.scheduleWindows)
        assertEquals(DayOfWeek.FRIDAY, settings.weeklyDay)
        assertEquals(SnoozeDurationOption.TWO_DAYS, settings.snoozeDuration)
        assertEquals(sensitivity, settings.signalSensitivity)
        // setHoldingPeriod is the last chart-range writer and intentionally normalizes the range.
        assertEquals(ChartRange.FIVE_YEAR, settings.selectedChartRange)
        assertEquals(true, settings.immediatePostsEnabled)
        assertEquals(false, settings.offlineTranslationEnabled)
        assertEquals(true, settings.onboardingCompleted)
        assertEquals(HoldingPeriod.YEARS, settings.holdingPeriod)
        assertTrue(repository.isOfflineTranslationPreferenceSet())
        assertEquals(robotsDate, repository.getLastRobotsTxtCheckDate())
    }

    @Test
    fun `legacy and invalid stored values fall back safely`() = runTest {
        val store = createStore("legacy")
        store.edit { prefs ->
            prefs[stringPreferencesKey("notification_frequency")] = "DEV_ONE_MINUTE"
            prefs[stringPreferencesKey("weekly_day")] = "NOT_A_DAY"
            prefs[stringPreferencesKey("snooze_duration")] = "BOGUS"
            prefs[stringPreferencesKey("selected_chart_range")] = "UNKNOWN_RANGE"
            prefs[stringPreferencesKey("holding_period")] = "UNKNOWN_HOLDING_PERIOD"
            prefs[stringPreferencesKey("schedule_windows")] = ""
        }
        val repository = SettingsRepository(store)

        val settings = repository.settingsFlow.first()

        assertEquals(NotificationFrequency.DEV_FIVE_MINUTES, settings.frequency)
        assertEquals(DayOfWeek.MONDAY, settings.weeklyDay)
        assertEquals(SnoozeDurationOption.TWENTY_FOUR_HOURS, settings.snoozeDuration)
        // Unknown holding period falls back to MONTHS; unknown chart range then falls back to the
        // holding-period default (SIX_MONTH for MONTHS) rather than a hardcoded ONE_DAY.
        assertEquals(ChartRange.SIX_MONTH, settings.selectedChartRange)
        assertEquals(HoldingPeriod.MONTHS, settings.holdingPeriod)
        assertEquals(3, settings.scheduleWindows.size)
    }

    private fun createStore(name: String): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(tempDir, "$name-${UUID.randomUUID()}.preferences_pb") }
        )
    }
}
