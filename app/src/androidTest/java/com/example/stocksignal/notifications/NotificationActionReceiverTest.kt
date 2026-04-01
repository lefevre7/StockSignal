package com.example.stocksignal.notifications

import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stocksignal.data.local.dao.WatchlistDao
import com.example.stocksignal.data.local.db.MIGRATION_1_2
import com.example.stocksignal.data.local.db.MIGRATION_2_3
import com.example.stocksignal.data.local.db.MIGRATION_3_4
import com.example.stocksignal.data.local.db.MIGRATION_4_5
import com.example.stocksignal.data.local.db.MIGRATION_5_6
import com.example.stocksignal.data.local.db.MIGRATION_6_7
import com.example.stocksignal.data.local.db.StockSignalDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [NotificationActionReceiver] covering dismiss and
 * add-to-watchlist actions. The receiver uses @AndroidEntryPoint so tests
 * run against the real Hilt component tree from [StockSignalApplication].
 */
@RunWith(AndroidJUnit4::class)
class NotificationActionReceiverTest {

    private lateinit var context: android.content.Context
    private lateinit var receiver: NotificationActionReceiver
    private lateinit var watchlistDao: WatchlistDao

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        receiver = NotificationActionReceiver()
        val db = Room.databaseBuilder(
            context, StockSignalDatabase::class.java, "stocksignal.db"
        ).addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
        ).build()
        watchlistDao = db.watchlistDao()
        // Clean up any existing watchlist entries for test tickers
        watchlistDao.deleteBySymbol("TEST.US")
        watchlistDao.deleteBySymbol("EXISTING.US")
    }

    // ============== ACTION_DISMISS ==============

    @Test
    fun dismissActionWithZeroIdDoesNotCrash() = runBlocking {
        val intent = Intent(NotificationActionReceiver.ACTION_DISMISS).apply {
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    @Test
    fun dismissActionWithNonZeroIdDoesNotCrash() = runBlocking {
        val intent = Intent(NotificationActionReceiver.ACTION_DISMISS).apply {
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 42)
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    // ============== ACTION_ADD_WATCHLIST ==============

    @Test
    fun addToWatchlistWithNewTickerAddsEntry() = runBlocking {
        val ticker = "TEST.US"
        val intent = Intent(NotificationActionReceiver.ACTION_ADD_WATCHLIST).apply {
            putExtra(NotificationActionReceiver.EXTRA_TICKER, ticker)
            putExtra(NotificationActionReceiver.EXTRA_COMPANY, "Test Corp")
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
        }
        // Ensure it doesn't exist yet
        assertNull(watchlistDao.getBySymbol(ticker))

        receiver.onReceive(context, intent)
        delay(500) // wait for async work

        val entry = watchlistDao.getBySymbol(ticker)
        assertNotNull("Watchlist entry should have been created for $ticker", entry)
    }

    @Test
    fun addToWatchlistWithBlankTickerDoesNotCrash() = runBlocking {
        val intent = Intent(NotificationActionReceiver.ACTION_ADD_WATCHLIST).apply {
            putExtra(NotificationActionReceiver.EXTRA_TICKER, "")
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
        }
        receiver.onReceive(context, intent)
        delay(300)
    }

    @Test
    fun addToWatchlistExistingTickerIsNotDuplicated() = runBlocking {
        val ticker = "EXISTING.US"
        // First call: should create entry
        val intent = Intent(NotificationActionReceiver.ACTION_ADD_WATCHLIST).apply {
            putExtra(NotificationActionReceiver.EXTRA_TICKER, ticker)
            putExtra(NotificationActionReceiver.EXTRA_COMPANY, "Existing Corp")
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, 0)
        }
        receiver.onReceive(context, intent)
        delay(500)
        assertNotNull(watchlistDao.getBySymbol(ticker))

        // Second call: should NOT create duplicate
        receiver.onReceive(context, intent)
        delay(500)
        val all = watchlistDao.getAll()
        val count = all.count { it.symbol == ticker }
        assert(count == 1) { "Expected 1 entry for $ticker, found $count" }
    }

    @Test
    fun unknownActionDoesNotCrash() = runBlocking {
        val intent = Intent("com.example.stocksignal.action.UNKNOWN")
        receiver.onReceive(context, intent)
        delay(300)
    }
}
