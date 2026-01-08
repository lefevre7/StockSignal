package com.example.stocksignal.data.stooq.repository

import android.util.Log
import com.example.stocksignal.data.local.entity.MarketMoversCacheEntity
import com.example.stocksignal.data.local.model.MarketMoversSnapshot
import com.example.stocksignal.data.local.repository.MarketMoversCacheRepository
import com.example.stocksignal.data.stooq.model.MarketMoverDirection
import com.example.stocksignal.data.stooq.model.MarketMoverRange
import com.example.stocksignal.data.stooq.model.MarketMoversSection
import com.example.stocksignal.data.stooq.model.Result
import com.example.stocksignal.data.stooq.network.StooqApi
import com.example.stocksignal.data.stooq.parser.MarketMoversHtmlParser
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketMoversRepository @Inject constructor(
    private val api: StooqApi,
    private val cacheRepository: MarketMoversCacheRepository
) {

    suspend fun getMarketMovers(
        range: MarketMoverRange,
        direction: MarketMoverDirection,
        forceRefresh: Boolean = false
    ): Result<MarketMoversSnapshot> {
        val cached = cacheRepository.getCache(range.label, direction.name)
        if (!forceRefresh && cached != null && !isStale(cached)) {
            return Result.Success(snapshotFromCache(cached, isFallback = false))
        }

        return try {
            val html = api.getMarketMovers()
            val sections = MarketMoversHtmlParser.parse(html)
            storeSections(sections)

            val match = findBestMatch(sections, range, direction)
            if (match != null) {
                val entity = MarketMoversCacheEntity(
                    range = range.label,
                    direction = direction.name,
                    fetchedAt = LocalDateTime.now(),
                    items = match.items
                )
                cacheRepository.upsert(entity)
                Result.Success(snapshotFromCache(entity, isFallback = false))
            } else {
                val fallback = cached?.let { snapshotFromCache(it, isFallback = true) }
                if (fallback != null) {
                    Result.Success(fallback)
                } else {
                    Log.w(TAG, "No market movers parsed for range=$range direction=$direction")
                    Result.Success(
                        MarketMoversSnapshot(
                            items = emptyList(),
                            fetchedAt = LocalDateTime.now(),
                            isStale = true,
                            isFallback = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch market movers", e)
            val fallback = cached?.let { snapshotFromCache(it, isFallback = true) }
            fallback?.let { Result.Success(it) }
                ?: Result.Error(e, "Failed to fetch market movers: ${e.message}")
        }
    }

    private fun isStale(cache: MarketMoversCacheEntity): Boolean {
        val age = Duration.between(cache.fetchedAt, LocalDateTime.now())
        return age > CACHE_TTL
    }

    private fun snapshotFromCache(
        cache: MarketMoversCacheEntity,
        isFallback: Boolean
    ): MarketMoversSnapshot {
        return MarketMoversSnapshot(
            items = cache.items,
            fetchedAt = cache.fetchedAt,
            isStale = isStale(cache),
            isFallback = isFallback
        )
    }

    private suspend fun storeSections(sections: List<MarketMoversSection>) {
        val now = LocalDateTime.now()
        sections.forEach { section ->
            val range = section.range ?: return@forEach
            val direction = section.direction ?: return@forEach
            val entity = MarketMoversCacheEntity(
                range = range.label,
                direction = direction.name,
                fetchedAt = now,
                items = section.items
            )
            cacheRepository.upsert(entity)
        }
    }

    private fun findBestMatch(
        sections: List<MarketMoversSection>,
        range: MarketMoverRange,
        direction: MarketMoverDirection
    ): MarketMoversSection? {
        return sections.firstOrNull { it.range == range && it.direction == direction }
            ?: sections.firstOrNull { it.direction == direction }
            ?: sections.firstOrNull()
    }

    companion object {
        private val CACHE_TTL = Duration.ofMinutes(10)
        private const val TAG = "MarketMoversRepository"
    }
}
