package com.example.stocksignal.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 4 to 5.
 *
 * Changes:
 * - Adds newsJson column to stock_overview_cache for recent headlines.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE stock_overview_cache ADD COLUMN newsJson TEXT"
        )
    }
}
