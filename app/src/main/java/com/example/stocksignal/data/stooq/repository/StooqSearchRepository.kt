package com.example.stocksignal.data.stooq.repository

import android.util.Log
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.model.SearchResult
import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.data.stooq.network.StooqBlockedException
import com.example.stocksignal.data.stooq.parser.CmpCampaignParser
import com.example.stocksignal.data.stooq.parser.CmpParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StooqSearchRepository @Inject constructor(
    private val api: StooqApi
) {

    @Volatile private var cachedCampaignId: String? = null

    suspend fun search(query: String): Result<List<SearchResult>> {
        if (query.isBlank()) return Result.Success(emptyList())

        return try {
            val campaignId = cachedCampaignId ?: fetchCampaignId()
            if (campaignId == null) {
                Log.i(TAG, "Search failed: missing cmp campaign id for query=$query")
                Result.Error(Exception("Missing cmp campaign id"), "Unable to load search metadata")
            } else {
                Log.i(TAG, "Searching for query=$query, campaignId=$campaignId")
                val raw = api.getCmp(campaignId, query)
                Log.i(TAG, "Raw cmp response for query=$query (length=${raw.length}):")
                Log.i(TAG, "--- START RAW RESPONSE ---")
                Log.i(TAG, raw)
                Log.i(TAG, "--- END RAW RESPONSE ---")
                
                val results = CmpParser.parse(raw)
                Log.i(TAG, "Parsed ${results.size} search results for query=$query")
                Result.Success(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for query=$query", e)
            Result.Error(e, "Search failed: ${e.message}")
        }
    }

    private suspend fun fetchCampaignId(): String? {
        return try {
            val html = api.getHomePage()
            val campaignId = CmpCampaignParser.parseCampaignId(html)
            if (!campaignId.isNullOrBlank()) {
                cachedCampaignId = campaignId
            }
            campaignId
        } catch (e: Exception) {
            if (e is StooqBlockedException) throw e
            Log.e(TAG, "Failed to fetch cmp campaign id", e)
            null
        }
    }

    companion object {
        private const val TAG = "StooqSearchRepository"
    }
}
