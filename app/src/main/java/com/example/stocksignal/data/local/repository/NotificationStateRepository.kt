package com.example.stocksignal.data.local.repository

import com.example.stocksignal.data.local.dao.NotificationStateDao
import com.example.stocksignal.data.local.entity.NotificationStateEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationStateRepository @Inject constructor(
    private val notificationStateDao: NotificationStateDao
) {

    suspend fun getState(): NotificationStateEntity? {
        return notificationStateDao.getState()
    }

    suspend fun upsert(state: NotificationStateEntity) {
        notificationStateDao.upsert(state)
    }
}
