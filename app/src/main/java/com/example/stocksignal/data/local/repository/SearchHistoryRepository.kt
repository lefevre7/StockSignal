package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.SearchHistoryDao
import com.example.stocksignal.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao
) {

    val historyFlow: Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeHistory()

    suspend fun getByQuery(query: String): SearchHistoryEntity? {
        return searchHistoryDao.getByQuery(query)
    }

    suspend fun upsert(entry: SearchHistoryEntity) {
        searchHistoryDao.upsert(entry)
    }

    suspend fun delete(query: String) {
        searchHistoryDao.delete(query)
    }

    suspend fun clear() {
        searchHistoryDao.clear()
    }
}
