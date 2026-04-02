package com.example.stocksignal.notifications

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreNotifyNotificationTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun `postPreNotifyNotification posts notification with correct id`() {
        val windowId = "market_open_minus_10"
        val runAtMillis = System.currentTimeMillis() + 30 * 60_000L

        NotificationAlarmReceiver.postPreNotifyNotification(context, windowId, runAtMillis)

        val shadow = shadowOf(notificationManager)
        val expectedId = NotificationAlarmReceiver.preNotifyNotificationId(windowId)
        val posted = shadow.getNotification(expectedId)
        assertNotNull("Pre-notify notification should be posted", posted)
    }

    @Test
    fun `cancelPreNotifyNotification removes posted notification`() {
        val windowId = "local_1100"
        val runAtMillis = System.currentTimeMillis() + 30 * 60_000L

        NotificationAlarmReceiver.postPreNotifyNotification(context, windowId, runAtMillis)
        val shadow = shadowOf(notificationManager)
        val expectedId = NotificationAlarmReceiver.preNotifyNotificationId(windowId)
        assertNotNull(shadow.getNotification(expectedId))

        NotificationAlarmReceiver.cancelPreNotifyNotification(context, windowId)
        assertNull(
            "Pre-notify notification should be canceled",
            shadow.getNotification(expectedId)
        )
    }

    @Test
    fun `preNotifyNotificationId is stable for same windowId`() {
        val id1 = NotificationAlarmReceiver.preNotifyNotificationId("win_a")
        val id2 = NotificationAlarmReceiver.preNotifyNotificationId("win_a")
        assertEquals(id1, id2)
    }

    @Test
    fun `preNotifyNotificationId differs for different windowIds`() {
        val id1 = NotificationAlarmReceiver.preNotifyNotificationId("win_a")
        val id2 = NotificationAlarmReceiver.preNotifyNotificationId("win_b")
        assertNotEquals(id1, id2)
    }

    @Test
    fun `postPreNotifyNotification notification content contains time`() {
        val windowId = "market_open_minus_10"
        val runAtMillis = System.currentTimeMillis() + 30 * 60_000L

        NotificationAlarmReceiver.postPreNotifyNotification(context, windowId, runAtMillis)

        val shadow = shadowOf(notificationManager)
        val expectedId = NotificationAlarmReceiver.preNotifyNotificationId(windowId)
        val notification = shadow.getNotification(expectedId)
        assertNotNull(notification)

        // Notification title should contain the window ID
        val extras = notification!!.extras
        val title = extras.getString("android.title") ?: ""
        assertTrue("Title should mention window id, got: $title", title.contains(windowId))
    }

    @Test
    fun `cancelPreNotifyNotification is safe when no notification exists`() {
        // Should not throw
        NotificationAlarmReceiver.cancelPreNotifyNotification(context, "nonexistent_window")
    }
}
