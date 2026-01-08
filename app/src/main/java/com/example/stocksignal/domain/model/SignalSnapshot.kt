package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class SignalSnapshot(
    val score: Int,
    val averageScore: Int?,
    val modeScore: Int?,
    val confidence: Int,
    val generatedAt: LocalDateTime
) {
    val tier: SignalTier
        get() = SignalTier.fromScore(score)
}
