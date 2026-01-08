package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stocksignal.data.local.entity.StockDetailCacheEntity

@Dao
interface StockDetailCacheDao {

    @Query("SELECT * FROM stock_detail_cache WHERE symbol = :symbol AND range = :range LIMIT 1")
    suspend fun getCache(symbol: String, range: String): StockDetailCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: StockDetailCacheEntity)

    @Query("DELETE FROM stock_detail_cache WHERE symbol = :symbol")
    suspend fun deleteForSymbol(symbol: String)
}
