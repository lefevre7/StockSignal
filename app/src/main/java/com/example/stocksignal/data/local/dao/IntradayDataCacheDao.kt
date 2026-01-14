package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
import java.time.LocalDate

@Dao
interface IntradayDataCacheDao {

    /**
     * Upsert (insert or replace) intraday data for a specific symbol and date.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: IntradayDataCacheEntity)

    /**
     * Get intraday data for a symbol within a date range (inclusive).
     * Returns data ordered by date ascending for chronological processing.
     */
    @Query("""
        SELECT * FROM intraday_data_cache 
        WHERE symbol = :symbol 
        AND date >= :startDate 
        AND date <= :endDate 
        ORDER BY date ASC
    """)
    suspend fun getCandlesByDateRange(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<IntradayDataCacheEntity>

    /**
     * Get all intraday data for a symbol (for full history retrieval).
     */
    @Query("SELECT * FROM intraday_data_cache WHERE symbol = :symbol ORDER BY date ASC")
    suspend fun getAllCandlesForSymbol(symbol: String): List<IntradayDataCacheEntity>

    /**
     * Delete intraday data older than a specified date (for cleanup).
     */
    @Query("DELETE FROM intraday_data_cache WHERE symbol = :symbol AND date < :beforeDate")
    suspend fun deleteOldData(symbol: String, beforeDate: LocalDate)

    /**
     * Delete all intraday data for a specific symbol.
     */
    @Query("DELETE FROM intraday_data_cache WHERE symbol = :symbol")
    suspend fun deleteForSymbol(symbol: String)

    /**
     * Count the number of date chunks for a symbol.
     */
    @Query("SELECT COUNT(*) FROM intraday_data_cache WHERE symbol = :symbol")
    suspend fun countDaysForSymbol(symbol: String): Int

    /**
     * Get the earliest date for a symbol's intraday data.
     */
    @Query("SELECT MIN(date) FROM intraday_data_cache WHERE symbol = :symbol")
    suspend fun getEarliestDate(symbol: String): LocalDate?

    /**
     * Get the latest date for a symbol's intraday data.
     */
    @Query("SELECT MAX(date) FROM intraday_data_cache WHERE symbol = :symbol")
    suspend fun getLatestDate(symbol: String): LocalDate?
}
