package com.example.stocksignal.domain.model

import java.time.LocalDateTime

enum class NotificationEventType {
    MARKET_MOVER,
    WATCHLIST_SIGNAL,
    DIGEST
}

data class NotificationEvent(
    val id: String,
    val type: NotificationEventType,
    val ticker: String,
    val companyName: String?,
    val score: Int,
    val averageScore: Int?,
    val modeScore: Int?,
    val confidence: Int,
    val price: Double?,
    val percentChange: Double?,
    val generatedAt: LocalDateTime,
    val notifiedAt: LocalDateTime?,
    val deepLink: String?,
    val source: String,
    val delivered: Boolean,
    val reasons: List<SignalReason>
) {
    val tier: SignalTier
        get() = SignalTier.fromScore(score)
}
