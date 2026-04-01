package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.SignalEventDao
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
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

class SignalEventsRepositoryTest {

    private lateinit var dao: SignalEventDao
    private lateinit var repository: SignalEventsRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        dao = mockk(relaxed = true)
        repository = SignalEventsRepository(dao)
    }

    private fun repoWith(events: List<GlobalSignalEventEntity>): SignalEventsRepository {
        val freshDao = mockk<SignalEventDao>(relaxed = true)
        every { freshDao.observeEvents() } returns flowOf(events)
        return SignalEventsRepository(freshDao)
    }

    private fun repoWithFlowError(): SignalEventsRepository {
        val freshDao = mockk<SignalEventDao>(relaxed = true)
        every { freshDao.observeEvents() } returns flow { throw RuntimeException("db error") }
        return SignalEventsRepository(freshDao)
    }

    private fun repoWithTickerFlow(
        ticker: String,
        events: List<GlobalSignalEventEntity>
    ): SignalEventsRepository {
        val freshDao = mockk<SignalEventDao>(relaxed = true)
        every { freshDao.observeEventsForTicker(ticker) } returns flowOf(events)
        return SignalEventsRepository(freshDao)
    }

    private fun repoWithTickerFlowError(ticker: String): SignalEventsRepository {
        val freshDao = mockk<SignalEventDao>(relaxed = true)
        every { freshDao.observeEventsForTicker(ticker) } returns flow { throw RuntimeException("db error") }
        return SignalEventsRepository(freshDao)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ---- eventsFlow ----

    @Test
    fun `eventsFlow emits events from dao`() = runTest {
        val events = listOf(sampleEvent("evt-1"), sampleEvent("evt-2"))
        val repo = repoWith(events)

        assertEquals(events, repo.eventsFlow.first())
    }

    @Test
    fun `eventsFlow emits empty list on dao error`() = runTest {
        val repo = repoWithFlowError()

        assertTrue(repo.eventsFlow.first().isEmpty())
    }

    // ---- eventsForTicker ----

    @Test
    fun `eventsForTicker emits events from dao`() = runTest {
        val events = listOf(sampleEvent("evt-1", ticker = "AAPL"))
        val repo = repoWithTickerFlow("AAPL", events)

        assertEquals(events, repo.eventsForTicker("AAPL").first())
    }

    @Test
    fun `eventsForTicker emits empty list on dao error`() = runTest {
        val repo = repoWithTickerFlowError("AAPL")

        assertTrue(repo.eventsForTicker("AAPL").first().isEmpty())
    }

    // ---- getLatestForTickerAndLabel ----

    @Test
    fun `getLatestForTickerAndLabel returns event from dao`() = runTest {
        val event = sampleEvent("evt-1", ticker = "AAPL")
        coEvery { dao.getLatestForTickerAndLabel("AAPL", "BUY") } returns event

        assertEquals(event, repository.getLatestForTickerAndLabel("AAPL", "BUY"))
    }

    @Test
    fun `getLatestForTickerAndLabel returns null on dao exception`() = runTest {
        coEvery { dao.getLatestForTickerAndLabel("AAPL", "BUY") } throws RuntimeException("db error")

        assertNull(repository.getLatestForTickerAndLabel("AAPL", "BUY"))
    }

    // ---- getByIds ----

    @Test
    fun `getByIds returns empty list immediately for empty input`() = runTest {
        val result = repository.getByIds(emptyList())

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { dao.getByIds(any()) }
    }

    @Test
    fun `getByIds delegates to dao for non-empty ids`() = runTest {
        val events = listOf(sampleEvent("evt-1"))
        coEvery { dao.getByIds(listOf("evt-1")) } returns events

        assertEquals(events, repository.getByIds(listOf("evt-1")))
    }

    @Test
    fun `getByIds returns empty list on dao exception`() = runTest {
        coEvery { dao.getByIds(any()) } throws RuntimeException("db error")

        assertTrue(repository.getByIds(listOf("evt-1")).isEmpty())
    }

    // ---- upsert ----

    @Test
    fun `upsert delegates to dao`() = runTest {
        val event = sampleEvent("evt-1")
        coEvery { dao.upsert(event) } returns Unit

        repository.upsert(event)

        coVerify(exactly = 1) { dao.upsert(event) }
    }

    @Test
    fun `upsert rethrows dao exception`() = runTest {
        val event = sampleEvent("evt-1")
        coEvery { dao.upsert(event) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.upsert(event)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- updateDelivery ----

    @Test
    fun `updateDelivery skips dao for empty ids`() = runTest {
        repository.updateDelivery(emptyList(), LocalDateTime.now(), true)

        coVerify(exactly = 0) { dao.updateDelivery(any(), any(), any()) }
    }

    @Test
    fun `updateDelivery delegates to dao for non-empty ids`() = runTest {
        val now = LocalDateTime.of(2026, 3, 31, 10, 0)
        coEvery { dao.updateDelivery(any(), any(), any()) } returns Unit

        repository.updateDelivery(listOf("evt-1"), now, true)

        coVerify(exactly = 1) { dao.updateDelivery(listOf("evt-1"), now, true) }
    }

    @Test
    fun `updateDelivery rethrows dao exception`() = runTest {
        coEvery { dao.updateDelivery(any(), any(), any()) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.updateDelivery(listOf("evt-1"), LocalDateTime.now(), true)
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- dismissSignal ----

    @Test
    fun `dismissSignal delegates to dao`() = runTest {
        coEvery { dao.dismissSignal("evt-1") } returns Unit

        repository.dismissSignal("evt-1")

        coVerify(exactly = 1) { dao.dismissSignal("evt-1") }
    }

    @Test
    fun `dismissSignal rethrows dao exception`() = runTest {
        coEvery { dao.dismissSignal(any()) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.dismissSignal("evt-1")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- undoDismissSignal ----

    @Test
    fun `undoDismissSignal delegates to dao`() = runTest {
        coEvery { dao.undoDismissSignal("evt-1") } returns Unit

        repository.undoDismissSignal("evt-1")

        coVerify(exactly = 1) { dao.undoDismissSignal("evt-1") }
    }

    @Test
    fun `undoDismissSignal rethrows dao exception`() = runTest {
        coEvery { dao.undoDismissSignal(any()) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.undoDismissSignal("evt-1")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    // ---- deleteById ----

    @Test
    fun `deleteById delegates to dao`() = runTest {
        coEvery { dao.deleteById("evt-1") } returns Unit

        repository.deleteById("evt-1")

        coVerify(exactly = 1) { dao.deleteById("evt-1") }
    }

    @Test
    fun `deleteById rethrows dao exception`() = runTest {
        coEvery { dao.deleteById(any()) } throws RuntimeException("db error")

        var thrown: Exception? = null
        try {
            repository.deleteById("evt-1")
        } catch (e: Exception) {
            thrown = e
        }
        assertTrue(thrown is RuntimeException)
    }

    private fun sampleEvent(id: String, ticker: String = "AAPL") = GlobalSignalEventEntity(
        id = id,
        type = "WATCHLIST_SIGNAL",
        ticker = ticker,
        score = 70,
        label = "BUY",
        confidence = 75,
        percentChange = 1.5,
        price = 185.0,
        generatedAt = LocalDateTime.of(2026, 3, 31, 10, 0),
        notifiedAt = null,
        source = "rule_based",
        delivered = false,
        deepLink = null,
        avgScore = null,
        modeScore = null,
        modelScores = null
    )
}
