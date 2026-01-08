package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.WatchlistDao
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val watchlistDao: WatchlistDao
) {

    val watchlistFlow: Flow<List<WatchlistItemEntity>> = watchlistDao.observeWatchlist()

    suspend fun getBySymbol(symbol: String): WatchlistItemEntity? {
        return watchlistDao.getBySymbol(symbol)
    }

    suspend fun getAll(): List<WatchlistItemEntity> {
        return watchlistDao.getAll()
    }

    suspend fun upsert(item: WatchlistItemEntity) {
        watchlistDao.upsert(item)
    }

    suspend fun deleteBySymbol(symbol: String) {
        watchlistDao.deleteBySymbol(symbol)
    }

    suspend fun updateSortOrder(symbols: List<String>) {
        symbols.forEachIndexed { index, symbol ->
            watchlistDao.updateSortOrder(symbol, index)
        }
    }
}
