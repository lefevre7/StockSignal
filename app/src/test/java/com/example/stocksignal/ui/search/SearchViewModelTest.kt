package com.example.stocksignal.ui.search

import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import com.example.stocksignal.data.local.model.MarketMoverItem
import com.example.stocksignal.data.local.repository.WatchlistRepository
import com.example.stocksignal.data.repository.SearchRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.data.stooq.repository.MarketMoversRepository
import com.example.stocksignal.domain.model.PriceCandle
import com.example.stocksignal.domain.model.RecentSearch
import com.example.stocksignal.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searchRepository = mockk<SearchRepository>(relaxed = true)
    private val watchlistRepository = mockk<WatchlistRepository>(relaxed = true)
    private val marketMoversRepository = mockk<MarketMoversRepository>()

    @Test
    fun `init loads recent searches watchlist and top movers`() = runTest(mainDispatcherRule.dispatcher) {
        val recentSearches = MutableStateFlow(listOf(sampleRecentSearch("AAPL")))
        val watchlistFlow = MutableStateFlow(listOf(sampleWatchlistEntity("MSFT", sortOrder = 3)))
        every { searchRepository.recentSearches } returns recentSearches
        every { watchlistRepository.watchlistFlow } returns watchlistFlow
        stubMovers()
        coEvery { searchRepository.search(any()) } returns Result.Success(emptyList())
        coEvery { watchlistRepository.upsert(any()) } returns Unit

        val viewModel = SearchViewModel(searchRepository, watchlistRepository, marketMoversRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(sampleRecentSearch("AAPL")), state.recentSearches)
        assertEquals(setOf("MSFT"), state.watchlist.keys)
        assertEquals(MarketMoverDirection.MOST_ACTIVE, state.quickFilter)
        assertEquals(setOf("NVDA", "AMD", "TSLA"), state.moverSymbols)

        viewModel.selectQuickFilter(MarketMoverDirection.DECREASERS)
        assertEquals(MarketMoverDirection.DECREASERS, viewModel.uiState.value.quickFilter)
    }

    @Test
    fun `query success error clear history and watchlist actions update state`() = runTest(mainDispatcherRule.dispatcher) {
        val recentSearches = MutableStateFlow(emptyList<RecentSearch>())
        val watchlistFlow = MutableStateFlow(
            listOf(
                sampleWatchlistEntity("AAPL", alertEnabled = true, sortOrder = 1),
                sampleWatchlistEntity("MSFT", alertEnabled = false, sortOrder = 4)
            )
        )
        every { searchRepository.recentSearches } returns recentSearches
        every { watchlistRepository.watchlistFlow } returns watchlistFlow
        stubMovers()
        coEvery { searchRepository.search(any()) } returns Result.Success(emptyList())
        coEvery { searchRepository.search("apple") } returns Result.Success(
            listOf(
                SearchResult(
                    symbol = "AAPL",
                    companyName = "Apple Inc.",
                    exchange = "NASDAQ",
                    price = 186.42,
                    percentChange = 2.31
                )
            )
        )
        coEvery { searchRepository.search("fail") } returns Result.Error(
            IllegalStateException("cmp unavailable"),
            "cmp unavailable"
        )
        coEvery { watchlistRepository.getBySymbol("NVDA") } returns null
        coEvery { watchlistRepository.getBySymbol("MSFT") } returns sampleWatchlistEntity("MSFT", alertEnabled = false, sortOrder = 4)
        coEvery { watchlistRepository.upsert(any()) } returns Unit
        coEvery { searchRepository.clearHistory() } returns Unit

        val viewModel = SearchViewModel(searchRepository, watchlistRepository, marketMoversRepository)
        advanceUntilIdle()

        viewModel.updateQuery("apple")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("AAPL", viewModel.uiState.value.results.single().symbol)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.updateQuery("fail")
        advanceTimeBy(301)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals("cmp unavailable", viewModel.uiState.value.errorMessage)

        viewModel.clearQuery()
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals(false, viewModel.uiState.value.isLoading)

        viewModel.addToWatchlist(
            SearchResult(
                symbol = "NVDA",
                companyName = "NVIDIA",
                exchange = "NASDAQ",
                price = null,
                percentChange = null
            ),
            alertsEnabled = false
        )
        advanceUntilIdle()
        coVerify {
            watchlistRepository.upsert(
                match {
                    it.symbol == "NVDA" &&
                        it.alertEnabled == false &&
                        it.sortOrder == 5
                }
            )
        }

        viewModel.setAlertEnabled("MSFT", true)
        advanceUntilIdle()
        coVerify {
            watchlistRepository.upsert(
                match { it.symbol == "MSFT" && it.alertEnabled }
            )
        }

        viewModel.selectRecentSearch("AMD")
        assertEquals("AMD", viewModel.uiState.value.query)

        viewModel.clearHistory()
        advanceUntilIdle()
        coVerify { searchRepository.clearHistory() }
    }

    private fun stubMovers() {
        coEvery {
            marketMoversRepository.getMarketMovers(MarketMoverRange.ONE_DAY, MarketMoverDirection.MOST_ACTIVE, false)
        } returns Result.Success(sampleMoversResult(listOf(sampleMover("NVDA"))))
        coEvery {
            marketMoversRepository.getMarketMovers(MarketMoverRange.ONE_DAY, MarketMoverDirection.INCREASERS, false)
        } returns Result.Success(sampleMoversResult(listOf(sampleMover("AMD"))))
        coEvery {
            marketMoversRepository.getMarketMovers(MarketMoverRange.ONE_DAY, MarketMoverDirection.DECREASERS, false)
        } returns Result.Success(sampleMoversResult(listOf(sampleMover("TSLA"))))
    }

    private fun sampleMoversResult(items: List<MarketMoverItem>) =
        com.example.stocksignal.data.local.model.MarketMoversSnapshot(
            items = items,
            fetchedAt = LocalDateTime.of(2026, 3, 31, 9, 30),
            isStale = false,
            isFallback = false
        )

    private fun sampleMover(ticker: String) = MarketMoverItem(
        ticker = ticker,
        companyName = "$ticker Inc.",
        exchange = "NASDAQ",
        price = 100.0,
        percentChange = 2.0,
        rank = 1,
        signalScore = 70,
        signalLabel = "Buy",
        series = listOf(
            PriceCandle(
                time = LocalDateTime.of(2026, 3, 31, 9, 30),
                open = 99.0,
                high = 101.0,
                low = 98.0,
                close = 100.0,
                volume = 1_000_000L
            )
        )
    )

    private fun sampleRecentSearch(query: String) = RecentSearch(
        query = query,
        lastSearchedAt = LocalDateTime.of(2026, 3, 31, 8, 0),
        count = 3
    )

    private fun sampleWatchlistEntity(
        symbol: String,
        alertEnabled: Boolean = true,
        sortOrder: Int? = 0
    ) = WatchlistItemEntity(
        symbol = symbol,
        companyName = "$symbol Inc.",
        exchange = "NASDAQ",
        addedAt = LocalDateTime.of(2026, 3, 30, 9, 30),
        alertEnabled = alertEnabled,
        minScoreForNotify = 60,
        quietHoursStart = null,
        quietHoursEnd = null,
        snoozedUntil = null,
        lastSignalScore = null,
        lastSignalLabel = null,
        lastSignalConfidence = null,
        lastSignalTime = null,
        notes = null,
        sortOrder = sortOrder,
        tags = emptyList(),
        muteMarketMovers = false,
        lastNotifiedAt = null,
        indicatorAlertsJson = null
    )
}
