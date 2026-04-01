package com.example.stocksignal.data.stooq.network

import android.app.NotificationManager
import android.os.Build
import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class StooqBlockReporterTest {

    private lateinit var reporter: StooqBlockReporter
    private lateinit var diagnosticsRepository: NotificationDiagnosticsRepository

    @Before
    fun setUp() {
        diagnosticsRepository = mockk(relaxed = true)
        reporter = StooqBlockReporter(
            context = RuntimeEnvironment.getApplication(),
            diagnosticsRepository = diagnosticsRepository
        )
    }

    @Test
    fun `reportBlocked creates notification channel and posts notification`() {
        reporter.reportBlocked("Stooq blocked", null)

        // Allow the IO scope coroutine to complete
        Thread.sleep(200)

        val nm = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val shadow = Shadows.shadowOf(nm)
        val notifs = shadow.allNotifications
        assertEquals(1, notifs.size)
    }

    @Test
    fun `reportBlocked within throttle window does not post second notification`() {
        reporter.reportBlocked("First block", null)
        Thread.sleep(100)
        reporter.reportBlocked("Second block within throttle", null)
        Thread.sleep(200)

        val nm = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val shadow = Shadows.shadowOf(nm)
        // Only the first call should have posted
        assertEquals(1, shadow.allNotifications.size)
    }

    @Test
    fun `reportBlocked records diagnostics with message and blockedUntilMillis`() {
        val until = System.currentTimeMillis() + 86_400_000L
        reporter.reportBlocked("Rate limited", until)
        Thread.sleep(200)

        coVerify(atLeast = 1) {
            diagnosticsRepository.recordStooqBlocked("Rate limited", until)
        }
    }

    @Test
    fun `reportBlocked with null blockedUntilMillis still records diagnostics`() {
        reporter.reportBlocked("Blocked with no end time", null)
        Thread.sleep(200)

        coVerify(atLeast = 1) {
            diagnosticsRepository.recordStooqBlocked("Blocked with no end time", null)
        }
    }
}
