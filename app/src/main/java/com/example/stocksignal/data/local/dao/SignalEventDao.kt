package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalEventDao {

    @Query("SELECT * FROM signal_events ORDER BY generatedAt DESC")
    fun observeEvents(): Flow<List<GlobalSignalEventEntity>>

    @Query("SELECT * FROM signal_events WHERE ticker = :ticker ORDER BY generatedAt DESC")
    fun observeEventsForTicker(ticker: String): Flow<List<GlobalSignalEventEntity>>

    @Query("SELECT * FROM signal_events WHERE ticker = :ticker AND label = :label ORDER BY generatedAt DESC LIMIT 1")
    suspend fun getLatestForTickerAndLabel(ticker: String, label: String): GlobalSignalEventEntity?

    @Query("SELECT * FROM signal_events WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<GlobalSignalEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: GlobalSignalEventEntity)

    @Query("UPDATE signal_events SET notifiedAt = :notifiedAt, delivered = :delivered WHERE id IN (:ids)")
    suspend fun updateDelivery(ids: List<String>, notifiedAt: java.time.LocalDateTime, delivered: Boolean)

    @Query("DELETE FROM signal_events WHERE id = :id")
    suspend fun deleteById(id: String)
}
