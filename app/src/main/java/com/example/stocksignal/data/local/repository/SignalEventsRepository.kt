package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.SignalEventDao
import com.example.stocksignal.data.local.entity.GlobalSignalEventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalEventsRepository @Inject constructor(
    private val signalEventDao: SignalEventDao
) {

    val eventsFlow: Flow<List<GlobalSignalEventEntity>> = signalEventDao.observeEvents()

    fun eventsForTicker(ticker: String): Flow<List<GlobalSignalEventEntity>> {
        return signalEventDao.observeEventsForTicker(ticker)
    }

    suspend fun getLatestForTickerAndLabel(ticker: String, label: String): GlobalSignalEventEntity? {
        return signalEventDao.getLatestForTickerAndLabel(ticker, label)
    }

    suspend fun getByIds(ids: List<String>): List<GlobalSignalEventEntity> {
        if (ids.isEmpty()) return emptyList()
        return signalEventDao.getByIds(ids)
    }

    suspend fun upsert(event: GlobalSignalEventEntity) {
        signalEventDao.upsert(event)
    }

    suspend fun updateDelivery(ids: List<String>, notifiedAt: java.time.LocalDateTime, delivered: Boolean) {
        if (ids.isEmpty()) return
        signalEventDao.updateDelivery(ids, notifiedAt, delivered)
    }

    suspend fun deleteById(id: String) {
        signalEventDao.deleteById(id)
    }
}
