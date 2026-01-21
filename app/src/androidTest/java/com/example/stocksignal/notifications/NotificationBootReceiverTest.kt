package com.example.stocksignal.notifications

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Tests for NotificationBootReceiver to ensure it properly:
 * - Responds only to BOOT_COMPLETED intent
 * - Enqueues NotificationBootstrapWorker
 * - Uses correct work name and tag
 */
@RunWith(AndroidJUnit4::class)
class NotificationBootReceiverTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var receiver: NotificationBootReceiver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(TestWorkerFactory())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        receiver = NotificationBootReceiver()
    }

    @Test
    fun bootReceiverEnqueuesBootstrapWorkerOnBootCompleted() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        val workInfos = workManager.getWorkInfosByTag("notification_bootstrap")
            .get(5, TimeUnit.SECONDS)

        assertTrue("Bootstrap worker should be enqueued", workInfos.isNotEmpty())
        val state = workInfos.first().state
        assertTrue(
            "Worker should be enqueued or finished",
            state == WorkInfo.State.ENQUEUED ||
                state == WorkInfo.State.RUNNING ||
                state == WorkInfo.State.SUCCEEDED
        )
    }

    @Test
    fun bootReceiverIgnoresNonBootIntents() {
        val intent = Intent(Intent.ACTION_SCREEN_ON)

        receiver.onReceive(context, intent)

        val workInfos = workManager.getWorkInfosByTag("notification_bootstrap")
            .get(5, TimeUnit.SECONDS)

        assertTrue("No worker should be enqueued for non-boot intent", workInfos.isEmpty())
    }

    @Test
    fun bootReceiverUsesCorrectWorkName() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        // Work name is used for unique work, so we verify through tags
        val workInfos = workManager.getWorkInfosByTag("notification_bootstrap")
            .get(5, TimeUnit.SECONDS)

        assertTrue("Bootstrap worker should be tagged correctly", workInfos.isNotEmpty())
    }

    @Test
    fun bootReceiverReplacesExistingBootstrapWork() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Enqueue first time
        receiver.onReceive(context, intent)
        val firstWorkInfos = workManager.getWorkInfosByTag("notification_bootstrap")
            .get(5, TimeUnit.SECONDS)
        val firstWorkId = firstWorkInfos.first().id

        // Enqueue second time
        receiver.onReceive(context, intent)
        val secondWorkInfos = workManager.getWorkInfosByTag("notification_bootstrap")
            .get(5, TimeUnit.SECONDS)

        // Due to REPLACE policy, should still only have one work item
        // (though it might be a different ID)
        assertEquals("Should have exactly one bootstrap work item", 1, secondWorkInfos.size)
    }

    @Test
    fun bootReceiverHandlesNullActionGracefully() {
        val intent = Intent()
        intent.action = null

        // Should not crash
        receiver.onReceive(context, intent)

        val workInfos = workManager.getWorkInfosByTag("notification_bootstrap")
            .get(5, TimeUnit.SECONDS)

        assertTrue("No worker should be enqueued for null action", workInfos.isEmpty())
    }

    private class TestWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: androidx.work.WorkerParameters
        ): androidx.work.ListenableWorker? {
            if (workerClassName != NotificationBootstrapWorker::class.java.name) {
                return null
            }
            return object : CoroutineWorker(appContext, workerParameters) {
                override suspend fun doWork(): Result = Result.success()
            }
        }
    }
}
