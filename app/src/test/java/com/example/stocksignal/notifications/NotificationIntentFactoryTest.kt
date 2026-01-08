package com.example.stocksignal.notifications

import android.app.PendingIntent
import android.content.Intent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class NotificationIntentFactoryTest {

    @Test
    fun `dismiss intent includes action and extras`() {
        val context = RuntimeEnvironment.getApplication()
        val pendingIntent = NotificationIntentFactory.dismissIntent(
            context,
            notificationId = 12,
            eventIds = arrayOf("evt_1", "evt_2")
        )

        val saved = extractIntent(pendingIntent)
        assertEquals(NotificationActionReceiver.ACTION_DISMISS, saved.action)
        assertEquals(12, saved.getIntExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0))
        assertArrayEquals(
            arrayOf("evt_1", "evt_2"),
            saved.getStringArrayExtra(NotificationActionReceiver.EXTRA_EVENT_IDS)
        )
    }

    @Test
    fun `add to watchlist intent includes ticker and company`() {
        val context = RuntimeEnvironment.getApplication()
        val pendingIntent = NotificationIntentFactory.addToWatchlistIntent(
            context,
            notificationId = 7,
            ticker = "TSLA",
            companyName = "Tesla, Inc."
        )

        val saved = extractIntent(pendingIntent)
        assertEquals(NotificationActionReceiver.ACTION_ADD_WATCHLIST, saved.action)
        assertEquals("TSLA", saved.getStringExtra(NotificationActionReceiver.EXTRA_TICKER))
        assertEquals("Tesla, Inc.", saved.getStringExtra(NotificationActionReceiver.EXTRA_COMPANY))
    }

    private fun extractIntent(pendingIntent: PendingIntent): Intent {
        val shadow = Shadows.shadowOf(pendingIntent)
        return shadow.savedIntent
    }
}
