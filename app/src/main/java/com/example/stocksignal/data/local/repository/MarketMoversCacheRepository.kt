package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.MarketMoversCacheDao
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketMoversCacheRepository @Inject constructor(
    private val marketMoversCacheDao: MarketMoversCacheDao
) {

    suspend fun getCache(range: String, direction: String): MarketMoversCacheEntity? {
        return try {
            marketMoversCacheDao.getCache(range, direction)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting market movers cache for $range/$direction", e)
            null
        }
    }

    suspend fun upsert(cache: MarketMoversCacheEntity) {
        try {
            marketMoversCacheDao.upsert(cache)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting market movers cache", e)
            throw e
        }
    }

    suspend fun delete(range: String, direction: String) {
        try {
            marketMoversCacheDao.delete(range, direction)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting market movers cache for $range/$direction", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "MarketMoversCacheRepo"
    }
}
