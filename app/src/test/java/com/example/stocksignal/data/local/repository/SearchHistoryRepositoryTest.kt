package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.SearchHistoryDao
import com.example.stocksignal.data.local.entity.SearchHistoryEntity
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

class SearchHistoryRepositoryTest {

    private lateinit var dao: SearchHistoryDao
    private lateinit var repository: SearchHistoryRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk(relaxed = true)
        repository = SearchHistoryRepository(dao)
    }

    private fun repoWith(entries: List<SearchHistoryEntity>): SearchHistoryRepository {
        val freshDao = mockk<SearchHistoryDao>(relaxed = true)
        every { freshDao.observeHistory() } returns flowOf(entries)
        return SearchHistoryRepository(freshDao)
    }

    private fun repoWithFlowError(): SearchHistoryRepository {
        val freshDao = mockk<SearchHistoryDao>(relaxed = true)
        every { freshDao.observeHistory() } returns flow { throw RuntimeException("db error") }
        return SearchHistoryRepository(freshDao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ---- historyFlow ----

    @Test
    fun `historyFlow emits entries from dao`() = runTest {
        val entries = listOf(sampleEntry("AAPL"), sampleEntry("NVDA"))
        val repo = repoWith(entries)

        assertEquals(entries, repo.historyFlow.first())
    }

    @Test
    fun `historyFlow emits empty list on dao error`() = runTest {
        val repo = repoWithFlowError()

        assertTrue(repo.historyFlow.first().isEmpty())
    }

    // ---- getByQuery ----

    @Test
    fun `getByQuery returns entry from dao`() = runTest {
        val entry = sampleEntry("TSLA")
        coEvery { dao.getByQuery("TSLA") } returns entry

        assertEquals(entry, repository.getByQuery("TSLA"))
    }

    @Test
    fun `getByQuery returns null on dao exception`() = runTest {
        coEvery { dao.getByQuery("TSLA") } throws RuntimeException("db error")

        assertNull(repository.getByQuery("TSLA"))
    }

    // ---- upsert ----

    @Test
    fun `upsert delegates to dao`() = runTest {
        val entry = sampleEntry("AAPL")
        coEvery { dao.upsert(entry) } returns Unit

        repository.upsert(entry)

        coVerify(exactly = 1) { dao.upsert(entry) }
    }

    @Test
    fun `upsert rethrows dao exception`() = runTest {
        val entry = sampleEntry("AAPL")
        coEvery { dao.upsert(entry) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.upsert(entry)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- delete ----

    @Test
    fun `delete delegates to dao`() = runTest {
        coEvery { dao.delete("AAPL") } returns Unit

        repository.delete("AAPL")

        coVerify(exactly = 1) { dao.delete("AAPL") }
    }

    @Test
    fun `delete rethrows dao exception`() = runTest {
        coEvery { dao.delete("AAPL") } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.delete("AAPL")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- clear ----

    @Test
    fun `clear delegates to dao`() = runTest {
        coEvery { dao.clear() } returns Unit

        repository.clear()

        coVerify(exactly = 1) { dao.clear() }
    }

    @Test
    fun `clear rethrows dao exception`() = runTest {
        coEvery { dao.clear() } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.clear()
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    private fun sampleEntry(query: String) = SearchHistoryEntity(
        query = query,
        lastSearchedAt = LocalDateTime.of(2026, 3, 31, 10, 0),
        count = 1
    )
}
