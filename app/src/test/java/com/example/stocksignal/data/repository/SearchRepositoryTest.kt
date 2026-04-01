package com.example.stocksignal.data.repository

import com.example.stocksignal.data.local.entity.SearchHistoryEntity
import com.example.stocksignal.data.local.repository.SearchHistoryRepository
import com.example.stocksignal.data.settings.HoldingPeriod
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.data.stooq.repository.StooqSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryTest {

    private val stooqSearchRepository = mockk<StooqSearchRepository>()
    private val searchHistoryRepository = mockk<SearchHistoryRepository>(relaxed = true)

    @Test
    fun `recentSearches maps history entries to domain models`() = runTest {
        val history = MutableStateFlow(
            listOf(
                SearchHistoryEntity(
                    query = "AAPL",
                    lastSearchedAt = LocalDateTime.of(2026, 3, 31, 9, 30),
                    count = 2
                )
            )
        )
        every { searchHistoryRepository.historyFlow } returns history
        val repository = SearchRepository(stooqSearchRepository, searchHistoryRepository)

        val recent = repository.recentSearches.first()

        assertEquals(1, recent.size)
        assertEquals("AAPL", recent.single().query)
        assertEquals(2, recent.single().count)
    }

    @Test
    fun `search returns empty result for blank query without touching dependencies`() = runTest {
        every { searchHistoryRepository.historyFlow } returns MutableStateFlow(emptyList())
        val repository = SearchRepository(stooqSearchRepository, searchHistoryRepository)

        val result = repository.search("   ")

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
        coVerify(exactly = 0) { stooqSearchRepository.search(any()) }
        coVerify(exactly = 0) { searchHistoryRepository.upsert(any()) }
    }

    @Test
    fun `search success records and increments history`() = runTest {
        every { searchHistoryRepository.historyFlow } returns MutableStateFlow(emptyList())
        val repository = SearchRepository(stooqSearchRepository, searchHistoryRepository)
        val existing = SearchHistoryEntity(
            query = "apple",
            lastSearchedAt = LocalDateTime.of(2026, 3, 30, 8, 0),
            count = 2
        )
        val results = listOf(
            SearchResult(
                symbol = "AAPL",
                companyName = "Apple Inc.",
                exchange = "NASDAQ",
                price = 186.42,
                percentChange = 2.31
            )
        )
        coEvery { stooqSearchRepository.search("apple") } returns Result.Success(results)
        coEvery { searchHistoryRepository.getByQuery("apple") } returns existing

        val result = repository.search("apple")

        assertEquals(Result.Success(results), result)
        coVerify {
            searchHistoryRepository.upsert(
                match {
                    it.query == "apple" &&
                        it.count == 3
                }
            )
        }
    }

    @Test
    fun `search error does not persist history`() = runTest {
        every { searchHistoryRepository.historyFlow } returns MutableStateFlow(emptyList())
        val repository = SearchRepository(stooqSearchRepository, searchHistoryRepository)
        val failure = IllegalStateException("cmp unavailable")
        coEvery {
            stooqSearchRepository.search("tesla")
        } returns Result.Error(failure, "Search failed: cmp unavailable")

        val result = repository.search("tesla")

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { searchHistoryRepository.upsert(any()) }
    }

    @Test
    fun `clearHistory delegates to repository`() = runTest {
        every { searchHistoryRepository.historyFlow } returns MutableStateFlow(emptyList())
        val repository = SearchRepository(stooqSearchRepository, searchHistoryRepository)

        repository.clearHistory()

        coVerify { searchHistoryRepository.clear() }
    }
}
