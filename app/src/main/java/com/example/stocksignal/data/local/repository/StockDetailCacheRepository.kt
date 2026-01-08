package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.StockDetailCacheDao
import com.example.stocksignal.data.local.entity.StockDetailCacheEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockDetailCacheRepository @Inject constructor(
    private val stockDetailCacheDao: StockDetailCacheDao
) {

    suspend fun getCache(symbol: String, range: String): StockDetailCacheEntity? {
        return stockDetailCacheDao.getCache(symbol, range)
    }

    suspend fun upsert(cache: StockDetailCacheEntity) {
        stockDetailCacheDao.upsert(cache)
    }

    suspend fun deleteForSymbol(symbol: String) {
        stockDetailCacheDao.deleteForSymbol(symbol)
    }
}
