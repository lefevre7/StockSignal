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

    data class BatchSnapshots(
        val snapshots: Map<MarketMoverDirection, MarketMoversSnapshot>,
        val liveErrorMessage: String?,
        val liveException: Throwable?
    )

    suspend fun getMarketMovers(
        range: MarketMoverRange,
        direction: MarketMoverDirection,
        forceRefresh: Boolean = false
    ): Result<MarketMoversSnapshot> {
        return when (
            val batch = getMarketMoversBatch(
                range = range,
                directions = setOf(direction),
                forceRefresh = forceRefresh
            )
        ) {
            is Result.Error -> batch
            is Result.Success -> {
                val snapshot = batch.data.snapshots[direction]
                if (snapshot != null) {
                    Result.Success(snapshot)
                } else {
                    Result.Error(
                        Exception("No market movers snapshot for $direction"),
                        "No market movers snapshot for $direction"
                    )
                }
            }
        }
    }

    suspend fun getMarketMoversBatch(
        range: MarketMoverRange,
        directions: Set<MarketMoverDirection>,
        forceRefresh: Boolean = false
    ): Result<BatchSnapshots> {
        if (directions.isEmpty()) {
            return Result.Success(
                BatchSnapshots(
                    snapshots = emptyMap(),
                    liveErrorMessage = null,
                    liveException = null
                )
            )
        }

        val cachedByDirection = mutableMapOf<MarketMoverDirection, MarketMoversCacheEntity?>()
        directions.forEach { direction ->
            cachedByDirection[direction] = cacheRepository.getCache(range.label, direction.name)
        }
        if (!forceRefresh && cachedByDirection.values.all { it != null && !isStale(it) }) {
            return Result.Success(
                BatchSnapshots(
                    snapshots = cachedByDirection.mapNotNull { (direction, entity) ->
                        entity?.let { direction to snapshotFromCache(it, isFallback = false) }
                    }.toMap(),
                    liveErrorMessage = null,
                    liveException = null
                )
            )
        }

        return try {
            val html = api.getHomePage()
            logLarge("Raw homepage response (length=${html.length}):", html)
            val sections = MarketMoversHtmlParser.parse(html)
            storeSections(sections)
            val now = LocalDateTime.now()
            val snapshots = mutableMapOf<MarketMoverDirection, MarketMoversSnapshot>()
            directions.forEach { direction ->
                val match = findBestMatch(sections, range, direction)
                snapshots[direction] = if (match != null) {
                    val entity = MarketMoversCacheEntity(
                        range = range.label,
                        direction = direction.name,
                        fetchedAt = now,
                        items = match.items
                    )
                    cacheRepository.upsert(entity)
                    snapshotFromCache(entity, isFallback = false)
                } else {
                    cachedByDirection[direction]?.let { snapshotFromCache(it, isFallback = true) }
                        ?: emptyFallbackSnapshot(now)
                }
            }
            Result.Success(
                BatchSnapshots(
                    snapshots = snapshots,
                    liveErrorMessage = null,
                    liveException = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch market movers", e)
            val fallbackSnapshots = cachedByDirection.mapNotNull { (direction, entity) ->
                entity?.let { direction to snapshotFromCache(it, isFallback = true) }
            }.toMap()
            if (fallbackSnapshots.isNotEmpty()) {
                Result.Success(
                    BatchSnapshots(
                        snapshots = fallbackSnapshots,
                        liveErrorMessage = "Failed to fetch market movers: ${e.message}",
                        liveException = e
                    )
                )
            } else {
                Result.Error(e, "Failed to fetch market movers: ${e.message}")
            }
        }
    }

    suspend fun getFreshCachedMovers(
        range: MarketMoverRange,
        direction: MarketMoverDirection
    ): Result<MarketMoversSnapshot> {
        return try {
            val cached = cacheRepository.getCache(range.label, direction.name)
            if (cached == null) {
                Result.Error(Exception("No cached market movers"), "No cached market movers")
            } else if (isStale(cached)) {
                Result.Error(Exception("Cached market movers stale"), "Cached market movers stale")
            } else {
                Result.Success(snapshotFromCache(cached, isFallback = false))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read market movers cache", e)
            Result.Error(e, "Failed to read market movers cache: ${e.message}")
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

    private fun emptyFallbackSnapshot(now: LocalDateTime): MarketMoversSnapshot {
        return MarketMoversSnapshot(
            items = emptyList(),
            fetchedAt = now,
            isStale = true,
            isFallback = true
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
    }

    /**
     * Updates items in the cache while preserving the fetchedAt timestamp.
     * Used to persist enriched data (series, exchange) without triggering a full refresh.
     */
    suspend fun updateItemsInCache(
        range: MarketMoverRange,
        direction: MarketMoverDirection,
        items: List<com.example.stocksignal.data.local.model.MarketMoverItem>
    ) {
        val cached = cacheRepository.getCache(range.label, direction.name)
        if (cached != null) {
            val updated = cached.copy(items = items)
            cacheRepository.upsert(updated)
            Log.d(TAG, "Updated cache with ${items.size} enriched items for range=${range.label} direction=${direction.name}")
        }
    }

    companion object {
        private val CACHE_TTL = Duration.ofMinutes(10)
        private const val TAG = "MarketMoversRepository"
        private const val LOG_CHUNK_SIZE = 3500
    }

    private fun logLarge(label: String, message: String) {
        Log.d(TAG, label)
        Log.d(TAG, "--- START RAW HOMEPAGE RESPONSE ---")
        var start = 0
        val length = message.length
        while (start < length) {
            val end = (start + LOG_CHUNK_SIZE).coerceAtMost(length)
            Log.d(TAG, message.substring(start, end))
            start = end
        }
        Log.d(TAG, "--- END RAW HOMEPAGE RESPONSE ---")
    }
}
