package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.WatchlistDao
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class WatchlistRepositoryTest {

    private lateinit var dao: WatchlistDao
    private lateinit var repository: WatchlistRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk(relaxed = true)
        // Default watchlistFlow stub; individual tests that need a specific emission
        // create their own repository instance after setting up the stub.
        repository = WatchlistRepository(dao)
    }

    /** Creates a fresh repository with a specific flow emission. */
    private fun repoWith(emittedItems: List<WatchlistItemEntity>): WatchlistRepository {
        val freshDao = mockk<WatchlistDao>(relaxed = true)
        every { freshDao.observeWatchlist() } returns flowOf(emittedItems)
        return WatchlistRepository(freshDao)
    }

    /** Creates a fresh repository whose flow throws once. */
    private fun repoWithFlowError(): WatchlistRepository {
        val freshDao = mockk<WatchlistDao>(relaxed = true)
        every { freshDao.observeWatchlist() } returns flow { throw RuntimeException("db error") }
        return WatchlistRepository(freshDao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ---- watchlistFlow ----

    @Test
    fun `watchlistFlow emits items from dao`() = runTest {
        val items = listOf(sampleItem("AAPL"), sampleItem("MSFT"))
        val repo = repoWith(items)

        val result = repo.watchlistFlow.first()

        assertEquals(items, result)
    }

    @Test
    fun `watchlistFlow emits empty list on dao error`() = runTest {
        val repo = repoWithFlowError()

        val result = repo.watchlistFlow.first()

        assertTrue(result.isEmpty())
    }

    // ---- getBySymbol ----

    @Test
    fun `getBySymbol returns item from dao`() = runTest {
        val item = sampleItem("AAPL")
        coEvery { dao.getBySymbol("AAPL") } returns item

        assertEquals(item, repository.getBySymbol("AAPL"))
    }

    @Test
    fun `getBySymbol returns null on dao exception`() = runTest {
        coEvery { dao.getBySymbol("AAPL") } throws RuntimeException("db error")

        assertNull(repository.getBySymbol("AAPL"))
    }

    // ---- getAll ----

    @Test
    fun `getAll returns list from dao`() = runTest {
        val items = listOf(sampleItem("AAPL"), sampleItem("GOOG"))
        coEvery { dao.getAll() } returns items

        assertEquals(items, repository.getAll())
    }

    @Test
    fun `getAll returns empty list on dao exception`() = runTest {
        coEvery { dao.getAll() } throws RuntimeException("db error")

        assertTrue(repository.getAll().isEmpty())
    }

    // ---- upsert ----

    @Test
    fun `upsert delegates to dao`() = runTest {
        val item = sampleItem("AAPL")
        coEvery { dao.upsert(item) } returns Unit

        repository.upsert(item)

        coVerify(exactly = 1) { dao.upsert(item) }
    }

    @Test
    fun `upsert rethrows dao exception`() = runTest {
        val item = sampleItem("AAPL")
        coEvery { dao.upsert(item) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.upsert(item)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- deleteBySymbol ----

    @Test
    fun `deleteBySymbol delegates to dao`() = runTest {
        coEvery { dao.deleteBySymbol("AAPL") } returns Unit

        repository.deleteBySymbol("AAPL")

        coVerify(exactly = 1) { dao.deleteBySymbol("AAPL") }
    }

    @Test
    fun `deleteBySymbol rethrows dao exception`() = runTest {
        coEvery { dao.deleteBySymbol("AAPL") } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.deleteBySymbol("AAPL")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- updateSortOrder ----

    @Test
    fun `updateSortOrder calls dao with correct index for each symbol`() = runTest {
        coEvery { dao.updateSortOrder(any(), any()) } returns Unit
        val symbols = listOf("AAPL", "MSFT", "GOOG")

        repository.updateSortOrder(symbols)

        coVerify(exactly = 1) { dao.updateSortOrder("AAPL", 0) }
        coVerify(exactly = 1) { dao.updateSortOrder("MSFT", 1) }
        coVerify(exactly = 1) { dao.updateSortOrder("GOOG", 2) }
    }

    @Test
    fun `updateSortOrder does nothing for empty list`() = runTest {
        repository.updateSortOrder(emptyList())

        coVerify(exactly = 0) { dao.updateSortOrder(any(), any()) }
    }

    @Test
    fun `updateSortOrder rethrows dao exception`() = runTest {
        coEvery { dao.updateSortOrder(any(), any()) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.updateSortOrder(listOf("AAPL"))
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    private fun sampleItem(symbol: String) = WatchlistItemEntity(
        symbol = symbol,
        companyName = "$symbol Inc",
        exchange = "NASDAQ",
        addedAt = LocalDateTime.now(),
        alertEnabled = true,
        minScoreForNotify = 60,
        quietHoursStart = null,
        quietHoursEnd = null,
        snoozedUntil = null,
        lastSignalScore = null,
        lastSignalLabel = null,
        lastSignalConfidence = null,
        lastSignalTime = null,
        notes = null,
        sortOrder = 0,
        tags = emptyList(),
        muteMarketMovers = false,
        lastNotifiedAt = null,
        indicatorAlertsJson = null
    )
}
