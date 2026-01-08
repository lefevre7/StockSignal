package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity

@Dao
interface MarketMoversCacheDao {

    @Query(
        "SELECT * FROM market_movers_cache WHERE range = :range AND direction = :direction LIMIT 1"
    )
    suspend fun getCache(range: String, direction: String): MarketMoversCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: MarketMoversCacheEntity)

    @Query("DELETE FROM market_movers_cache WHERE range = :range AND direction = :direction")
    suspend fun delete(range: String, direction: String)
}
