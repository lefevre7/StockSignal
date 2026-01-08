package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist_items ORDER BY sortOrder IS NULL, sortOrder ASC, symbol ASC")
    fun observeWatchlist(): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist_items WHERE symbol = :symbol LIMIT 1")
    suspend fun getBySymbol(symbol: String): WatchlistItemEntity?

    @Query("SELECT * FROM watchlist_items ORDER BY sortOrder IS NULL, sortOrder ASC, symbol ASC")
    suspend fun getAll(): List<WatchlistItemEntity>

    @Query("UPDATE watchlist_items SET sortOrder = :sortOrder WHERE symbol = :symbol")
    suspend fun updateSortOrder(symbol: String, sortOrder: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItemEntity)

    @Update
    suspend fun update(item: WatchlistItemEntity)

    @Delete
    suspend fun delete(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist_items WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
