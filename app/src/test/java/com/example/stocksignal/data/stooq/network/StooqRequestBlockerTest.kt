package com.example.stocksignal.data.stooq.network

import com.example.stocksignal.notifications.NotificationDiagnosticsRepository
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class StooqRequestBlockerTest {

    @Test
    fun clearBlockResetsBlockedState() {
        val diagnostics = mockk<NotificationDiagnosticsRepository>(relaxed = true) {
            coEvery { getStooqBlockedInfo() } returns NotificationDiagnosticsRepository.StooqBlockedInfo(
                blockedAtMillis = null,
                blockedUntilMillis = null,
                message = null
            )
            coEvery { clearStooqBlocked() } returns Unit
        }
        val blocker = StooqRequestBlocker(diagnostics)

        blocker.blockFor(Duration.ofMinutes(5), "test block")
        assertTrue(blocker.isBlocked())

        blocker.clearBlock()
        assertFalse(blocker.isBlocked())
    }
}
