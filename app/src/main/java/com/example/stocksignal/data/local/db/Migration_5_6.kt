package com.example.stocksignal.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 5 to 6.
 *
 * Changes:
 * - Adds AI score fields to signal_events for AI Score/Confidence and reasons.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE signal_events ADD COLUMN aiScore INTEGER")
        database.execSQL("ALTER TABLE signal_events ADD COLUMN aiConfidence INTEGER")
        database.execSQL("ALTER TABLE signal_events ADD COLUMN aiSummary TEXT")
        database.execSQL("ALTER TABLE signal_events ADD COLUMN aiReasonsJson TEXT")
    }
}
