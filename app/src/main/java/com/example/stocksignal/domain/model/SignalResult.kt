package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class SignalResult(
    val score: Int,
    val averageScore: Int,
    val modeScore: Int?,
    val confidence: Int,
    val reasons: List<SignalReason>,
    val modelScores: Map<String, Int>,
    val generatedAt: LocalDateTime
) {
    val tier: SignalTier
        get() = SignalTier.fromScore(score)
}
