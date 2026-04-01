package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.StockOverviewCacheDao
import com.example.stocksignal.data.local.entity.StockOverviewCacheEntity
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
import org.junit.Before
import org.junit.Test

class StockOverviewCacheRepositoryTest {

    private lateinit var dao: StockOverviewCacheDao
    private lateinit var repository: StockOverviewCacheRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk(relaxed = true)
        repository = StockOverviewCacheRepository(dao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `getCache returns entity from dao`() = runTest {
        val cache = sampleCache("AAPL")
        coEvery { dao.getCache("AAPL") } returns cache
        assertEquals(cache, repository.getCache("AAPL"))
    }

    @Test
    fun `getCache returns null on dao exception`() = runTest {
        coEvery { dao.getCache(any()) } throws RuntimeException("db error")
        assertNull(repository.getCache("AAPL"))
    }

    @Test
    fun `upsert delegates to dao`() = runTest {
        val cache = sampleCache("AAPL")
        coEvery { dao.upsert(cache) } returns Unit
        repository.upsert(cache)
        coVerify(exactly = 1) { dao.upsert(cache) }
    }

    @Test
    fun `upsert silently swallows dao exception`() = runTest {
        coEvery { dao.upsert(any()) } throws RuntimeException("db error")
        // Should not throw
        repository.upsert(sampleCache("AAPL"))
    }

    @Test
    fun `delete delegates to dao`() = runTest {
        coEvery { dao.delete("AAPL") } returns Unit
        repository.delete("AAPL")
        coVerify(exactly = 1) { dao.delete("AAPL") }
    }

    @Test
    fun `delete silently swallows dao exception`() = runTest {
        coEvery { dao.delete(any()) } throws RuntimeException("db error")
        repository.delete("AAPL")
    }

    @Test
    fun `clearAll delegates to dao`() = runTest {
        coEvery { dao.clearAll() } returns Unit
        repository.clearAll()
        coVerify(exactly = 1) { dao.clearAll() }
    }

    @Test
    fun `clearAll silently swallows dao exception`() = runTest {
        coEvery { dao.clearAll() } throws RuntimeException("db error")
        repository.clearAll()
    }

    private fun sampleCache(symbol: String) = StockOverviewCacheEntity(
        symbol = symbol,
        marketCap = 2_900_000_000_000.0,
        peRatio = 28.4,
        dividend = 0.52,
        week52High = 199.62,
        week52Low = 142.10,
        newsJson = null,
        fetchedAt = "2026-03-31T10:00:00"
    )
}
