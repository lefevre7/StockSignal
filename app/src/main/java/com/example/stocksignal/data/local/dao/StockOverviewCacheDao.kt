package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.stocksignal.data.local.entity.StockOverviewCacheEntity

@Dao
interface StockOverviewCacheDao {

    @Query("SELECT * FROM stock_overview_cache WHERE symbol = :symbol")
    suspend fun getCache(symbol: String): StockOverviewCacheEntity?

    @Upsert
    suspend fun upsert(cache: StockOverviewCacheEntity)

    @Query("DELETE FROM stock_overview_cache WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("DELETE FROM stock_overview_cache")
    suspend fun clearAll()
}
