package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.WatchlistDao
import com.example.stocksignal.data.local.entity.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val watchlistDao: WatchlistDao
) {

    val watchlistFlow: Flow<List<WatchlistItemEntity>> = watchlistDao.observeWatchlist()
        .catch { e ->
            Log.e(TAG, "Error observing watchlist", e)
            emit(emptyList())
        }

    suspend fun getBySymbol(symbol: String): WatchlistItemEntity? {
        return try {
            watchlistDao.getBySymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting watchlist item for symbol: $symbol", e)
            null
        }
    }

    suspend fun getAll(): List<WatchlistItemEntity> {
        return try {
            watchlistDao.getAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all watchlist items", e)
            emptyList()
        }
    }

    suspend fun upsert(item: WatchlistItemEntity) {
        try {
            watchlistDao.upsert(item)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting watchlist item: ${item.symbol}", e)
            throw e
        }
    }

    suspend fun deleteBySymbol(symbol: String) {
        try {
            watchlistDao.deleteBySymbol(symbol)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting watchlist item: $symbol", e)
            throw e
        }
    }

    suspend fun updateSortOrder(symbols: List<String>) {
        try {
            symbols.forEachIndexed { index, symbol ->
                watchlistDao.updateSortOrder(symbol, index)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating sort order", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "WatchlistRepository"
    }
}
