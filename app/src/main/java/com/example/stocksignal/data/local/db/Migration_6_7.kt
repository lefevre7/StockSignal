package com.example.stocksignal.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 6 to 7.
 *
 * Changes:
 * - Adds dismissed flag to signal_events for swipe-to-dismiss functionality.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE signal_events ADD COLUMN dismissed INTEGER NOT NULL DEFAULT 0"
        )
    }
}
