package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.SignalEventDao
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalEventsRepository @Inject constructor(
    private val signalEventDao: SignalEventDao
) {

    val eventsFlow: Flow<List<GlobalSignalEventEntity>> = signalEventDao.observeEvents()
        .catch { e ->
            Log.e(TAG, "Error observing signal events", e)
            emit(emptyList())
        }

    fun eventsForTicker(ticker: String): Flow<List<GlobalSignalEventEntity>> {
        return signalEventDao.observeEventsForTicker(ticker)
            .catch { e ->
                Log.e(TAG, "Error observing events for ticker: $ticker", e)
                emit(emptyList())
            }
    }

    suspend fun getLatestForTickerAndLabel(ticker: String, label: String): GlobalSignalEventEntity? {
        return try {
            signalEventDao.getLatestForTickerAndLabel(ticker, label)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest event for $ticker/$label", e)
            null
        }
    }

    suspend fun getByIds(ids: List<String>): List<GlobalSignalEventEntity> {
        if (ids.isEmpty()) return emptyList()
        return try {
            signalEventDao.getByIds(ids)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting events by IDs", e)
            emptyList()
        }
    }

    suspend fun upsert(event: GlobalSignalEventEntity) {
        try {
            signalEventDao.upsert(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting signal event: ${event.id}", e)
            throw e
        }
    }

    suspend fun updateDelivery(ids: List<String>, notifiedAt: java.time.LocalDateTime, delivered: Boolean) {
        if (ids.isEmpty()) return
        try {
            signalEventDao.updateDelivery(ids, notifiedAt, delivered)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating delivery for ${ids.size} events", e)
            throw e
        }
    }

    suspend fun dismissSignal(id: String) {
        try {
            signalEventDao.dismissSignal(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing event: $id", e)
            throw e
        }
    }

    suspend fun undoDismissSignal(id: String) {
        try {
            signalEventDao.undoDismissSignal(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring dismissed event: $id", e)
            throw e
        }
    }

    suspend fun deleteById(id: String) {
        try {
            signalEventDao.deleteById(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event: $id", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "SignalEventsRepository"
    }
}
