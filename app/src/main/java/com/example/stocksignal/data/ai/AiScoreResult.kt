package com.example.stocksignal.data.ai

import com.example.stocksignal.domain.model.AiScoreReason

data class AiScoreResult(
    val score: Int,
    val confidence: Int,
    val summary: String,
    val reasons: List<AiScoreReason>
)
