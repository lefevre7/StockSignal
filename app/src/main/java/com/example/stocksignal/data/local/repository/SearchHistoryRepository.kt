package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.SearchHistoryDao
import com.example.stocksignal.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) {

    val historyFlow: Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeHistory()
        .catch { e ->
            Log.e(TAG, "Error observing search history", e)
            emit(emptyList())
        }

    suspend fun getByQuery(query: String): SearchHistoryEntity? {
        return try {
            searchHistoryDao.getByQuery(query)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting search history for query: $query", e)
            null
        }
    }

    suspend fun upsert(entry: SearchHistoryEntity) {
        try {
            searchHistoryDao.upsert(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting search history: ${entry.query}", e)
            throw e
        }
    }

    suspend fun delete(query: String) {
        try {
            searchHistoryDao.delete(query)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting search history: $query", e)
            throw e
        }
    }

    suspend fun clear() {
        try {
            searchHistoryDao.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing search history", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "SearchHistoryRepository"
    }
}
