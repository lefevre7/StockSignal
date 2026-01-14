package com.example.stocksignal.data.local.di

import android.content.Context
import androidx.room.Room
import com.example.stocksignal.data.local.db.StockSignalDatabase
import com.example.stocksignal.data.local.db.MIGRATION_1_2
import com.example.stocksignal.data.local.db.MIGRATION_2_3
import com.example.stocksignal.data.local.db.MIGRATION_3_4
import com.example.stocksignal.data.local.dao.IntradayDataCacheDao
import com.example.stocksignal.data.local.dao.MarketMoversCacheDao
import com.example.stocksignal.data.local.dao.NotesDao
import com.example.stocksignal.data.local.dao.NotificationStateDao
import com.example.stocksignal.data.local.dao.SearchHistoryDao
import com.example.stocksignal.data.local.dao.SignalEventDao
import com.example.stocksignal.data.local.dao.StockDetailCacheDao
import com.example.stocksignal.data.local.dao.StockOverviewCacheDao
import com.example.stocksignal.data.local.dao.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalDataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StockSignalDatabase {
        return Room.databaseBuilder(
            context,
            StockSignalDatabase::class.java,
            "stocksignal.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideWatchlistDao(database: StockSignalDatabase): WatchlistDao {
        return database.watchlistDao()
    }

    @Provides
    fun provideSignalEventDao(database: StockSignalDatabase): SignalEventDao {
        return database.signalEventDao()
    }

    @Provides
    fun provideMarketMoversCacheDao(database: StockSignalDatabase): MarketMoversCacheDao {
        return database.marketMoversCacheDao()
    }

    @Provides
    fun provideStockDetailCacheDao(database: StockSignalDatabase): StockDetailCacheDao {
        return database.stockDetailCacheDao()
    }

    @Provides
    fun provideNotificationStateDao(database: StockSignalDatabase): NotificationStateDao {
        return database.notificationStateDao()
    }

    @Provides
    fun provideNotesDao(database: StockSignalDatabase): NotesDao {
        return database.notesDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: StockSignalDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideIntradayDataCacheDao(database: StockSignalDatabase): IntradayDataCacheDao {
        return database.intradayDataCacheDao()
    }

    @Provides
    fun provideStockOverviewCacheDao(database: StockSignalDatabase): StockOverviewCacheDao {
        return database.stockOverviewCacheDao()
    }
}
