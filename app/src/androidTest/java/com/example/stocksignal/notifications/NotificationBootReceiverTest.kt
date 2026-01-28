package com.example.stocksignal.notifications

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.ScheduleWindow
import com.example.stocksignal.data.settings.ScheduleWindowType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.settingsDataStore
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/**
 * Tests for NotificationBootReceiver to ensure it properly:
 * - Responds only to BOOT_COMPLETED intent
 * - Schedules notification alarms based on current settings
 */
@RunWith(AndroidJUnit4::class)
class NotificationBootReceiverTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var receiver: NotificationBootReceiver
    private lateinit var diagnosticsRepository: NotificationDiagnosticsRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        settingsRepository = SettingsRepository(context.settingsDataStore)
        diagnosticsRepository = NotificationDiagnosticsRepository(context.settingsDataStore)
        receiver = NotificationBootReceiver()
    }

    @Test
    fun bootReceiverSchedulesAlarmsOnBootCompleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        disableBackgroundScheduling()

        settingsRepository.setFrequency(NotificationFrequency.THREE_PER_DAY)
        settingsRepository.setNotificationTypes(
            setOf(
                NotificationType.WATCHLIST,
                NotificationType.MARKET_MOVERS,
                NotificationType.DIGESTS
            )
        )
        settingsRepository.setScheduleWindows(
            listOf(
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
            )
        )

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        awaitWindowIds(setOf("local_1100", "local_1400"))
    }

    @Test
    fun bootReceiverIgnoresNonBootIntents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        disableBackgroundScheduling()
        receiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        kotlinx.coroutines.delay(200)
        val scheduled = diagnosticsRepository.getScheduledWindowIds()
        assertTrue("No alarms should be scheduled for non-boot intent", scheduled.isEmpty())
    }

    @Test
    fun bootReceiverHandlesNullActionGracefully() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        disableBackgroundScheduling()
        val intent = Intent()
        intent.action = null

        // Should not crash
        receiver.onReceive(context, intent)
        val scheduled = diagnosticsRepository.getScheduledWindowIds()
        assertTrue("No alarms should be scheduled for null action", scheduled.isEmpty())
    }

    private suspend fun awaitWindowIds(expected: Set<String>, timeoutMs: Long = 5_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val scheduled = diagnosticsRepository.getScheduledWindowIds()
            if (scheduled.containsAll(expected)) return
            kotlinx.coroutines.delay(100)
        }
        fail("Timed out waiting for alarms: expected $expected")
    }

    private suspend fun disableBackgroundScheduling() {
        diagnosticsRepository.clearScheduleFingerprint()
        diagnosticsRepository.setScheduledWindowIds(emptySet())
        diagnosticsRepository.setScheduledPremarketKeys(emptySet())
        diagnosticsRepository.setRobotsNextRun(null)
        settingsRepository.setNotificationTypes(
            setOf(NotificationType.WATCHLIST)
        )
        settingsRepository.setFrequency(NotificationFrequency.ONLY_WHEN_OPEN)
        awaitEmptyScheduled()
    }

    private suspend fun awaitEmptyScheduled(timeoutMs: Long = 5_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val scheduled = diagnosticsRepository.getScheduledWindowIds()
            if (scheduled.isEmpty()) return
            kotlinx.coroutines.delay(100)
        }
        fail("Timed out waiting for alarms to clear")
    }
}
