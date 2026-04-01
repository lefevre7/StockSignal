package com.example.stocksignal.notifications

import android.app.NotificationManager
import android.os.Build
import com.example.stocksignal.data.settings.SettingsRepository
import com.example.stocksignal.data.stooq.network.StooqApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class RobotsTxtCheckRunnerTest {

    private lateinit var stooqApi: StooqApi
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var diagnosticsRepository: NotificationDiagnosticsRepository
    private lateinit var backgroundRunPolicy: BackgroundStooqRunPolicy
    private lateinit var runner: RobotsTxtCheckRunner

    @Before
    fun setUp() {
        stooqApi = mockk()
        settingsRepository = mockk(relaxed = true)
        diagnosticsRepository = mockk(relaxed = true)
        backgroundRunPolicy = mockk()
        runner = RobotsTxtCheckRunner(
            context = RuntimeEnvironment.getApplication(),
            stooqApi = stooqApi,
            settingsRepository = settingsRepository,
            diagnosticsRepository = diagnosticsRepository,
            backgroundRunPolicy = backgroundRunPolicy
        )
    }

    private val expectedRobots = "User-agent: *\r\nDisallow:\r\n"

    // ---- skip reason ----

    @Test
    fun `returns SUCCESS immediately when backgroundRunPolicy provides skip reason`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } returns "blocked"

        val result = runner.run()

        assertEquals(RobotsTxtCheckRunner.RunOutcome.SUCCESS, result)
        coVerify(exactly = 0) { stooqApi.getRobotsTxt() }
    }

    // ---- already checked today ----

    @Test
    fun `returns SUCCESS without network call when already checked today`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } returns null
        coEvery { settingsRepository.getLastRobotsTxtCheckDate() } returns LocalDate.now()

        val result = runner.run()

        assertEquals(RobotsTxtCheckRunner.RunOutcome.SUCCESS, result)
        coVerify(exactly = 0) { stooqApi.getRobotsTxt() }
    }

    // ---- network error ----

    @Test
    fun `returns FAILURE when network call throws`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } returns null
        coEvery { settingsRepository.getLastRobotsTxtCheckDate() } returns null
        coEvery { stooqApi.getRobotsTxt() } throws RuntimeException("timeout")

        val result = runner.run()

        assertEquals(RobotsTxtCheckRunner.RunOutcome.FAILURE, result)
        coVerify(exactly = 0) { settingsRepository.setLastRobotsTxtCheckDate(any()) }
    }

    // ---- content changed ----

    @Test
    fun `returns SUCCESS and persists date when robots txt has changed`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } returns null
        coEvery { settingsRepository.getLastRobotsTxtCheckDate() } returns null
        coEvery { stooqApi.getRobotsTxt() } returns "User-agent: *\r\nDisallow: /private\r\n"

        val result = runner.run()

        // Runner returns SUCCESS even when content changed (it just alerts the user)
        assertEquals(RobotsTxtCheckRunner.RunOutcome.SUCCESS, result)
        // Date should be persisted regardless of whether content matched
        coVerify(exactly = 1) { settingsRepository.setLastRobotsTxtCheckDate(any()) }
    }

    @Test
    fun `posts notification when robots txt changed and app is in background`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } returns null
        coEvery { settingsRepository.getLastRobotsTxtCheckDate() } returns null
        coEvery { stooqApi.getRobotsTxt() } returns "User-agent: *\r\nDisallow: /private\r\n"

        // Force background state by clearing the running app processes list
        val am = RuntimeEnvironment.getApplication()
            .getSystemService(android.app.ActivityManager::class.java)
        val shadow = Shadows.shadowOf(am)
        shadow.setProcesses(emptyList())

        runner.run()

        val nm = RuntimeEnvironment.getApplication()
            .getSystemService(NotificationManager::class.java)
        val nmShadow = Shadows.shadowOf(nm)
        assertEquals(1, nmShadow.allNotifications.size)
    }

    // ---- content matches ----

    @Test
    fun `returns SUCCESS and persists date when robots txt matches expected`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } returns null
        coEvery { settingsRepository.getLastRobotsTxtCheckDate() } returns null
        coEvery { stooqApi.getRobotsTxt() } returns expectedRobots

        val result = runner.run()

        assertEquals(RobotsTxtCheckRunner.RunOutcome.SUCCESS, result)
        coVerify(exactly = 1) { settingsRepository.setLastRobotsTxtCheckDate(any()) }
    }

    // ---- general exception ----

    @Test
    fun `returns FAILURE when an unexpected exception occurs`() = runTest {
        every { backgroundRunPolicy.robotsSkipReason() } answers { throw RuntimeException("unexpected") }

        val result = runner.run()

        assertEquals(RobotsTxtCheckRunner.RunOutcome.FAILURE, result)
    }
}
