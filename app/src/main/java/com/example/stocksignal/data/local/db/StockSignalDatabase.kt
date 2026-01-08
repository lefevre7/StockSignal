package com.example.stocksignal.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.stocksignal.data.local.dao.MarketMoversCacheDao
import com.example.stocksignal.data.local.dao.NotesDao
import com.example.stocksignal.data.local.dao.NotificationStateDao
import com.example.stocksignal.data.local.dao.SearchHistoryDao
import com.example.stocksignal.data.local.dao.SignalEventDao
import com.example.stocksignal.data.local.dao.StockDetailCacheDao
import com.example.stocksignal.data.local.dao.WatchlistDao
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity
import com.example.stocksignal.data.local.entity.NoteEntity
import com.example.stocksignal.data.local.entity.NotificationStateEntity
import com.example.stocksignal.data.local.entity.SearchHistoryEntity
import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
import com.example.stocksignal.data.local.entity.WatchlistItemEntity

@Database(
    entities = [
        WatchlistItemEntity::class,
        GlobalSignalEventEntity::class,
        MarketMoversCacheEntity::class,
        StockDetailCacheEntity::class,
        NotificationStateEntity::class,
        NoteEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StockSignalDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun signalEventDao(): SignalEventDao
    abstract fun marketMoversCacheDao(): MarketMoversCacheDao
    abstract fun stockDetailCacheDao(): StockDetailCacheDao
    abstract fun notificationStateDao(): NotificationStateDao
    abstract fun notesDao(): NotesDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
