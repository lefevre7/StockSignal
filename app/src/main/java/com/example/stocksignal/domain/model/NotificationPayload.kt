package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class NotificationPayload(
    val type: NotificationEventType,
    val ticker: String,
    val company: String?,
    val signal: String,
    val score: Int,
    val confidence: Int,
    val price: Double?,
    val percentChange: Double?,
    val time: LocalDateTime,
    val deepLink: String?,
    val source: String
)
