package com.example.stocksignal.data.stooq.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class StooqRequestBlockerTest {

    @Test
    fun clearBlockResetsBlockedState() {
        val blocker = StooqRequestBlocker()

        blocker.blockFor(Duration.ofMinutes(5), "test block")
        assertTrue(blocker.isBlocked())

        blocker.clearBlock()
        assertFalse(blocker.isBlocked())
    }
}
