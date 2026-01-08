package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.MarketMoversCacheDao
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketMoversCacheRepository @Inject constructor(
    private val marketMoversCacheDao: MarketMoversCacheDao
) {

    suspend fun getCache(range: String, direction: String): MarketMoversCacheEntity? {
        return marketMoversCacheDao.getCache(range, direction)
    }

    suspend fun upsert(cache: MarketMoversCacheEntity) {
        marketMoversCacheDao.upsert(cache)
    }

    suspend fun delete(range: String, direction: String) {
        marketMoversCacheDao.delete(range, direction)
    }
}
