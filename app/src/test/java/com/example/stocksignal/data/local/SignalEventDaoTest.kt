package com.example.stocksignal.data.local

import androidx.room.Room
import com.example.stocksignal.data.local.dao.SignalEventDao
import com.example.stocksignal.data.local.db.StockSignalDatabase
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class SignalEventDaoTest {

    private lateinit var database: StockSignalDatabase
    private lateinit var dao: SignalEventDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, StockSignalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.signalEventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `dismissed signals are filtered from observeEvents`() = runTest {
        val active = sampleEvent(id = "evt_active", dismissed = false)
        val dismissed = sampleEvent(id = "evt_dismissed", dismissed = true)

        dao.upsert(active)
        dao.upsert(dismissed)

        val events = dao.observeEvents().first()
        assertEquals(1, events.size)
        assertEquals("evt_active", events.first().id)
    }

    @Test
    fun `dismiss and undo updates dismissed state`() = runTest {
        val event = sampleEvent(id = "evt_undo", dismissed = false)
        dao.upsert(event)

        dao.dismissSignal(event.id)
        val dismissed = dao.getByIds(listOf(event.id)).first()
        assertTrue(dismissed.dismissed)

        dao.undoDismissSignal(event.id)
        val restored = dao.getByIds(listOf(event.id)).first()
        assertFalse(restored.dismissed)

        val events = dao.observeEvents().first()
        assertEquals(1, events.size)
        assertEquals(event.id, events.first().id)
    }

    private fun sampleEvent(id: String, dismissed: Boolean): GlobalSignalEventEntity {
        return GlobalSignalEventEntity(
            id = id,
            type = "watchlist_signal",
            ticker = "AAPL",
            score = 50,
            label = "Buy",
            confidence = 70,
            percentChange = null,
            price = null,
            generatedAt = LocalDateTime.of(2026, 1, 10, 9, 30),
            notifiedAt = null,
            source = "local",
            delivered = false,
            dismissed = dismissed,
            deepLink = "stocksignal://stock/AAPL",
            reasons = emptyList(),
            avgScore = 50,
            modeScore = null,
            modelScores = null
        )
    }
}
