package com.example.stocksignal.data.repository

import com.example.stocksignal.data.local.entity.SearchHistoryEntity
import com.example.stocksignal.data.local.repository.SearchHistoryRepository
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.data.stooq.repository.StooqSearchRepository
import com.example.stocksignal.domain.model.RecentSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val stooqSearchRepository: StooqSearchRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) {

    val recentSearches: Flow<List<RecentSearch>> = searchHistoryRepository.historyFlow.map { entries ->
        entries.map { entry ->
            RecentSearch(
                query = entry.query,
                lastSearchedAt = entry.lastSearchedAt,
                count = entry.count
            )
        }
    }

    suspend fun search(query: String): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.Success(emptyList())
        val result = stooqSearchRepository.search(query)
        if (result is Result.Success) {
            val existing = searchHistoryRepository.getByQuery(query)
            val count = (existing?.count ?: 0) + 1
            searchHistoryRepository.upsert(
                SearchHistoryEntity(
                    query = query,
                    lastSearchedAt = LocalDateTime.now(),
                    count = count
                )
            )
        }
        return result
    }

    suspend fun clearHistory() {
        searchHistoryRepository.clear()
    }
}
