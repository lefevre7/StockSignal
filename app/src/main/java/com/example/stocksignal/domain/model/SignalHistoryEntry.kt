package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class SignalHistoryEntry(
    val generatedAt: LocalDateTime,
    val score: Int,
    val averageScore: Int?,
    val modeScore: Int?,
    val confidence: Int,
    val reasons: List<SignalReason>
) {
    val tier: SignalTier
        get() = SignalTier.fromScore(score)
}
