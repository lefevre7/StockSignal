package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.StockOverviewCacheDao
import com.example.stocksignal.data.local.entity.StockOverviewCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockOverviewCacheRepository @Inject constructor(
    private val dao: StockOverviewCacheDao
) {
    private val TAG = "StockOverviewCacheRepo"

    suspend fun getCache(symbol: String): StockOverviewCacheEntity? {
        return try {
            dao.getCache(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching cache for $symbol", e)
            null
        }
    }

    suspend fun upsert(cache: StockOverviewCacheEntity) {
        try {
            dao.upsert(cache)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting cache for ${cache.symbol}", e)
        }
    }

    suspend fun delete(symbol: String) {
        try {
            dao.delete(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting cache for $symbol", e)
        }
    }

    suspend fun clearAll() {
        try {
            dao.clearAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all cache", e)
        }
    }
}
