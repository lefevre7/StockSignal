package com.example.stocksignal.data.stooq.repository

import android.util.Log
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.data.stooq.network.StooqBlockedException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StooqSearchRepositoryTest {

    private val api = mockk<StooqApi>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `blank query returns empty result without network calls`() = runTest {
        val repository = StooqSearchRepository(api)

        val result = repository.search(" ")

        assertEquals(Result.Success<List<com.example.stocksignal.data.stooq.model.SearchResult>>(emptyList()), result)
        coVerify(exactly = 0) { api.getHomePage() }
        coVerify(exactly = 0) { api.getCmp(any(), any()) }
    }

    @Test
    fun `successful search caches campaign id for subsequent requests`() = runTest {
        val repository = StooqSearchRepository(api)
        coEvery { api.getHomePage() } returns """<a href="/cmp/?12345&q=test">cmp</a>"""
        coEvery { api.getCmp(any(), any()) } returns "window.cmp_r('AAPL.US~Apple Inc~XNAS~186.4200~2.31%~4');"

        val first = repository.search("apple")
        val second = repository.search("apple")

        assertTrue(first is Result.Success)
        assertTrue(second is Result.Success)
        assertEquals("AAPL.US", (first as Result.Success).data.single().symbol)
        coVerify(exactly = 1) { api.getHomePage() }
        coVerify(exactly = 2) { api.getCmp("12345", "apple") }
    }

    @Test
    fun `home page failure falls back to empty campaign id`() = runTest {
        val repository = StooqSearchRepository(api)
        coEvery { api.getHomePage() } throws IllegalStateException("homepage unavailable")
        coEvery { api.getCmp(any(), any()) } returns "window.cmp_r('TSLA.US~Tesla Inc~XNAS~432.7500~0.43%~4');"

        val result = repository.search("tesla")

        assertTrue(result is Result.Success)
        assertEquals("TSLA.US", (result as Result.Success).data.single().symbol)
        coVerify { api.getCmp("", "tesla") }
    }

    @Test
    fun `blocked exception from campaign fetch returns error result`() = runTest {
        val repository = StooqSearchRepository(api)
        coEvery { api.getHomePage() } throws StooqBlockedException("Requests paused.")

        val result = repository.search("nvda")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message!!.contains("Requests paused."))
        coVerify(exactly = 0) { api.getCmp(any(), any()) }
    }

    @Test
    fun `cmp request exception returns error result`() = runTest {
        val repository = StooqSearchRepository(api)
        coEvery { api.getHomePage() } returns """<a href="/cmp/?777&q=test">cmp</a>"""
        coEvery { api.getCmp("777", "msft") } throws IllegalStateException("cmp unavailable")

        val result = repository.search("msft")

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message!!.contains("cmp unavailable"))
    }
}
