package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.IntradayDataCacheDao
import com.example.stocksignal.data.local.entity.IntradayDataCacheEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntradayDataCacheRepository @Inject constructor(
    private val intradayDataCacheDao: IntradayDataCacheDao
) {

    suspend fun upsert(cache: IntradayDataCacheEntity) {
        try {
            intradayDataCacheDao.upsert(cache)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting intraday cache for ${cache.symbol}/${cache.date}", e)
            throw e
        }
    }

    suspend fun getCandlesByDateRange(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<IntradayDataCacheEntity> {
        return try {
            intradayDataCacheDao.getCandlesByDateRange(symbol, startDate, endDate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting intraday candles for $symbol from $startDate to $endDate", e)
            emptyList()
        }
    }

    suspend fun getAllCandlesForSymbol(symbol: String): List<IntradayDataCacheEntity> {
        return try {
            intradayDataCacheDao.getAllCandlesForSymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all candles for $symbol", e)
            emptyList()
        }
    }

    suspend fun deleteOldData(symbol: String, beforeDate: LocalDate) {
        try {
            intradayDataCacheDao.deleteOldData(symbol, beforeDate)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting old data for $symbol before $beforeDate", e)
            throw e
        }
    }

    suspend fun deleteForSymbol(symbol: String) {
        try {
            intradayDataCacheDao.deleteForSymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting intraday cache for $symbol", e)
            throw e
        }
    }

    suspend fun countDaysForSymbol(symbol: String): Int {
        return try {
            intradayDataCacheDao.countDaysForSymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error counting days for $symbol", e)
            0
        }
    }

    suspend fun getEarliestDate(symbol: String): LocalDate? {
        return try {
            intradayDataCacheDao.getEarliestDate(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting earliest date for $symbol", e)
            null
        }
    }

    suspend fun getLatestDate(symbol: String): LocalDate? {
        return try {
            intradayDataCacheDao.getLatestDate(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest date for $symbol", e)
            null
        }
    }

    companion object {
        private const val TAG = "IntradayDataCacheRepo"
    }
}
