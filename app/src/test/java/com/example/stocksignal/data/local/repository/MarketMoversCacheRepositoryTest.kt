package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.MarketMoversCacheDao
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity
import com.example.stocksignal.data.local.model.MarketMoverItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class MarketMoversCacheRepositoryTest {

    private lateinit var dao: MarketMoversCacheDao
    private lateinit var repo: MarketMoversCacheRepository

    private val range = "1D"
    private val direction = "GAINERS"
    private val entity = MarketMoversCacheEntity(
        range = range,
        direction = direction,
        fetchedAt = LocalDateTime.of(2024, 6, 1, 10, 0),
        items = listOf(MarketMoverItem("AAPL.US", "Apple", "XNAS", 150.0, 2.5, 1, null, null))
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk()
        repo = MarketMoversCacheRepository(dao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // --- getCache ---

    @Test
    fun `getCache returns entity from dao`() = runTest {
        coEvery { dao.getCache(range, direction) } returns entity
        val result = repo.getCache(range, direction)
        assertEquals(entity, result)
    }

    @Test
    fun `getCache returns null on exception`() = runTest {
        coEvery { dao.getCache(range, direction) } throws RuntimeException("db error")
        val result = repo.getCache(range, direction)
        assertNull(result)
    }

    // --- upsert ---

    @Test
    fun `upsert delegates to dao`() = runTest {
        coEvery { dao.upsert(entity) } returns Unit
        repo.upsert(entity)
        coVerify(exactly = 1) { dao.upsert(entity) }
    }

    @Test
    fun `upsert rethrows exception from dao`() = runTest {
        coEvery { dao.upsert(entity) } throws RuntimeException("db error")
        assertThrows(RuntimeException::class.java) { runTest { repo.upsert(entity) } }
    }

    // --- delete ---

    @Test
    fun `delete delegates to dao`() = runTest {
        coEvery { dao.delete(range, direction) } returns Unit
        repo.delete(range, direction)
        coVerify(exactly = 1) { dao.delete(range, direction) }
    }

    @Test
    fun `delete rethrows exception from dao`() = runTest {
        coEvery { dao.delete(range, direction) } throws RuntimeException("db error")
        assertThrows(RuntimeException::class.java) { runTest { repo.delete(range, direction) } }
    }
}
