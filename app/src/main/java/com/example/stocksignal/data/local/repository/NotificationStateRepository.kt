package com.example.stocksignal.data.local.repository

import android.util.Log
import com.example.stocksignal.data.local.dao.NotificationStateDao
import com.example.stocksignal.data.local.entity.NotificationStateEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationStateRepository @Inject constructor(
    private val notificationStateDao: NotificationStateDao
) {

    suspend fun getState(): NotificationStateEntity? {
        return try {
            notificationStateDao.getState()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notification state", e)
            null
        }
    }

    suspend fun upsert(state: NotificationStateEntity) {
        try {
            notificationStateDao.upsert(state)
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting notification state", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "NotificationStateRepo"
    }
}
