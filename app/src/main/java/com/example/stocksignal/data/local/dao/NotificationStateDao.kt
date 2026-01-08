package com.example.stocksignal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stocksignal.data.local.entity.NotificationStateEntity

@Dao
interface NotificationStateDao {

    @Query("SELECT * FROM notification_state WHERE id = 1")
    suspend fun getState(): NotificationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: NotificationStateEntity)
}
