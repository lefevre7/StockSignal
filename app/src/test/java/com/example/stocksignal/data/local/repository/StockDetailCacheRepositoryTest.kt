package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.StockDetailCacheDao
import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
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

class StockDetailCacheRepositoryTest {

    private lateinit var dao: StockDetailCacheDao
    private lateinit var repo: StockDetailCacheRepository

    private val symbol = "TSLA.US"
    private val range = "1D"
    private val entity = StockDetailCacheEntity(
        symbol = symbol,
        range = range,
        fetchedAt = LocalDateTime.of(2024, 6, 1, 10, 0),
        seriesJson = "[]",
        latestPrice = 250.0,
        indicatorsJson = null,
        signalHistoryJson = null
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk()
        repo = StockDetailCacheRepository(dao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // --- getCache ---

    @Test
    fun `getCache returns entity from dao`() = runTest {
        coEvery { dao.getCache(symbol, range) } returns entity
        val result = repo.getCache(symbol, range)
        assertEquals(entity, result)
    }

    @Test
    fun `getCache returns null on exception`() = runTest {
        coEvery { dao.getCache(symbol, range) } throws RuntimeException("db error")
        val result = repo.getCache(symbol, range)
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

    // --- deleteForSymbol ---

    @Test
    fun `deleteForSymbol delegates to dao`() = runTest {
        coEvery { dao.deleteForSymbol(symbol) } returns Unit
        repo.deleteForSymbol(symbol)
        coVerify(exactly = 1) { dao.deleteForSymbol(symbol) }
    }

    @Test
    fun `deleteForSymbol rethrows exception from dao`() = runTest {
        coEvery { dao.deleteForSymbol(symbol) } throws RuntimeException("db error")
        assertThrows(RuntimeException::class.java) { runTest { repo.deleteForSymbol(symbol) } }
    }
}
