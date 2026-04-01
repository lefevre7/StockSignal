package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.NotificationStateDao
import com.example.stocksignal.data.local.entity.NotificationStateEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class NotificationStateRepositoryTest {

    private lateinit var dao: NotificationStateDao
    private lateinit var repository: NotificationStateRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk(relaxed = true)
        repository = NotificationStateRepository(dao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `getState returns state from dao`() = runTest {
        val state = sampleState()
        coEvery { dao.getState() } returns state
        assertEquals(state, repository.getState())
    }

    @Test
    fun `getState returns null on dao exception`() = runTest {
        coEvery { dao.getState() } throws RuntimeException("db error")
        assertNull(repository.getState())
    }

    @Test
    fun `upsert delegates to dao`() = runTest {
        val state = sampleState()
        coEvery { dao.upsert(state) } returns Unit
        repository.upsert(state)
        coVerify(exactly = 1) { dao.upsert(state) }
    }

    @Test
    fun `upsert rethrows dao exception`() = runTest {
        coEvery { dao.upsert(any()) } throws RuntimeException("db error")
        var thrown: Exception? = null
        try { repository.upsert(sampleState()) }
        catch (e: Exception) { thrown = e }
        assertTrue(thrown is RuntimeException)
    }

    private fun sampleState() = NotificationStateEntity(
        id = 1,
        lastActiveNotificationId = 42,
        lastActiveAt = LocalDateTime.of(2026, 3, 31, 10, 0),
        dismissed = false,
        lastResetAt = null
    )
}
