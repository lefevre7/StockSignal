package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.StockDetailCacheDao
import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockDetailCacheRepository @Inject constructor(
    private val stockDetailCacheDao: StockDetailCacheDao
) {

    suspend fun getCache(symbol: String, range: String): StockDetailCacheEntity? {
        return try {
            stockDetailCacheDao.getCache(symbol, range)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache for $symbol/$range", e)
            null
        }
    }

    suspend fun upsert(cache: StockDetailCacheEntity) {
        try {
            stockDetailCacheDao.upsert(cache)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting cache for ${cache.symbol}/${cache.range}", e)
            throw e
        }
    }

    suspend fun deleteForSymbol(symbol: String) {
        try {
            stockDetailCacheDao.deleteForSymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cache for symbol: $symbol", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "StockDetailCacheRepo"
    }
}
