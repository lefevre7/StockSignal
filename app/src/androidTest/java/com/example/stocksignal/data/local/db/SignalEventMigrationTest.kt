package com.example.stocksignal.data.local.db

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalEventMigrationTest {

    @Test
    fun migrate6To7_addsDismissedColumnWithDefault() {
        val dbName = "signal_event_migration_test"
        val db = createV6Database(dbName)

        val values = ContentValues().apply {
            put("id", "evt_migration")
            put("type", "watchlist_signal")
            put("ticker", "AAPL")
            put("score", 50)
            put("label", "Buy")
            put("confidence", 70)
            putNull("aiScore")
            putNull("aiConfidence")
            putNull("aiSummary")
            putNull("aiReasonsJson")
            putNull("percentChange")
            putNull("price")
            put("generatedAt", "2026-01-10T09:30:00")
            putNull("notifiedAt")
            put("source", "local")
            put("delivered", 0)
            put("deepLink", "stocksignal://stock/AAPL")
            put("reasons", "[]")
            put("avgScore", 50)
            putNull("modeScore")
            putNull("modelScores")
        }
        db.insert("signal_events", 0, values)

        MIGRATION_6_7.migrate(db)

        val cursor = db.query(
            "SELECT dismissed FROM signal_events WHERE id = ?",
            arrayOf("evt_migration")
        )
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    private fun createV6Database(dbName: String): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS signal_events (
                                id TEXT NOT NULL PRIMARY KEY,
                                type TEXT NOT NULL,
                                ticker TEXT NOT NULL,
                                score INTEGER NOT NULL,
                                label TEXT NOT NULL,
                                confidence INTEGER NOT NULL,
                                aiScore INTEGER,
                                aiConfidence INTEGER,
                                aiSummary TEXT,
                                aiReasonsJson TEXT,
                                percentChange REAL,
                                price REAL,
                                generatedAt TEXT NOT NULL,
                                notifiedAt TEXT,
                                source TEXT NOT NULL,
                                delivered INTEGER NOT NULL,
                                deepLink TEXT,
                                reasons TEXT NOT NULL,
                                avgScore INTEGER,
                                modeScore INTEGER,
                                modelScores TEXT
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return helper.writableDatabase
    }
}
