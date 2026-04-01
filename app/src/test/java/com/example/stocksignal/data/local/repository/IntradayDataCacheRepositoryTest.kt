package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.IntradayDataCacheDao
import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class IntradayDataCacheRepositoryTest {

    private lateinit var dao: IntradayDataCacheDao
    private lateinit var repo: IntradayDataCacheRepository

    private val symbol = "AAPL.US"
    private val date = LocalDate.of(2024, 6, 1)
    private val entity = IntradayDataCacheEntity(
        symbol = symbol,
        date = date,
        createdAt = LocalDateTime.of(2024, 6, 1, 9, 30),
        updatedAt = LocalDateTime.of(2024, 6, 1, 16, 0),
        candlesJson = "[]"
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk()
        repo = IntradayDataCacheRepository(dao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
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

    // --- getCandlesByDateRange ---

    @Test
    fun `getCandlesByDateRange returns list from dao`() = runTest {
        val end = date.plusDays(7)
        coEvery { dao.getCandlesByDateRange(symbol, date, end) } returns listOf(entity)
        val result = repo.getCandlesByDateRange(symbol, date, end)
        assertEquals(listOf(entity), result)
    }

    @Test
    fun `getCandlesByDateRange returns emptyList on exception`() = runTest {
        val end = date.plusDays(7)
        coEvery { dao.getCandlesByDateRange(symbol, date, end) } throws RuntimeException("fail")
        val result = repo.getCandlesByDateRange(symbol, date, end)
        assertTrue(result.isEmpty())
    }

    // --- getAllCandlesForSymbol ---

    @Test
    fun `getAllCandlesForSymbol returns list from dao`() = runTest {
        coEvery { dao.getAllCandlesForSymbol(symbol) } returns listOf(entity)
        val result = repo.getAllCandlesForSymbol(symbol)
        assertEquals(listOf(entity), result)
    }

    @Test
    fun `getAllCandlesForSymbol returns emptyList on exception`() = runTest {
        coEvery { dao.getAllCandlesForSymbol(symbol) } throws RuntimeException("fail")
        val result = repo.getAllCandlesForSymbol(symbol)
        assertTrue(result.isEmpty())
    }

    // --- deleteOldData ---

    @Test
    fun `deleteOldData delegates to dao`() = runTest {
        val before = date.minusDays(1)
        coEvery { dao.deleteOldData(symbol, before) } returns Unit
        repo.deleteOldData(symbol, before)
        coVerify(exactly = 1) { dao.deleteOldData(symbol, before) }
    }

    @Test
    fun `deleteOldData rethrows exception from dao`() = runTest {
        val before = date.minusDays(1)
        coEvery { dao.deleteOldData(symbol, before) } throws RuntimeException("db error")
        assertThrows(RuntimeException::class.java) { runTest { repo.deleteOldData(symbol, before) } }
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

    // --- countDaysForSymbol ---

    @Test
    fun `countDaysForSymbol returns count from dao`() = runTest {
        coEvery { dao.countDaysForSymbol(symbol) } returns 42
        val result = repo.countDaysForSymbol(symbol)
        assertEquals(42, result)
    }

    @Test
    fun `countDaysForSymbol returns 0 on exception`() = runTest {
        coEvery { dao.countDaysForSymbol(symbol) } throws RuntimeException("fail")
        val result = repo.countDaysForSymbol(symbol)
        assertEquals(0, result)
    }

    // --- getEarliestDate ---

    @Test
    fun `getEarliestDate returns date from dao`() = runTest {
        coEvery { dao.getEarliestDate(symbol) } returns date
        val result = repo.getEarliestDate(symbol)
        assertEquals(date, result)
    }

    @Test
    fun `getEarliestDate returns null on exception`() = runTest {
        coEvery { dao.getEarliestDate(symbol) } throws RuntimeException("fail")
        val result = repo.getEarliestDate(symbol)
        assertNull(result)
    }

    // --- getLatestDate ---

    @Test
    fun `getLatestDate returns date from dao`() = runTest {
        coEvery { dao.getLatestDate(symbol) } returns date.plusDays(5)
        val result = repo.getLatestDate(symbol)
        assertEquals(date.plusDays(5), result)
    }

    @Test
    fun `getLatestDate returns null on exception`() = runTest {
        coEvery { dao.getLatestDate(symbol) } throws RuntimeException("fail")
        val result = repo.getLatestDate(symbol)
        assertNull(result)
    }
}
