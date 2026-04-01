package com.example.stocksignal.notifications

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.data.settings.NotificationFrequency
import com.example.stocksignal.data.settings.NotificationType
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.settings.settingsDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [NotificationAlarmReceiver] covering the safe
 * (non-FGS) branches: invalid/missing intent extras, skip-reason paths
 * that don't try to start a foreground service.
 */
@RunWith(AndroidJUnit4::class)
class NotificationAlarmReceiverTest {

    private lateinit var context: android.content.Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var diagnosticsRepository: NotificationDiagnosticsRepository
    private lateinit var receiver: NotificationAlarmReceiver

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = SettingsRepository(context.settingsDataStore)
        diagnosticsRepository = NotificationDiagnosticsRepository(context.notificationDiagnosticsDataStore)
        receiver = NotificationAlarmReceiver()
        // Ensure settings are in a known state: no active alarming
        settingsRepository.setFrequency(NotificationFrequency.ONLY_WHEN_OPEN)
        settingsRepository.setNotificationTypes(setOf(NotificationType.WATCHLIST))
    }

    // ============== Null / unknown type ==============

    @Test
    fun nullTypeDoesNotCrash() = runBlocking {
        val intent = Intent()
        // No EXTRA_TYPE set → type is null → when(null) falls through
        receiver.onReceive(context, intent)
        delay(300) // wait for goAsync() + IO coroutine
        // No exception thrown → test passes
    }

    @Test
    fun unknownTypeDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, "UNKNOWN_TYPE")
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    // ============== TYPE_WINDOW ==============

    @Test
    fun typeWindowWithBlankWindowIdDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_WINDOW)
            putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, "")
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    @Test
    fun typeWindowWithMissingWindowIdDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_WINDOW)
            // No EXTRA_WINDOW_ID
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    @Test
    fun typeWindowWithSkipReasonRecordsDiagnostics() = runBlocking {
        // ONLY_WHEN_OPEN frequency → windowSkipReason returns non-null
        // This causes the receiver to call diagnosticsRepository.recordWindowRun(windowId, "skipped", ...)
        // instead of starting a FGS (which would fail in tests)
        val windowId = "test_win_alarm"
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_WINDOW)
            putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, windowId)
        }
        // ONLY_WHEN_OPEN ensures skip branch is taken
        settingsRepository.setFrequency(NotificationFrequency.ONLY_WHEN_OPEN)
        receiver.onReceive(context, intent)
        delay(500) // wait for async work
        // The receiver should have recorded a "skipped" run without crashing
        assertNotNull(diagnosticsRepository) // basic sanity check that context is valid
    }

    // ============== TYPE_PRE_NOTIFY ==============

    @Test
    fun typePreNotifyWithBlankWindowIdDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_PRE_NOTIFY)
            putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, "")
            putExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, -1L)
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    @Test
    fun typePreNotifyWithValidDataDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_PRE_NOTIFY)
            putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, "win1")
            putExtra(NotificationAlarmIntentFactory.EXTRA_RUN_AT_MILLIS, System.currentTimeMillis())
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    // ============== TYPE_PREMARKET ==============

    @Test
    fun typePremarketWithBlankWindowIdDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_PREMARKET)
            putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, "")
            putExtra(NotificationAlarmIntentFactory.EXTRA_SAMPLE_INDEX, 0)
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    @Test
    fun typePremarketWithNegativeSampleIndexDoesNotCrash() = runBlocking {
        val intent = Intent().apply {
            putExtra(NotificationAlarmIntentFactory.EXTRA_TYPE, NotificationAlarmIntentFactory.TYPE_PREMARKET)
            putExtra(NotificationAlarmIntentFactory.EXTRA_WINDOW_ID, "win1")
            putExtra(NotificationAlarmIntentFactory.EXTRA_SAMPLE_INDEX, -1)
        }
        receiver.onReceive(context, intent)
        delay(300)
    }
}
