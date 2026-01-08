package com.example.stocksignal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "notification_state")
data class NotificationStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastActiveNotificationId: Int?,
    val lastActiveAt: LocalDateTime?,
    val dismissed: Boolean,
    val queuedEventIds: List<String> = emptyList(),
    val notificationCounts: Map<String, Int> = emptyMap(),
    val lastResetAt: LocalDateTime?
)
