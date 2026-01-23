package com.example.stocksignal.domain.model

import java.time.LocalDateTime

data class SignalResult(
    val score: Int,
    val averageScore: Int,
    val modeScore: Int?,
    val confidence: Int,
    val aiScore: Int? = null,
    val aiConfidence: Int? = null,
    val aiSummary: String? = null,
    val aiReasons: List<AiScoreReason> = emptyList(),
    val reasons: List<SignalReason>,
    val modelScores: Map<String, Int>,
    val generatedAt: LocalDateTime
) {
    val displayScore: Int
        get() = aiScore ?: score

    val displayConfidence: Int?
        get() = aiConfidence ?: confidence

    val tier: SignalTier
        get() = SignalTier.fromScore(displayScore)
}
