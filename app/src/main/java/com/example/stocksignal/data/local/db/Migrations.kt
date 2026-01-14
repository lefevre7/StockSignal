package com.example.stocksignal.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE watchlist_items ADD COLUMN indicatorAlertsJson TEXT"
        )
    }
}

/**
 * Migration from version 2 to 3.
 * 
 * IMPORTANT: This migration requires a FULL DATA RESET.
 * Users will be notified personally about this update.
 * 
 * Changes:
 * - Adds IntradayDataCacheEntity table for storing up to 1 year of 10-minute intraday data
 * - Adds HoldingPeriod setting to customize signal calculation based on investment timeframe
 * - Historical intraday data accumulates passively at user's notification frequency
 * 
 * The fallback approach is to destroy and recreate the database.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create intraday_data_cache table
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS intraday_data_cache (
                symbol TEXT NOT NULL,
                date TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                updatedAt TEXT NOT NULL,
                candlesJson TEXT NOT NULL,
                PRIMARY KEY(symbol, date)
            )
            """.trimIndent()
        )
    }
}
