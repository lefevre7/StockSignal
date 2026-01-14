package com.example.stocksignal.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 3 to 4.
 * 
 * Changes:
 * - Adds stock_overview_cache table for storing fundamental data (market cap, P/E, dividend, 52W high/low)
 * - TTL: 24 hours
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stock_overview_cache (
                symbol TEXT NOT NULL PRIMARY KEY,
                marketCap REAL,
                peRatio REAL,
                dividend REAL,
                week52High REAL,
                week52Low REAL,
                fetchedAt TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}
